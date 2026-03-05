@file:Suppress("unused")

package dev.transmute.structure.image

import dev.transmute.model.core.MediaMetadata
import dev.transmute.model.metadata.png.PngTextMetadata
import dev.transmute.model.metadata.png.PngTextChunk
import dev.transmute.model.structure.image.types.PngRaw
import dev.transmute.model.structure.image.types.iccp
import dev.transmute.structure.util.inflateBytes

/**
 * Extract metadata from a parsed [dev.transmute.model.structure.image.types.PngRaw].
 *
 * Supports:
 * - **EXIF** - `eXIf` chunk (registered 2017) containing a raw TIFF byte stream
 * - **ICC Profile** - `iCCP` chunk (compressed ICC profile via zlib / deflate)
 * - **XMP** - `iTXt` chunk with keyword `"XML:com.adobe.xmp"` (per XMP spec)
 * - **PNG Text** - aggregated `tEXt` / `zTXt` / `iTXt` entries
 */
fun PngRaw.extractMetadata(): List<MediaMetadata> = buildList {
  extractExif()?.let(::add)
  extractIcc()?.let(::add)
  extractXmp()?.let(::add)
  extractText()?.let(::add)
}

// -- EXIF via eXIf chunk ------------------------------------------------------

private fun PngRaw.extractExif(): MediaMetadata? {
  val chunk = chunks.firstOrNull { it.type.value == "eXIf" } ?: return null
  val data = chunk.data
  if (data.size < 8) return null
  return try {
    val reader = TiffStructureReader()
    val tiffRaw = reader.read(data)
    tiffRawToExif(tiffRaw)
  } catch (_: Exception) {
    null
  }
}

// -- ICC via iCCP chunk -------------------------------------------------------

/**
 * The iCCP chunk stores a zlib-compressed ICC profile. The profile data after the
 * compression method byte is deflate-compressed. We inflate it and then delegate
 * to [parseIccProfile].
 */
private fun PngRaw.extractIcc(): MediaMetadata? {
  val iccp = iccp ?: return null
  val compressed = iccp.compressedProfile.data
  if (compressed.isEmpty()) return null
  return try {
    val decompressed = inflateBytes(compressed)
    if (decompressed.size < 128) return null
    parseIccProfile(decompressed)
  } catch (_: Exception) {
    null
  }
}

// -- XMP via iTXt chunk -------------------------------------------------------

private fun PngRaw.extractXmp(): MediaMetadata? {
  val xmp = chunks.firstOrNull { it.type.value == "iTXt" }
    ?.let { chunk ->
      val d = chunk.data.data
      val kwEnd = d.indexOf(0)
      if (kwEnd <= 0) return@let null
      val keyword = decodeLatin1(d.copyOfRange(0, kwEnd))
      if (keyword != "XML:com.adobe.xmp") return@let null
      val payload = parseItxtPayload(d, kwEnd + 1) ?: return@let null
      val textBytes = payload.textBytes
      val text = try {
        if (payload.compressed) inflateBytes(textBytes).decodeToString() else textBytes.decodeToString()
      } catch (_: Exception) {
        return@let null
      }
      parseXmpText(text)
    }
  return xmp
}

// -- PNG text metadata (tEXt / zTXt / iTXt) ----------------------------------

private fun PngRaw.extractText(): PngTextMetadata? {
  val out = mutableListOf<PngTextChunk>()

  for (chunk in chunks) {
    val d = chunk.data.data
    when (chunk.type.value) {
      "tEXt" -> {
        val nullIdx = d.indexOf(0)
        if (nullIdx <= 0) continue
        val keyword = decodeLatin1(d.copyOfRange(0, nullIdx))
        val text = decodeLatin1(d.copyOfRange(nullIdx + 1, d.size))
        out += PngTextChunk.Text(
          keyword = dev.transmute.model.core.Latin1String(keyword),
          text = dev.transmute.model.core.Latin1String(text),
          payload = dev.transmute.model.metadata.common.PayloadRef(sizeBytes = d.size.toULong()),
        )
      }
      "zTXt" -> {
        val nullIdx = d.indexOf(0)
        if (nullIdx <= 0 || nullIdx + 1 >= d.size) continue
        val keyword = decodeLatin1(d.copyOfRange(0, nullIdx))
        val method = d[nullIdx + 1].toUByte()
        val compressed = d.copyOfRange(nullIdx + 2, d.size)
        val (text, err) = try {
          decodeLatin1(inflateBytes(compressed)) to null
        } catch (e: Exception) {
          null to e.message
        }
        out += PngTextChunk.ZText(
          keyword = dev.transmute.model.core.Latin1String(keyword),
          compressionMethod = method,
          compressedText = dev.transmute.model.metadata.common.PayloadRef(sizeBytes = compressed.size.toULong()),
          text = text?.let { dev.transmute.model.core.Latin1String(it) },
          decodeError = err,
        )
      }
      "iTXt" -> {
        val kwEnd = d.indexOf(0)
        if (kwEnd <= 0) continue
        val keyword = decodeLatin1(d.copyOfRange(0, kwEnd))
        if (keyword == "XML:com.adobe.xmp") continue
        val payload = parseItxtPayload(d, kwEnd + 1) ?: continue
        val language = payload.languageTag.takeIf { it.isNotEmpty() }?.let { dev.transmute.model.core.LanguageTag(it) }
        val translated = payload.translatedKeyword.takeIf { it.isNotEmpty() }?.let { dev.transmute.model.core.Utf8String(it) }
        val (text, err) = try {
          val bytes = if (payload.compressed) inflateBytes(payload.textBytes) else payload.textBytes
          dev.transmute.model.core.Utf8String(bytes.decodeToString()) to null
        } catch (e: Exception) {
          null to e.message
        }
        out += PngTextChunk.IText(
          keyword = dev.transmute.model.core.Latin1String(keyword),
          compressed = payload.compressed,
          compressionMethod = payload.compressionMethod,
          languageTag = language,
          translatedKeyword = translated,
          text = text,
          compressedText = if (payload.compressed) dev.transmute.model.metadata.common.PayloadRef(sizeBytes = payload.textBytes.size.toULong()) else null,
          decodeError = err,
        )
      }
    }
  }

  return if (out.isEmpty()) null else PngTextMetadata(chunks = out)
}

private data class ItxtPayload(
  val compressed: Boolean,
  val compressionMethod: UByte,
  val languageTag: String,
  val translatedKeyword: String,
  val textBytes: ByteArray,
)

private fun parseItxtPayload(d: ByteArray, offset: Int): ItxtPayload? {
  if (offset + 2 >= d.size) return null
  val compressionFlag = d[offset].toUByte()
  val compressionMethod = d[offset + 1].toUByte()
  fun nextNull(from: Int): Int = indexOfByte(d, 0, from)
  val langStart = offset + 2
  val langEnd = nextNull(langStart)
  if (langEnd < 0) return null
  val trKwStart = langEnd + 1
  val trKwEnd = nextNull(trKwStart)
  if (trKwEnd < 0) return null
  val textStart = trKwEnd + 1
  val compressed = compressionFlag.toInt() != 0
  val languageTag = d.copyOfRange(langStart, langEnd).decodeToString()
  val translatedKeyword = d.copyOfRange(trKwStart, trKwEnd).decodeToString()
  val textBytes = if (textStart <= d.size) d.copyOfRange(textStart, d.size) else ByteArray(0)
  return ItxtPayload(compressed, compressionMethod, languageTag, translatedKeyword, textBytes)
}

private fun decodeLatin1(bytes: ByteArray): String = buildString(bytes.size) {
  for (b in bytes) append((b.toInt() and 0xFF).toChar())
}

private fun indexOfByte(d: ByteArray, value: Int, from: Int): Int {
  var i = from.coerceAtLeast(0)
  while (i < d.size) {
    if ((d[i].toInt() and 0xFF) == value) return i
    i++
  }
  return -1
}
