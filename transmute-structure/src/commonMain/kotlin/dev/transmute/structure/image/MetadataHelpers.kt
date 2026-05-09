@file:Suppress("unused")

package dev.transmute.structure.image

import dev.transmute.model.identify.Endianness
import dev.transmute.model.metadata.exif.*
import dev.transmute.model.metadata.icc.IccHeader
import dev.transmute.model.metadata.icc.IccProfileMetadata
import dev.transmute.model.metadata.icc.IccTag
import dev.transmute.model.metadata.common.ByteSlice
import dev.transmute.model.metadata.common.PayloadRef
import dev.transmute.model.metadata.xmp.XmpAttribute
import dev.transmute.model.metadata.xmp.XmpElement
import dev.transmute.model.metadata.xmp.XmpDocument
import dev.transmute.model.metadata.xmp.XmpMetadata
import dev.transmute.model.metadata.xmp.XmpMiscNode
import dev.transmute.model.metadata.xmp.XmpNode
import dev.transmute.model.structure.image.types.TiffFieldType
import dev.transmute.model.structure.image.types.TiffIfd
import dev.transmute.model.structure.image.types.TiffIfdEntry
import dev.transmute.model.structure.image.types.TiffRaw

// -- TIFF -> EXIF mapping (shared across JPEG / TIFF / PNG / WebP) ---

/**
 * Convert a fully parsed [TiffRaw] structure to an [ExifMetadata] model.
 *
 * This is the shared core used by every extractor that encounters
 * TIFF-based EXIF: JPEG APP1, standalone TIFF files, PNG `eXIf` chunks,
 * and WebP `EXIF` RIFF chunks.
 */
internal fun tiffRawToExif(tiff: TiffRaw): ExifMetadata {
  return tiffRawToExif(tiff, originalSizeBytes = null)
}

internal fun tiffRawToExif(tiff: TiffRaw, originalSizeBytes: ULong?): ExifMetadata {
  val byteOrder = when (tiff.byteOrder) {
    Endianness.Little -> ExifByteOrder.LITTLE_ENDIAN
    Endianness.Big -> ExifByteOrder.BIG_ENDIAN
  }

  val ifdByOffset = tiff.ifds.associateBy { it.offset }
  val visited = mutableSetOf<UInt>()

  fun entryValueStorageRef(ifdOffset: UInt, entryIndex: Int, entry: TiffIfdEntry, order: Endianness): PayloadRef? {
    val type = TiffFieldType.fromCode(entry.fieldType)
    val bytesPerValue = type?.bytesPerValue?.toULong() ?: 1uL
    val valueBytes = entry.count.toULong() * bytesPerValue
    val entryBase = ifdOffset.toULong() + 2uL + (entryIndex.toULong() * 12uL)
    val valueFieldOffset = entryBase + 8uL
    return if (valueBytes <= 4uL) {
      PayloadRef(sizeBytes = 4uL, slice = ByteSlice(offset = valueFieldOffset, length = 4uL))
    } else {
      val valueOffset = readUInt(entry.valueOrOffset.data, 0, order) ?: return null
      PayloadRef(sizeBytes = valueBytes, slice = ByteSlice(offset = valueOffset.toULong(), length = valueBytes))
    }
  }

  fun TiffIfdEntry.toExifEntry(entryIndex: Int, ifdOffset: UInt, order: Endianness): ExifEntry {
    val tiffFieldType = TiffFieldType.fromCode(fieldType)
    val exifType = tiffFieldType?.let { ExifFieldType.fromCode(it.code) } ?: ExifFieldType.UNKNOWN
    val tagName = EXIF_TAG_NAMES[tag.toUInt()]
    val stored = entryValueStorageRef(ifdOffset, entryIndex, this, order)
    val value = decodeExifValue(
      data = data.data,
      type = exifType,
      rawFieldTypeCode = this.fieldType,
      count = count,
      order = order,
      stored = stored,
    )
    return ExifEntry(
      tag = tag,
      tagName = tagName,
      type = exifType,
      count = count,
      value = value,
      stored = stored,
    )
  }

  fun buildIfd(offset: UInt): ExifIfd? {
    val ifd = ifdByOffset[offset] ?: return null
    if (!visited.add(ifd.offset)) return null

    val converted = ifd.entries.mapIndexed { idx, e -> e.toExifEntry(idx, ifd.offset, tiff.byteOrder) }
    val byTag = converted.groupBy { it.tag }

    fun pointerLink(tag: UShort): ExifIfdLink? {
      val entry = byTag[tag]?.firstOrNull() ?: return null
      val targetOffset = (entry.value as? ExifValue.UInts)?.values?.firstOrNull() ?: return null
      return ExifIfdLink(
        pointerEntry = entry,
        targetOffset = targetOffset,
        target = buildIfd(targetOffset),
      )
    }

    val exifLink = pointerLink(0x8769u)
    val gpsLink = pointerLink(0x8825u)
    val interopLink = pointerLink(0xA005u)

    val subIfdLinks = buildList {
      val subEntry = byTag[0x014Au]?.firstOrNull() ?: return@buildList
      val offsets = (subEntry.value as? ExifValue.UInts)?.values ?: return@buildList
      for (off in offsets) {
        add(
          ExifIfdLink(
            pointerEntry = subEntry,
            targetOffset = off,
            target = buildIfd(off),
          ),
        )
      }
    }

    val thumbOffsetEntry = byTag[0x0201u]?.firstOrNull()
    val thumbLengthEntry = byTag[0x0202u]?.firstOrNull()
    val thumbPayload = run {
      val off = (thumbOffsetEntry?.value as? ExifValue.UInts)?.values?.firstOrNull()
      val len = (thumbLengthEntry?.value as? ExifValue.UInts)?.values?.firstOrNull()
      if (off != null && len != null && len > 0u) {
        PayloadRef(sizeBytes = len.toULong(), slice = ByteSlice(offset = off.toULong(), length = len.toULong()))
      } else {
        null
      }
    }
    val thumbnail = if (thumbOffsetEntry != null || thumbLengthEntry != null) {
      ExifThumbnailRef(
        jpegInterchangeFormat = thumbOffsetEntry,
        jpegInterchangeFormatLength = thumbLengthEntry,
        payload = thumbPayload,
      )
    } else {
      null
    }

    val consumed = buildSet {
      exifLink?.pointerEntry?.let { add(it) }
      gpsLink?.pointerEntry?.let { add(it) }
      interopLink?.pointerEntry?.let { add(it) }
      if (subIfdLinks.isNotEmpty()) add(subIfdLinks.first().pointerEntry)
      thumbOffsetEntry?.let { add(it) }
      thumbLengthEntry?.let { add(it) }
    }
    val extra = converted.filterNot(consumed::contains)

    val entries = ExifIfdEntries(
      exifIfd = exifLink,
      gpsIfd = gpsLink,
      interoperabilityIfd = interopLink,
      subIfds = subIfdLinks,
      thumbnail = thumbnail,
      extra = extra,
    )

    val nextOffset = ifd.nextIfdOffset
    val nextIfd = if (nextOffset != 0u) buildIfd(nextOffset) else null

    return ExifIfd(
      offset = ifd.offset,
      entries = entries,
      nextIfdOffset = nextOffset,
      nextIfd = nextIfd,
    )
  }

  val ifd0 = tiff.ifds.firstOrNull()?.offset?.let(::buildIfd)

  val orphans = tiff.ifds
    .filterNot { visited.contains(it.offset) }
    .mapNotNull { buildIfd(it.offset) }

  return ExifMetadata(
    byteOrder = byteOrder,
    magic = 42u,
    firstIfdOffset = tiff.firstIfdOffset,
    ifd0 = ifd0,
    orphanIfds = orphans,
    original = originalSizeBytes?.let { PayloadRef(sizeBytes = it) },
  )
}

// -- Value decoding ---

internal fun decodeExifValue(
  data: ByteArray,
  type: ExifFieldType,
  rawFieldTypeCode: UShort,
  count: UInt,
  order: Endianness,
  stored: PayloadRef?,
): ExifValue {
  val n = count.toInt()
  return when (type) {
    ExifFieldType.ASCII -> {
      val text = data.decodeToString().trimEnd('\u0000')
      ExifValue.Ascii(text)
    }
    ExifFieldType.BYTE -> ExifValue.UBytes(List(n) { i -> data.getOrElse(i) { 0 }.toUByte() })
    ExifFieldType.SBYTE -> ExifValue.SBytes(List(n) { i -> data.getOrElse(i) { 0 } })
    ExifFieldType.SHORT -> ExifValue.UShorts(List(n) { i -> readUShort(data, i * 2, order) ?: 0u })
    ExifFieldType.SSHORT -> ExifValue.Shorts(List(n) { i -> (readUShort(data, i * 2, order) ?: 0u).toShort() })
    ExifFieldType.LONG -> ExifValue.UInts(List(n) { i -> readUInt(data, i * 4, order) ?: 0u })
    ExifFieldType.SLONG -> ExifValue.Ints(List(n) { i -> (readUInt(data, i * 4, order) ?: 0u).toInt() })
    ExifFieldType.RATIONAL -> ExifValue.URationals(
      List(n) { i ->
        val num = readUInt(data, i * 8, order) ?: 0u
        val den = readUInt(data, i * 8 + 4, order) ?: 1u
        ExifURational(num, den)
      },
    )
    ExifFieldType.SRATIONAL -> ExifValue.SRationals(
      List(n) { i ->
        val num = (readUInt(data, i * 8, order) ?: 0u).toInt()
        val den = (readUInt(data, i * 8 + 4, order) ?: 1u).toInt()
        ExifSRational(num, den)
      },
    )
    ExifFieldType.FLOAT -> ExifValue.Floats(
      List(n) { i ->
        val bits = readUInt(data, i * 4, order)?.toInt() ?: 0
        Float.fromBits(bits)
      },
    )
    ExifFieldType.DOUBLE -> ExifValue.Doubles(
      List(n) { i ->
        val hi = (readUInt(data, i * 8, order) ?: 0u).toLong()
        val lo = (readUInt(data, i * 8 + 4, order) ?: 0u).toLong()
        val bits = if (order == Endianness.Big) (hi shl 32) or lo else (lo shl 32) or hi
        Double.fromBits(bits)
      },
    )
    ExifFieldType.UNDEFINED -> ExifValue.Undefined(stored ?: PayloadRef(sizeBytes = data.size.toULong()))
    ExifFieldType.UNKNOWN -> ExifValue.Unknown(rawFieldTypeCode, stored ?: PayloadRef(sizeBytes = data.size.toULong()))
  }
}

// -- Byte-order-aware readers ---

internal fun readUShort(data: ByteArray, offset: Int, order: Endianness): UShort? {
  if (offset + 2 > data.size) return null
  val b0 = data[offset].toInt() and 0xFF
  val b1 = data[offset + 1].toInt() and 0xFF
  return when (order) {
    Endianness.Little -> (b1 shl 8 or b0).toUShort()
    Endianness.Big -> (b0 shl 8 or b1).toUShort()
  }
}

internal fun readUInt(data: ByteArray, offset: Int, order: Endianness): UInt? {
  if (offset + 4 > data.size) return null
  val b = IntArray(4) { data[offset + it].toInt() and 0xFF }
  return when (order) {
    Endianness.Little -> (b[3] shl 24 or (b[2] shl 16) or (b[1] shl 8) or b[0]).toUInt()
    Endianness.Big -> (b[0] shl 24 or (b[1] shl 16) or (b[2] shl 8) or b[3]).toUInt()
  }
}

// -- XMP parsing (shared) ---

/**
 * Parse raw XMP XML text into an [XmpMetadata] model with a fully
 * resolved [XmpElement] tree.
 *
 * Uses a simple recursive-descent XML parser that handles elements,
 * attributes, namespace prefixes, text nodes, processing instructions
 * (`<?...?>`), and comments (`<!--...-->`).  CDATA sections are treated
 * as text.
 *
 * On any parse failure the function returns `null`.
 */
internal fun parseXmpText(xmlText: String): XmpMetadata? {
  val trimmed = xmlText.trim()
  if (trimmed.isEmpty()) return null
  return try {
    val parser = SimpleXmlParser(trimmed)
    val doc = parser.parseDocument() ?: return null
    XmpMetadata(document = doc)
  } catch (_: Exception) {
    null
  }
}

// ===
//  Minimal recursive-descent XML parser (KMP-safe, no platform deps)
// ===

/**
 * A tiny XML parser that builds an [XmpElement] tree.
 *
 * **Deliberately minimal** - supports the subset of XML that appears in
 * real-world XMP packets:
 * - Processing instructions (`<?...?>`) - skipped
 * - Comments (`<!-- ... -->`) - skipped
 * - CDATA sections (`<![CDATA[...]]>`) - treated as text
 * - Elements with attributes, namespace prefixes, and children
 * - Text content between tags
 * - Self-closing elements (`<foo/>`)
 *
 * Does **not** handle DTDs, entities beyond the five built-in ones
 * (`&amp;`, `&lt;`, `&gt;`, `&apos;`, `&quot;`), or XML namespaces
 * that rely on default namespace inheritance (prefixes are always
 * used explicitly in XMP).
 */
private class SimpleXmlParser(private val src: String) {
  private var pos = 0

  // -- Public entry point ---

  fun parseDocument(): XmpDocument? {
    val prolog = mutableListOf<XmpMiscNode>()
    skipWs()
    while (pos < src.length && src[pos] == '<') {
      val comment = tryReadComment()
      if (comment != null) {
        prolog.add(XmpMiscNode.Comment(comment))
        skipWs()
        continue
      }
      val pi = tryReadPI()
      if (pi != null) {
        prolog.add(XmpMiscNode.ProcessingInstruction(pi.first, pi.second))
        skipWs()
        continue
      }
      break
    }
    if (pos >= src.length || src[pos] != '<' || !peekNonSpecial()) return null
    val root = parseElement(inScopeNamespaces = emptyMap()) ?: return null
    return XmpDocument(prolog = prolog, root = root)
  }

  // -- Element parsing ---

  private fun parseElement(inScopeNamespaces: Map<String, String>): XmpElement? {
    if (pos >= src.length || src[pos] != '<') return null
    pos++ // skip '<'
    skipWs()
    val tagName = readName() ?: return null

    // Collect namespace declarations and attributes (preserving order)
    val localNsDecls = mutableListOf<dev.transmute.model.metadata.xmp.XmpNamespaceDecl>()
    val nsMap = inScopeNamespaces.toMutableMap()
    val attrs = mutableListOf<XmpAttribute>()
    while (pos < src.length) {
      skipWs()
      if (pos >= src.length) break
      if (src[pos] == '/' || src[pos] == '>') break
      val attrName = readName() ?: break
      skipWs()
      if (pos < src.length && src[pos] == '=') {
        pos++ // skip '='
        skipWs()
        val attrValue = readAttrValue() ?: ""
        if (attrName.startsWith("xmlns:")) {
          val prefix = attrName.removePrefix("xmlns:")
          nsMap[prefix] = attrValue
          localNsDecls.add(dev.transmute.model.metadata.xmp.XmpNamespaceDecl(prefix = prefix, namespaceUri = attrValue))
        } else if (attrName == "xmlns") {
          nsMap[""] = attrValue
          localNsDecls.add(dev.transmute.model.metadata.xmp.XmpNamespaceDecl(prefix = null, namespaceUri = attrValue))
        } else {
          attrs.add(
            XmpAttribute(
              name = resolveQName(attrName, nsMap, defaultApplies = false),
              value = attrValue,
            ),
          )
        }
      }
    }

    val elName = resolveQName(tagName, nsMap, defaultApplies = true)

    // Self-closing?
    if (pos < src.length && src[pos] == '/') {
      pos++ // skip '/'
      expect('>')
      return XmpElement(
        name = elName,
        namespaceDeclarations = localNsDecls,
        attributes = attrs,
      )
    }
    expect('>')

    // Children (elements + text)
    val children = mutableListOf<XmpNode>()
    while (pos < src.length) {
      // Closing tag?
      if (src[pos] == '<' && pos + 1 < src.length && src[pos + 1] == '/') {
        pos += 2 // skip '</'
        val closeName = readName()
        skipWs()
        expect('>')
        // Validate closing tag matches (ignore mismatches gracefully)
        break
      }
      // Comment / PI / CDATA / element
      if (src[pos] == '<') {
        val comment = tryReadComment()
        if (comment != null) {
          children.add(XmpNode.Comment(comment))
          continue
        }
        val pi = tryReadPI()
        if (pi != null) {
          children.add(XmpNode.ProcessingInstruction(pi.first, pi.second))
          continue
        }
        val cdata = tryReadCdata()
        if (cdata != null) {
          if (cdata.isNotBlank()) children.add(XmpNode.CData(cdata))
          continue
        }
        val child = parseElement(inScopeNamespaces = nsMap)
        if (child != null) children.add(XmpNode.Element(child)) else pos++
      } else {
        // Text node
        val text = readText()
        if (text.isNotBlank()) children.add(XmpNode.Text(unescapeXml(text)))
      }
    }

    return XmpElement(
      name = elName,
      namespaceDeclarations = localNsDecls,
      attributes = attrs,
      children = children,
    )
  }

  // -- Lexer helpers ---

  private fun skipWs() {
    while (pos < src.length && src[pos].isWhitespace()) pos++
  }

  private fun tryReadPI(): Pair<String, String>? {
    if (pos + 1 >= src.length || src[pos] != '<' || src[pos + 1] != '?') return null
    val end = src.indexOf("?>", pos + 2)
    val inner = if (end >= 0) src.substring(pos + 2, end) else src.substring(pos + 2)
    pos = if (end >= 0) end + 2 else src.length
    val trimmed = inner.trim()
    if (trimmed.isEmpty()) return "" to ""
    val sp = trimmed.indexOfFirst { it.isWhitespace() }
    return if (sp < 0) trimmed to "" else trimmed.substring(0, sp) to trimmed.substring(sp + 1).trim()
  }

  private fun tryReadComment(): String? {
    if (pos + 3 >= src.length || !src.startsWith("<!--", pos)) return null
    val end = src.indexOf("-->", pos + 4)
    val content = if (end >= 0) src.substring(pos + 4, end) else src.substring(pos + 4)
    pos = if (end >= 0) end + 3 else src.length
    return content
  }

  private fun tryReadCdata(): String? {
    if (pos + 8 >= src.length || !src.startsWith("<![CDATA[", pos)) return null
    val end = src.indexOf("]]>", pos + 9)
    val content = if (end >= 0) {
      val s = src.substring(pos + 9, end)
      pos = end + 3
      s
    } else {
      val s = src.substring(pos + 9)
      pos = src.length
      s
    }
    return content
  }

  /** Return true if the current '<' is the start of an element (not PI/comment/CDATA/close). */
  private fun peekNonSpecial(): Boolean {
    if (pos + 1 >= src.length) return false
    val c = src[pos + 1]
    return c != '?' && c != '!' && c != '/'
  }

  private fun readName(): String? {
    val start = pos
    while (pos < src.length) {
      val c = src[pos]
      if (c.isLetterOrDigit() || c == ':' || c == '_' || c == '-' || c == '.') {
        pos++
      } else {
        break
      }
    }
    return if (pos > start) src.substring(start, pos) else null
  }

  private fun readAttrValue(): String? {
    if (pos >= src.length) return null
    val q = src[pos]
    if (q != '"' && q != '\'') return null
    pos++ // skip opening quote
    val start = pos
    while (pos < src.length && src[pos] != q) pos++
    val value = src.substring(start, pos)
    if (pos < src.length) pos++ // skip closing quote
    return unescapeXml(value)
  }

  private fun readText(): String {
    val start = pos
    while (pos < src.length && src[pos] != '<') pos++
    return src.substring(start, pos)
  }

  private fun expect(ch: Char) {
    if (pos < src.length && src[pos] == ch) pos++
  }

  // -- Namespace helpers ---

  private fun resolveQName(raw: String, nsMap: Map<String, String>, defaultApplies: Boolean): dev.transmute.model.metadata.xmp.XmpQName {
    val colon = raw.indexOf(':')
    return if (colon > 0) {
      val prefix = raw.substring(0, colon)
      val local = raw.substring(colon + 1)
      dev.transmute.model.metadata.xmp.XmpQName(prefix = prefix, localName = local, namespaceUri = nsMap[prefix])
    } else {
      dev.transmute.model.metadata.xmp.XmpQName(prefix = null, localName = raw, namespaceUri = if (defaultApplies) nsMap[""] else null)
    }
  }

  private fun unescapeXml(s: String): String = s
    .replace("&amp;", "&")
    .replace("&lt;", "<")
    .replace("&gt;", ">")
    .replace("&apos;", "'")
    .replace("&quot;", "\"")
}

// -- ICC Profile parsing (shared) ---

/**
 * Parse an ICC profile from raw bytes.
 *
 * The profile header is always 128 bytes big-endian, followed by
 * a tag table.  Tag payloads are not decoded.
 */
internal fun parseIccProfile(data: ByteArray): IccProfileMetadata {
  fun u32be(offset: Int): UInt = ((data[offset].toUInt() and 0xFFu) shl 24) or
    ((data[offset + 1].toUInt() and 0xFFu) shl 16) or
    ((data[offset + 2].toUInt() and 0xFFu) shl 8) or
    (data[offset + 3].toUInt() and 0xFFu)

  fun u16be(offset: Int): Int = ((data[offset].toInt() and 0xFF) shl 8) or (data[offset + 1].toInt() and 0xFF)

  fun sig4(offset: Int): dev.transmute.model.identify.FourCC {
    val s = buildString(4) {
      for (i in 0 until 4) append(data.getOrElse(offset + i) { 0 }.toInt().toChar())
    }
    return dev.transmute.model.identify.FourCC(s)
  }

  fun sig4OrNull(offset: Int): dev.transmute.model.identify.FourCC? {
    val b0 = data.getOrElse(offset) { 0 }
    val b1 = data.getOrElse(offset + 1) { 0 }
    val b2 = data.getOrElse(offset + 2) { 0 }
    val b3 = data.getOrElse(offset + 3) { 0 }
    if (b0 == 0.toByte() && b1 == 0.toByte() && b2 == 0.toByte() && b3 == 0.toByte()) return null
    return sig4(offset)
  }

  val profileSize = u32be(0)
  val preferredCmm = sig4OrNull(4)

  val major = data.getOrElse(8) { 0 }.toUByte()
  val minor = ((data.getOrElse(9) { 0 }.toInt() and 0xF0) shr 4).toUByte()
  val bugfix = (data.getOrElse(9) { 0 }.toInt() and 0x0F).toUByte()

  val profileClass = sig4(12)
  val colorSpace = sig4(16)
  val pcs = sig4(20)

  val year = u16be(24)
  val month = u16be(26)
  val day = u16be(28)
  val hour = u16be(30)
  val minute = u16be(32)
  val second = u16be(34)
  val creationDate = if (year > 0) {
    dev.transmute.model.core.Iso8601String(
      buildString(19) {
        append(year.toString().padStart(4, '0'))
        append('-')
        append(month.toString().padStart(2, '0'))
        append('-')
        append(day.toString().padStart(2, '0'))
        append('T')
        append(hour.toString().padStart(2, '0'))
        append(':')
        append(minute.toString().padStart(2, '0'))
        append(':')
        append(second.toString().padStart(2, '0'))
      },
    )
  } else {
    null
  }

  val primaryPlatform = sig4OrNull(40)
  val renderingIntentCode = u32be(64).toInt()
  val renderingIntent = when (renderingIntentCode) {
    0 -> dev.transmute.model.metadata.icc.IccRenderingIntent.Perceptual
    1 -> dev.transmute.model.metadata.icc.IccRenderingIntent.MediaRelativeColorimetric
    2 -> dev.transmute.model.metadata.icc.IccRenderingIntent.Saturation
    3 -> dev.transmute.model.metadata.icc.IccRenderingIntent.ICCAbsoluteColorimetric
    else -> dev.transmute.model.metadata.icc.IccRenderingIntent.Unknown
  }

  val tagCount = if (data.size >= 132) u32be(128).toInt() else 0
  val tags = (0 until tagCount).mapNotNull { i ->
    val entryOffset = 132 + i * 12
    if (entryOffset + 12 > data.size) return@mapNotNull null
    val sig = sig4(entryOffset)
    val off = u32be(entryOffset + 4)
    val size = u32be(entryOffset + 8)
    IccTag(
      signature = sig,
      offset = off,
      size = size,
      payload = PayloadRef(
        sizeBytes = size.toULong(),
        slice = ByteSlice(offset = off.toULong(), length = size.toULong()),
      ),
    )
  }

  return IccProfileMetadata(
    header = IccHeader(
      profileSize = profileSize,
      preferredCmm = preferredCmm,
      profileVersion = dev.transmute.model.metadata.icc.IccVersion(major, minor, bugfix),
      profileClass = profileClass,
      colorSpace = colorSpace,
      pcs = pcs,
      creationDate = creationDate,
      primaryPlatform = primaryPlatform,
      renderingIntent = renderingIntent,
    ),
    tags = tags,
    original = PayloadRef(sizeBytes = data.size.toULong()),
  )
}

// -- Helpers ---

internal fun ByteArray.startsWith(prefix: ByteArray): Boolean {
  if (size < prefix.size) return false
  for (i in prefix.indices) {
    if (this[i] != prefix[i]) return false
  }
  return true
}

// -- Well-known EXIF tag names ---

internal val EXIF_TAG_NAMES: Map<UInt, String> = mapOf(
  // IFD0 / IFD1 (TIFF baseline)
  0x0100u to "ImageWidth",
  0x0101u to "ImageLength",
  0x0102u to "BitsPerSample",
  0x0103u to "Compression",
  0x0106u to "PhotometricInterpretation",
  0x010Eu to "ImageDescription",
  0x010Fu to "Make",
  0x0110u to "Model",
  0x0111u to "StripOffsets",
  0x0112u to "Orientation",
  0x0115u to "SamplesPerPixel",
  0x0116u to "RowsPerStrip",
  0x0117u to "StripByteCounts",
  0x011Au to "XResolution",
  0x011Bu to "YResolution",
  0x0128u to "ResolutionUnit",
  0x0131u to "Software",
  0x0132u to "DateTime",
  0x013Bu to "Artist",
  0x0201u to "JPEGInterchangeFormat",
  0x0202u to "JPEGInterchangeFormatLength",
  0x8298u to "Copyright",
  // Pointer tags
  0x8769u to "ExifIFDPointer",
  0x8825u to "GPSInfoIFDPointer",
  // Exif IFD
  0x829Au to "ExposureTime",
  0x829Du to "FNumber",
  0x8822u to "ExposureProgram",
  0x8827u to "ISOSpeedRatings",
  0x9000u to "ExifVersion",
  0x9003u to "DateTimeOriginal",
  0x9004u to "DateTimeDigitized",
  0x9101u to "ComponentsConfiguration",
  0x9102u to "CompressedBitsPerPixel",
  0x9201u to "ShutterSpeedValue",
  0x9202u to "ApertureValue",
  0x9203u to "BrightnessValue",
  0x9204u to "ExposureBiasValue",
  0x9205u to "MaxApertureValue",
  0x9206u to "SubjectDistance",
  0x9207u to "MeteringMode",
  0x9208u to "LightSource",
  0x9209u to "Flash",
  0x920Au to "FocalLength",
  0x927Cu to "MakerNote",
  0x9286u to "UserComment",
  0x9290u to "SubSecTime",
  0x9291u to "SubSecTimeOriginal",
  0x9292u to "SubSecTimeDigitized",
  0xA000u to "FlashpixVersion",
  0xA001u to "ColorSpace",
  0xA002u to "PixelXDimension",
  0xA003u to "PixelYDimension",
  0xA005u to "InteroperabilityIFDPointer",
  0xA20Eu to "FocalPlaneXResolution",
  0xA20Fu to "FocalPlaneYResolution",
  0xA210u to "FocalPlaneResolutionUnit",
  0xA217u to "SensingMethod",
  0xA300u to "FileSource",
  0xA301u to "SceneType",
  0xA401u to "CustomRendered",
  0xA402u to "ExposureMode",
  0xA403u to "WhiteBalance",
  0xA404u to "DigitalZoomRatio",
  0xA405u to "FocalLengthIn35mmFilm",
  0xA406u to "SceneCaptureType",
  0xA408u to "Contrast",
  0xA409u to "Saturation",
  0xA40Au to "Sharpness",
  0xA420u to "ImageUniqueID",
  0xA430u to "CameraOwnerName",
  0xA431u to "BodySerialNumber",
  0xA432u to "LensInfo",
  0xA433u to "LensMake",
  0xA434u to "LensModel",
  0xA435u to "LensSerialNumber",
  // GPS IFD
  0x0000u to "GPSVersionID",
  0x0001u to "GPSLatitudeRef",
  0x0002u to "GPSLatitude",
  0x0003u to "GPSLongitudeRef",
  0x0004u to "GPSLongitude",
  0x0005u to "GPSAltitudeRef",
  0x0006u to "GPSAltitude",
  0x0007u to "GPSTimeStamp",
  0x0008u to "GPSSatellites",
  0x0009u to "GPSStatus",
  0x000Au to "GPSMeasureMode",
  0x000Bu to "GPSDOP",
  0x000Cu to "GPSSpeedRef",
  0x000Du to "GPSSpeed",
  0x000Eu to "GPSTrackRef",
  0x000Fu to "GPSTrack",
  0x0010u to "GPSImgDirectionRef",
  0x0011u to "GPSImgDirection",
  0x0012u to "GPSMapDatum",
  0x0013u to "GPSDestLatitudeRef",
  0x0014u to "GPSDestLatitude",
  0x0015u to "GPSDestLongitudeRef",
  0x0016u to "GPSDestLongitude",
  0x0017u to "GPSDestBearingRef",
  0x0018u to "GPSDestBearing",
  0x0019u to "GPSDestDistanceRef",
  0x001Au to "GPSDestDistance",
  0x001Bu to "GPSProcessingMethod",
  0x001Cu to "GPSAreaInformation",
  0x001Du to "GPSDateStamp",
  0x001Eu to "GPSDifferential",
)
