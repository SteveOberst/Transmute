@file:Suppress("unused")

package dev.transmute.model.structure.audio.types

import dev.transmute.model.core.BitsPerSample
import dev.transmute.model.core.Bytes
import dev.transmute.model.core.Channels
import dev.transmute.model.core.Hertz
import dev.transmute.model.core.RawMediaStructure
import dev.transmute.model.core.asBytes
import kotlinx.serialization.Serializable

// ================================================================
//  FLAC metadata block type
// ================================================================

/**
 * FLAC metadata block type codes.
 */
@Serializable
enum class FlacMetadataBlockType(val code: Int) {
  StreamInfo(0),
  Padding(1),
  Application(2),
  SeekTable(3),
  VorbisComment(4),
  CueSheet(5),
  Picture(6),
  Unknown(-1),
  ;

  companion object {
    fun fromCode(code: Int): FlacMetadataBlockType = entries.firstOrNull { it.code == code } ?: Unknown
  }
}

// ================================================================
//  FLAC metadata block - fundamental structural unit
// ================================================================

/**
 * A single FLAC metadata block as stored on disk.
 *
 * Each block has a 4-byte header:
 * ```
 * | isLast (1 bit) | type (7 bits) | length (24 bits BE) |
 * ```
 * followed by [data] of the specified length.
 */
@Serializable
data class FlacMetadataBlock(
  val type: FlacMetadataBlockType,
  val isLast: Boolean,
  /** Block body (does NOT include the 4-byte header). */
  val data: Bytes,
) {
  /** Serialize this block back to its on-disk representation. */
  fun toBytes(): Bytes {
    val body = data.data
    val len = body.size
    val out = ByteArray(4 + len)
    val typeByte = (type.code and 0x7F) or (if (isLast) 0x80 else 0)
    out[0] = typeByte.toByte()
    out[1] = ((len shr 16) and 0xFF).toByte()
    out[2] = ((len shr 8) and 0xFF).toByte()
    out[3] = (len and 0xFF).toByte()
    body.copyInto(out, 4)
    return out.asBytes()
  }
}

// ================================================================
//  Typed model: STREAMINFO block
// ================================================================

/**
 * Parsed STREAMINFO metadata (block type 0, always 34 bytes).
 *
 * ```
 * | minBlockSize (2 B) | maxBlockSize (2 B) | minFrameSize (3 B) | maxFrameSize (3 B) |
 * | sampleRate (20 b) | channels-1 (3 b) | bps-1 (5 b) | totalSamples (36 b) |
 * | md5 (16 B) |
 * ```
 */
@Serializable
data class FlacStreamInfo(
  val minBlockSize: Int,
  val maxBlockSize: Int,
  val minFrameSize: Int,
  val maxFrameSize: Int,
  val sampleRate: Int,
  val channels: Int,
  val bitsPerSample: Int,
  val totalSamples: Long,
  val md5: Bytes,
)

// ================================================================
//  FLAC file - complete on-disk representation
// ================================================================

/**
 * Canonical representation of a FLAC file as written to disk.
 *
 * A FLAC file consists of:
 * ```
 * | "fLaC" (4 B) | Metadata Block 0 (STREAMINFO) | ... | Audio Frames |
 * ```
 */
@Serializable
data class FlacRaw(
  /** Metadata blocks in file order (first is always STREAMINFO). */
  val metadataBlocks: List<FlacMetadataBlock>,
  /** Raw audio frame data following the last metadata block. */
  val audioData: Bytes,
) : RawMediaStructure {

  // --- Binary serialization ---

  override fun toBytes(): Bytes {
    val marker = SIGNATURE
    val blockParts = metadataBlocks.map { it.toBytes().data }
    val audio = audioData.data
    val total = marker.size + blockParts.sumOf { it.size } + audio.size
    val out = ByteArray(total)
    var pos = 0
    marker.copyInto(out, pos)
    pos += marker.size
    for (part in blockParts) {
      part.copyInto(out, pos)
      pos += part.size
    }
    audio.copyInto(out, pos)
    return out.asBytes()
  }

  companion object {
    /** "fLaC" magic bytes. */
    val SIGNATURE = byteArrayOf(0x66, 0x4C, 0x61, 0x43)
  }
}

// --- Typed extension accessors ---

/** The STREAMINFO block (always the first block). */
val FlacRaw.streamInfoBlock: FlacMetadataBlock?
  get() = metadataBlocks.firstOrNull { it.type == FlacMetadataBlockType.StreamInfo }

/** Parsed STREAMINFO data. */
val FlacRaw.streamInfo: FlacStreamInfo?
  get() {
    val d = streamInfoBlock?.data?.data ?: return null
    if (d.size < 34) return null
    fun u16(off: Int) = ((d[off].toInt() and 0xFF) shl 8) or (d[off + 1].toInt() and 0xFF)
    fun u24(off: Int) = ((d[off].toInt() and 0xFF) shl 16) or
      ((d[off + 1].toInt() and 0xFF) shl 8) or (d[off + 2].toInt() and 0xFF)
    val packed = LongArray(8) { d[10 + it].toLong() and 0xFF }
    val sr = ((packed[0] shl 12) or (packed[1] shl 4) or (packed[2] shr 4)).toInt()
    val ch = (((packed[2] shr 1) and 0x07).toInt()) + 1
    val bps = ((((packed[2] and 0x01) shl 4) or (packed[3] shr 4)).toInt()) + 1
    val total = ((packed[3] and 0x0F) shl 32) or (packed[4] shl 24) or
      (packed[5] shl 16) or (packed[6] shl 8) or packed[7]
    return FlacStreamInfo(
      minBlockSize = u16(0),
      maxBlockSize = u16(2),
      minFrameSize = u24(4),
      maxFrameSize = u24(7),
      sampleRate = sr,
      channels = ch,
      bitsPerSample = bps,
      totalSamples = total,
      md5 = d.copyOfRange(18, 34).asBytes(),
    )
  }

/** Sample rate from STREAMINFO. */
val FlacRaw.sampleRate: Hertz?
  get() = streamInfo?.sampleRate?.let { Hertz(it) }

/** Number of audio channels from STREAMINFO. */
val FlacRaw.channels: Channels?
  get() = streamInfo?.channels?.let { Channels(it) }

/** Bits per sample from STREAMINFO. */
val FlacRaw.bitsPerSample: BitsPerSample?
  get() = streamInfo?.bitsPerSample?.let { BitsPerSample(it) }

/** Vorbis comment block, if present. */
val FlacRaw.vorbisCommentBlock: FlacMetadataBlock?
  get() = metadataBlocks.firstOrNull { it.type == FlacMetadataBlockType.VorbisComment }

/** Picture block(s), if present. */
val FlacRaw.pictureBlocks: List<FlacMetadataBlock>
  get() = metadataBlocks.filter { it.type == FlacMetadataBlockType.Picture }
