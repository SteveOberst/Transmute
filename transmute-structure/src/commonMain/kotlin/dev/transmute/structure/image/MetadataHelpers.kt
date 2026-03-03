@file:Suppress("unused")

package dev.transmute.structure.image

import dev.transmute.model.identify.Endianness
import dev.transmute.model.metadata.exif.*
import dev.transmute.model.metadata.icc.IccHeader
import dev.transmute.model.metadata.icc.IccProfileMetadata
import dev.transmute.model.metadata.icc.IccTag
import dev.transmute.model.metadata.xmp.XmpAttribute
import dev.transmute.model.metadata.xmp.XmpElement
import dev.transmute.model.metadata.xmp.XmpMetadata
import dev.transmute.model.metadata.xmp.XmpNode
import dev.transmute.model.structure.image.types.TiffFieldType
import dev.transmute.model.structure.image.types.TiffIfd
import dev.transmute.model.structure.image.types.TiffIfdEntry
import dev.transmute.model.structure.image.types.TiffRaw

// -- TIFF -> EXIF mapping (shared across JPEG / TIFF / PNG / WebP) ------------

/**
 * Convert a fully parsed [TiffRaw] structure to an [ExifMetadata] model.
 *
 * This is the shared core used by every extractor that encounters
 * TIFF-based EXIF: JPEG APP1, standalone TIFF files, PNG `eXIf` chunks,
 * and WebP `EXIF` RIFF chunks.
 */
internal fun tiffRawToExif(tiff: TiffRaw): ExifMetadata {
    val byteOrder = when (tiff.byteOrder) {
        Endianness.Little -> ExifByteOrder.LITTLE_ENDIAN
        Endianness.Big -> ExifByteOrder.BIG_ENDIAN
    }

    val ifdByOffset = tiff.ifds.associateBy { it.offset }

    val ifd0 = tiff.ifds.firstOrNull()
    val ifd1 = if (ifd0 != null && ifd0.nextIfdOffset != 0u)
        ifdByOffset[ifd0.nextIfdOffset] else null

    val exifIfdOffset = ifd0?.findPointerTag(34665u, tiff.byteOrder)
    val gpsIfdOffset = ifd0?.findPointerTag(34853u, tiff.byteOrder)

    val exifIfd = exifIfdOffset?.let { ifdByOffset[it] }
    val interopIfdOffset = exifIfd?.findPointerTag(40965u, tiff.byteOrder)
    val interopIfd = interopIfdOffset?.let { ifdByOffset[it] }

    return ExifMetadata(
        byteOrder = byteOrder,
        ifd0 = ifd0?.toExifIfd(tiff.byteOrder),
        exifIfd = exifIfd?.toExifIfd(tiff.byteOrder),
        gpsIfd = gpsIfdOffset?.let { ifdByOffset[it] }?.toExifIfd(tiff.byteOrder),
        interopIfd = interopIfd?.toExifIfd(tiff.byteOrder),
        ifd1 = ifd1?.toExifIfd(tiff.byteOrder),
        thumbnailOffset = ifd1?.findLongTag(513u, tiff.byteOrder),
        thumbnailLength = ifd1?.findLongTag(514u, tiff.byteOrder),
    )
}

internal fun TiffIfd.findPointerTag(tagCode: UShort, order: Endianness): UInt? {
    val entry = entries.firstOrNull { it.tag == tagCode } ?: return null
    return readUInt(entry.data.data, 0, order)
}

internal fun TiffIfd.findLongTag(tagCode: UShort, order: Endianness): Long? {
    val entry = entries.firstOrNull { it.tag == tagCode } ?: return null
    return readUInt(entry.data.data, 0, order)?.toLong()
}

internal fun TiffIfd.toExifIfd(order: Endianness): ExifIfd = ExifIfd(
    entries = entries.map { it.toExifEntry(order) }
)

internal fun TiffIfdEntry.toExifEntry(order: Endianness): ExifEntry {
    val fieldType = TiffFieldType.fromCode(fieldType)
    val exifType = fieldType?.let { ExifFieldType.fromCode(it.code.toInt()) } ?: ExifFieldType.UNKNOWN
    val tagInt = tag.toInt()
    return ExifEntry(
        tag = tagInt,
        tagName = EXIF_TAG_NAMES[tagInt],
        type = exifType,
        count = count.toLong(),
        value = decodeExifValue(data.data, exifType, count.toInt(), order),
    )
}

// -- Value decoding -----------------------------------------------------------

internal fun decodeExifValue(
    data: ByteArray,
    type: ExifFieldType,
    count: Int,
    order: Endianness,
): ExifValue = when (type) {
    ExifFieldType.ASCII -> {
        val text = data.decodeToString().trimEnd('\u0000')
        ExifValue.Text(text)
    }
    ExifFieldType.BYTE, ExifFieldType.SBYTE -> {
        if (count > 32) ExifValue.Blob(data.size.toLong())
        else ExifValue.Integers(List(count) { i ->
            if (type == ExifFieldType.SBYTE) data.getOrElse(i) { 0 }.toLong()
            else (data.getOrElse(i) { 0 }.toInt() and 0xFF).toLong()
        })
    }
    ExifFieldType.SHORT, ExifFieldType.SSHORT -> {
        if (count > 32) ExifValue.Blob(data.size.toLong())
        else ExifValue.Integers(List(count) { i ->
            val v = readUShort(data, i * 2, order) ?: 0u
            if (type == ExifFieldType.SSHORT) v.toShort().toLong() else v.toLong()
        })
    }
    ExifFieldType.LONG, ExifFieldType.SLONG -> {
        if (count > 32) ExifValue.Blob(data.size.toLong())
        else ExifValue.Integers(List(count) { i ->
            val v = readUInt(data, i * 4, order) ?: 0u
            if (type == ExifFieldType.SLONG) v.toInt().toLong() else v.toLong()
        })
    }
    ExifFieldType.RATIONAL, ExifFieldType.SRATIONAL -> {
        if (count > 16) ExifValue.Blob(data.size.toLong())
        else ExifValue.Rationals(List(count) { i ->
            val num = readUInt(data, i * 8, order) ?: 0u
            val den = readUInt(data, i * 8 + 4, order) ?: 1u
            if (type == ExifFieldType.SRATIONAL)
                ExifRational(num.toInt().toLong(), den.toInt().toLong())
            else
                ExifRational(num.toLong(), den.toLong())
        })
    }
    ExifFieldType.FLOAT -> {
        if (count > 16) ExifValue.Blob(data.size.toLong())
        else ExifValue.Floats(List(count) { i ->
            val bits = readUInt(data, i * 4, order)?.toInt() ?: 0
            Float.fromBits(bits).toDouble()
        })
    }
    ExifFieldType.DOUBLE -> {
        if (count > 16) ExifValue.Blob(data.size.toLong())
        else ExifValue.Floats(List(count) { i ->
            val hi = (readUInt(data, i * 8, order) ?: 0u).toLong()
            val lo = (readUInt(data, i * 8 + 4, order) ?: 0u).toLong()
            val bits = if (order == Endianness.Big) (hi shl 32) or lo
            else (lo shl 32) or hi
            Double.fromBits(bits)
        })
    }
    ExifFieldType.UNDEFINED, ExifFieldType.UNKNOWN -> ExifValue.Blob(data.size.toLong())
}

// -- Byte-order-aware readers -------------------------------------------------

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

// -- XMP parsing (shared) ----------------------------------------------------

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
        val root = parser.parseDocument() ?: return null
        XmpMetadata(root = root)
    } catch (_: Exception) {
        null
    }
}

// ===============================================================================
//  Minimal recursive-descent XML parser (KMP-safe, no platform deps)
// ===============================================================================

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

    // -- Public entry point ---------------------------------------

    fun parseDocument(): XmpElement? {
        skipMisc()
        return if (pos < src.length && src[pos] == '<' && peekNonSpecial()) {
            parseElement()
        } else null
    }

    // -- Element parsing ------------------------------------------

    private fun parseElement(): XmpElement? {
        if (pos >= src.length || src[pos] != '<') return null
        pos++ // skip '<'
        skipWs()
        val tagName = readName() ?: return null

        // Collect namespace declarations and attributes
        val nsMap = mutableMapOf<String, String>()
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
                    nsMap[attrName.removePrefix("xmlns:")] = attrValue
                } else if (attrName == "xmlns") {
                    nsMap[""] = attrValue
                } else {
                    val (aNs, aLocal) = splitPrefixed(attrName, nsMap)
                    attrs.add(XmpAttribute(namespace = aNs, name = aLocal, value = attrValue))
                }
            }
        }

        val (elNs, elLocal) = splitPrefixed(tagName, nsMap)

        // Self-closing?
        if (pos < src.length && src[pos] == '/') {
            pos++ // skip '/'
            expect('>')
            return XmpElement(namespace = elNs, name = elLocal, attributes = attrs)
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
            // Comment or PI?
            if (src[pos] == '<') {
                if (trySkipComment() || trySkipPI()) continue
                // CDATA?
                val cdata = tryReadCdata()
                if (cdata != null) {
                    if (cdata.isNotBlank()) children.add(XmpNode.Text(cdata))
                    continue
                }
                // Child element
                val child = parseElement()
                if (child != null) {
                    children.add(XmpNode.Element(child))
                } else {
                    pos++ // skip unrecognised '<' to avoid infinite loop
                }
            } else {
                // Text node
                val text = readText()
                if (text.isNotBlank()) children.add(XmpNode.Text(unescapeXml(text)))
            }
        }

        return XmpElement(
            namespace = elNs,
            name = elLocal,
            attributes = attrs,
            children = children,
        )
    }

    // -- Lexer helpers --------------------------------------------

    private fun skipWs() {
        while (pos < src.length && src[pos].isWhitespace()) pos++
    }

    private fun skipMisc() {
        while (pos < src.length) {
            skipWs()
            if (pos >= src.length) return
            if (src[pos] != '<') return
            if (trySkipComment() || trySkipPI()) continue
            return // it's a real element
        }
    }

    private fun trySkipPI(): Boolean {
        if (pos + 1 >= src.length || src[pos] != '<' || src[pos + 1] != '?') return false
        val end = src.indexOf("?>", pos + 2)
        pos = if (end >= 0) end + 2 else src.length
        return true
    }

    private fun trySkipComment(): Boolean {
        if (pos + 3 >= src.length || !src.startsWith("<!--", pos)) return false
        val end = src.indexOf("-->", pos + 4)
        pos = if (end >= 0) end + 3 else src.length
        return true
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
            if (c.isLetterOrDigit() || c == ':' || c == '_' || c == '-' || c == '.') pos++
            else break
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

    // -- Namespace helpers ----------------------------------------

    private fun splitPrefixed(name: String, nsMap: Map<String, String>): Pair<String?, String> {
        val colon = name.indexOf(':')
        return if (colon > 0) {
            val prefix = name.substring(0, colon)
            val local = name.substring(colon + 1)
            nsMap[prefix] to local
        } else {
            nsMap[""] to name
        }
    }

    private fun unescapeXml(s: String): String = s
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&apos;", "'")
        .replace("&quot;", "\"")
}

// -- ICC Profile parsing (shared) ---------------------------------------------

/**
 * Parse an ICC profile from raw bytes.
 *
 * The profile header is always 128 bytes big-endian, followed by
 * a tag table.  Tag payloads are not decoded.
 */
internal fun parseIccProfile(data: ByteArray): IccProfileMetadata {
    fun u32(offset: Int): Long =
        ((data[offset].toInt() and 0xFF).toLong() shl 24) or
        ((data[offset + 1].toInt() and 0xFF).toLong() shl 16) or
        ((data[offset + 2].toInt() and 0xFF).toLong() shl 8) or
        (data[offset + 3].toInt() and 0xFF).toLong()

    fun sig(offset: Int): String =
        data.decodeToString(offset, (offset + 4).coerceAtMost(data.size))
            .trimEnd('\u0000', ' ')

    fun u16(offset: Int): Int =
        ((data[offset].toInt() and 0xFF) shl 8) or (data[offset + 1].toInt() and 0xFF)

    val profileSize = u32(0)
    val preferredCmm = sig(4).ifEmpty { null }
    val majorVer = (data[8].toInt() and 0xFF)
    val minorVer = (data[9].toInt() and 0xF0) shr 4
    val bugfixVer = data[9].toInt() and 0x0F
    val profileVersion = "$majorVer.$minorVer.$bugfixVer"
    val profileClass = sig(12)
    val colorSpace = sig(16)
    val pcs = sig(20)

    val year = u16(24)
    val month = u16(26)
    val day = u16(28)
    val hour = u16(30)
    val minute = u16(32)
    val second = u16(34)
    val creationDate = if (year > 0)
        "%04d-%02d-%02dT%02d:%02d:%02d".format(year, month, day, hour, minute, second)
    else null

    val primaryPlatform = sig(40).ifEmpty { null }
    val renderingIntentCode = u32(64).toInt()
    val renderingIntent = when (renderingIntentCode) {
        0 -> "Perceptual"
        1 -> "MediaRelativeColorimetric"
        2 -> "Saturation"
        3 -> "ICCAbsoluteColorimetric"
        else -> "Unknown($renderingIntentCode)"
    }

    val tagCount = if (data.size >= 132) u32(128).toInt() else 0
    val tags = (0 until tagCount).mapNotNull { i ->
        val tagOffset = 132 + i * 12
        if (tagOffset + 12 > data.size) return@mapNotNull null
        IccTag(
            signature = sig(tagOffset),
            offset = u32(tagOffset + 4),
            size = u32(tagOffset + 8),
        )
    }

    return IccProfileMetadata(
        header = IccHeader(
            profileSize = profileSize,
            preferredCmm = preferredCmm,
            profileVersion = profileVersion,
            profileClass = profileClass,
            colorSpace = colorSpace,
            pcs = pcs,
            creationDate = creationDate,
            primaryPlatform = primaryPlatform,
            renderingIntent = renderingIntent,
        ),
        tags = tags,
    )
}

// -- Helpers ------------------------------------------------------------------

internal fun ByteArray.startsWith(prefix: ByteArray): Boolean {
    if (size < prefix.size) return false
    for (i in prefix.indices) {
        if (this[i] != prefix[i]) return false
    }
    return true
}

// -- Well-known EXIF tag names ------------------------------------------------

internal val EXIF_TAG_NAMES: Map<Int, String> = mapOf(
    // IFD0 / IFD1 (TIFF baseline)
    0x0100 to "ImageWidth",
    0x0101 to "ImageLength",
    0x0102 to "BitsPerSample",
    0x0103 to "Compression",
    0x0106 to "PhotometricInterpretation",
    0x010E to "ImageDescription",
    0x010F to "Make",
    0x0110 to "Model",
    0x0111 to "StripOffsets",
    0x0112 to "Orientation",
    0x0115 to "SamplesPerPixel",
    0x0116 to "RowsPerStrip",
    0x0117 to "StripByteCounts",
    0x011A to "XResolution",
    0x011B to "YResolution",
    0x0128 to "ResolutionUnit",
    0x0131 to "Software",
    0x0132 to "DateTime",
    0x013B to "Artist",
    0x0201 to "JPEGInterchangeFormat",
    0x0202 to "JPEGInterchangeFormatLength",
    0x8298 to "Copyright",
    // Pointer tags
    0x8769 to "ExifIFDPointer",
    0x8825 to "GPSInfoIFDPointer",
    // Exif IFD
    0x829A to "ExposureTime",
    0x829D to "FNumber",
    0x8822 to "ExposureProgram",
    0x8827 to "ISOSpeedRatings",
    0x9000 to "ExifVersion",
    0x9003 to "DateTimeOriginal",
    0x9004 to "DateTimeDigitized",
    0x9101 to "ComponentsConfiguration",
    0x9102 to "CompressedBitsPerPixel",
    0x9201 to "ShutterSpeedValue",
    0x9202 to "ApertureValue",
    0x9203 to "BrightnessValue",
    0x9204 to "ExposureBiasValue",
    0x9205 to "MaxApertureValue",
    0x9206 to "SubjectDistance",
    0x9207 to "MeteringMode",
    0x9208 to "LightSource",
    0x9209 to "Flash",
    0x920A to "FocalLength",
    0x927C to "MakerNote",
    0x9286 to "UserComment",
    0x9290 to "SubSecTime",
    0x9291 to "SubSecTimeOriginal",
    0x9292 to "SubSecTimeDigitized",
    0xA000 to "FlashpixVersion",
    0xA001 to "ColorSpace",
    0xA002 to "PixelXDimension",
    0xA003 to "PixelYDimension",
    0xA005 to "InteroperabilityIFDPointer",
    0xA20E to "FocalPlaneXResolution",
    0xA20F to "FocalPlaneYResolution",
    0xA210 to "FocalPlaneResolutionUnit",
    0xA217 to "SensingMethod",
    0xA300 to "FileSource",
    0xA301 to "SceneType",
    0xA401 to "CustomRendered",
    0xA402 to "ExposureMode",
    0xA403 to "WhiteBalance",
    0xA404 to "DigitalZoomRatio",
    0xA405 to "FocalLengthIn35mmFilm",
    0xA406 to "SceneCaptureType",
    0xA408 to "Contrast",
    0xA409 to "Saturation",
    0xA40A to "Sharpness",
    0xA420 to "ImageUniqueID",
    0xA430 to "CameraOwnerName",
    0xA431 to "BodySerialNumber",
    0xA432 to "LensInfo",
    0xA433 to "LensMake",
    0xA434 to "LensModel",
    0xA435 to "LensSerialNumber",
    // GPS IFD
    0x0000 to "GPSVersionID",
    0x0001 to "GPSLatitudeRef",
    0x0002 to "GPSLatitude",
    0x0003 to "GPSLongitudeRef",
    0x0004 to "GPSLongitude",
    0x0005 to "GPSAltitudeRef",
    0x0006 to "GPSAltitude",
    0x0007 to "GPSTimeStamp",
    0x0008 to "GPSSatellites",
    0x0009 to "GPSStatus",
    0x000A to "GPSMeasureMode",
    0x000B to "GPSDOP",
    0x000C to "GPSSpeedRef",
    0x000D to "GPSSpeed",
    0x000E to "GPSTrackRef",
    0x000F to "GPSTrack",
    0x0010 to "GPSImgDirectionRef",
    0x0011 to "GPSImgDirection",
    0x0012 to "GPSMapDatum",
    0x0013 to "GPSDestLatitudeRef",
    0x0014 to "GPSDestLatitude",
    0x0015 to "GPSDestLongitudeRef",
    0x0016 to "GPSDestLongitude",
    0x0017 to "GPSDestBearingRef",
    0x0018 to "GPSDestBearing",
    0x0019 to "GPSDestDistanceRef",
    0x001A to "GPSDestDistance",
    0x001B to "GPSProcessingMethod",
    0x001C to "GPSAreaInformation",
    0x001D to "GPSDateStamp",
    0x001E to "GPSDifferential",
)
