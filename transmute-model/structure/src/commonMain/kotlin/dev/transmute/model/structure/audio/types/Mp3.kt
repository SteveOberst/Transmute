@file:Suppress("unused")

package dev.transmute.model.structure.audio.types

import dev.transmute.model.core.Bitrate
import dev.transmute.model.core.ByteLength
import dev.transmute.model.core.Bytes
import dev.transmute.model.core.Channels
import dev.transmute.model.core.Hertz
import dev.transmute.model.core.RawMediaStructure
import dev.transmute.model.core.asBytes
import kotlinx.serialization.Serializable

// ===
//  MPEG enums
// ===

/** MPEG audio version. */
@Serializable
enum class MpegVersion { Mpeg1, Mpeg2, Mpeg25 }

/** MPEG audio layer. */
@Serializable
enum class MpegLayer { Layer1, Layer2, Layer3 }

/** MPEG audio channel mode. */
@Serializable
enum class MpegChannelMode { Stereo, JointStereo, DualChannel, Mono }

// ===
//  Typed models
// ===

/**
 * Parsed MPEG audio frame header (4 bytes).
 *
 * ```
 * | sync (11 b) | version (2 b) | layer (2 b) | protection (1 b) |
 * | bitrate (4 b) | sampleRate (2 b) | padding (1 b) | private (1 b) |
 * | channelMode (2 b) | modeExt (2 b) | copyright (1 b) | original (1 b) | emphasis (2 b) |
 * ```
 */
@Serializable
data class Mp3FrameHeader(
  val version: MpegVersion,
  val layer: MpegLayer,
  val bitrate: Bitrate,
  val sampleRate: Hertz,
  val channelMode: MpegChannelMode,
  val hasCrc: Boolean,
  val hasPadding: Boolean,
)

/**
 * VBR (variable bitrate) information from Xing / VBRI header.
 */
@Serializable
data class Mp3VbrInfo(val totalFrames: Int? = null, val totalBytes: ByteLength? = null, val qualityIndicator: Int? = null)

/**
 * ID3v1 tag (the last 128 bytes of an MP3 with the "TAG" marker).
 */
@Serializable
data class Mp3Id3v1Tag(
  val title: String,
  val artist: String,
  val album: String,
  val year: String,
  val comment: String,
  val track: Int?,
  val genre: Int,
)

// ===
//  MP3 file - complete on-disk representation
// ===

/**
 * Canonical representation of an MP3 file as written to disk.
 *
 * An MP3 file is a stream of MPEG audio frames optionally
 * preceded by an ID3v2 tag and/or followed by an ID3v1 tag:
 * ```
 * | [ID3v2 tag] | Frame 1 | Frame 2 | ... | [ID3v1 tag (128 B)] |
 * ```
 *
 * Storing every individual frame is impractical (thousands per file),
 * so the audio frames are held as a single opaque [audioData] blob.
 * Typed accessors parse the first-frame header and VBR info.
 */
@Serializable
data class Mp3Raw(
  /** Optional ID3v2 tag at the start of the file (raw bytes). */
  val id3v2Tag: Bytes?,
  /** Audio frame data (all MPEG frames concatenated). */
  val audioData: Bytes,
  /** Optional ID3v1 tag at the end of the file (raw 128 bytes). */
  val id3v1TagData: Bytes?,
) : RawMediaStructure {

  // --- Binary serialization ---

  override fun toBytes(): Bytes {
    val tag2 = id3v2Tag?.data ?: ByteArray(0)
    val audio = audioData.data
    val tag1 = id3v1TagData?.data ?: ByteArray(0)
    val out = ByteArray(tag2.size + audio.size + tag1.size)
    var pos = 0
    tag2.copyInto(out, pos)
    pos += tag2.size
    audio.copyInto(out, pos)
    pos += audio.size
    tag1.copyInto(out, pos)
    return out.asBytes()
  }
}

// --- Typed extension accessors ---

/** Parse the first MPEG frame header from the audio data. */
val Mp3Raw.firstFrameHeader: Mp3FrameHeader?
  get() {
    val d = audioData.data
    if (d.size < 4) return null
    var off = 0
    while (off < d.size - 3) {
      if ((d[off].toInt() and 0xFF) == 0xFF && (d[off + 1].toInt() and 0xE0) == 0xE0) break
      off++
    }
    if (off >= d.size - 3) return null
    val b1 = d[off + 1].toInt() and 0xFF
    val b2 = d[off + 2].toInt() and 0xFF
    val b3 = d[off + 3].toInt() and 0xFF
    val verBits = (b1 shr 3) and 0x03
    val version = when (verBits) {
      3 -> MpegVersion.Mpeg1
      2 -> MpegVersion.Mpeg2
      0 -> MpegVersion.Mpeg25
      else -> return null
    }
    val layerBits = (b1 shr 1) and 0x03
    val layer = when (layerBits) {
      3 -> MpegLayer.Layer1
      2 -> MpegLayer.Layer2
      1 -> MpegLayer.Layer3
      else -> return null
    }
    val hasCrc = (b1 and 0x01) == 0
    val brIdx = (b2 shr 4) and 0x0F
    val srIdx = (b2 shr 2) and 0x03
    val padding = (b2 and 0x02) != 0
    val chMode = when ((b3 shr 6) and 0x03) {
      0 -> MpegChannelMode.Stereo
      1 -> MpegChannelMode.JointStereo
      2 -> MpegChannelMode.DualChannel
      3 -> MpegChannelMode.Mono
      else -> MpegChannelMode.Stereo
    }
    val bitrate = lookupBitrate(version, layer, brIdx) ?: return null
    val sampleRate = lookupSampleRate(version, srIdx) ?: return null
    return Mp3FrameHeader(version, layer, Bitrate(bitrate.toLong()), Hertz(sampleRate), chMode, hasCrc, padding)
  }

/** Sample rate from the first audio frame. */
val Mp3Raw.sampleRate: Hertz?
  get() = firstFrameHeader?.sampleRate

/** Channel count inferred from channel mode. */
val Mp3Raw.channels: Channels?
  get() = firstFrameHeader?.channelMode?.let {
    Channels(if (it == MpegChannelMode.Mono) 1 else 2)
  }

/**
 * VBR info from Xing / VBRI header embedded in the first audio frame, if present.
 *
 * VBR-encoded files begin with a dummy padding frame whose side-information area holds
 * either a **Xing** / **Info** header or a **VBRI** header.
 * CBR files encoded with `--cbr` may also carry an `"Info"` tag.
 */
val Mp3Raw.vbrInfo: Mp3VbrInfo?
  get() {
    val hdr = firstFrameHeader ?: return null
    val d = audioData.data
    val sideInfoSize = when {
      hdr.version == MpegVersion.Mpeg1 && hdr.channelMode != MpegChannelMode.Mono -> 32
      hdr.version == MpegVersion.Mpeg1 -> 17
      hdr.channelMode != MpegChannelMode.Mono -> 17
      else -> 9
    }
    val xingOff = 4 + sideInfoSize
    if (d.size < xingOff + 8) return null
    val tag = d.decodeToString(xingOff, xingOff + 4)
    if (tag == "Xing" || tag == "Info") {
      fun be32(o: Int): UInt = (d[o].toUInt() and 0xFFu shl 24) or (d[o + 1].toUInt() and 0xFFu shl 16) or
        (d[o + 2].toUInt() and 0xFFu shl 8) or (d[o + 3].toUInt() and 0xFFu)
      val flags = be32(xingOff + 4)
      var off = xingOff + 8
      var frames: Int? = null
      var bytes: ByteLength? = null
      var quality: Int? = null
      if (flags and 0x01u != 0u && d.size >= off + 4) {
        frames = be32(off).toInt()
        off += 4
      }
      if (flags and 0x02u != 0u && d.size >= off + 4) {
        bytes = ByteLength(be32(off).toLong())
        off += 4
      }
      if (flags and 0x04u != 0u) off += 100 // skip 100-entry TOC
      if (flags and 0x08u != 0u && d.size >= off + 4) {
        quality = be32(off).toInt()
      }
      return Mp3VbrInfo(totalFrames = frames, totalBytes = bytes, qualityIndicator = quality)
    }
    // VBRI is at a fixed offset of 36 bytes from the frame start (always)
    val vbriOff = 36
    if (d.size >= vbriOff + 26) {
      val vbriTag = d.decodeToString(vbriOff, vbriOff + 4)
      if (vbriTag == "VBRI") {
        fun be32(o: Int): UInt = (d[o].toUInt() and 0xFFu shl 24) or (d[o + 1].toUInt() and 0xFFu shl 16) or
          (d[o + 2].toUInt() and 0xFFu shl 8) or (d[o + 3].toUInt() and 0xFFu)
        return Mp3VbrInfo(
          totalFrames = be32(vbriOff + 14).toInt(),
          totalBytes = ByteLength(be32(vbriOff + 10).toLong()),
        )
      }
    }
    return null
  }

/** Parsed ID3v1 tag. */
val Mp3Raw.id3v1Tag: Mp3Id3v1Tag?
  get() {
    val d = id3v1TagData?.data ?: return null
    if (d.size != 128) return null
    if (d[0].toInt().toChar() != 'T' || d[1].toInt().toChar() != 'A' || d[2].toInt().toChar() != 'G') return null
    fun str(start: Int, len: Int) = d.decodeToString(start, start + len).trimEnd('\u0000', ' ')
    val hasTrack = d[125].toInt() == 0 && d[126].toInt() != 0
    return Mp3Id3v1Tag(
      title = str(3, 30),
      artist = str(33, 30),
      album = str(63, 30),
      year = str(93, 4),
      comment = if (hasTrack) str(97, 28) else str(97, 30),
      track = if (hasTrack) (d[126].toInt() and 0xFF) else null,
      genre = d[127].toInt() and 0xFF,
    )
  }

// --- Bitrate & sample rate lookup tables ---

private fun lookupBitrate(ver: MpegVersion, layer: MpegLayer, idx: Int): Int? {
  if (idx == 0 || idx == 15) return null
  val table = when {
    ver == MpegVersion.Mpeg1 && layer == MpegLayer.Layer1 ->
      intArrayOf(0, 32, 64, 96, 128, 160, 192, 224, 256, 288, 320, 352, 384, 416, 448)
    ver == MpegVersion.Mpeg1 && layer == MpegLayer.Layer2 ->
      intArrayOf(0, 32, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320, 384)
    ver == MpegVersion.Mpeg1 && layer == MpegLayer.Layer3 ->
      intArrayOf(0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320)
    layer == MpegLayer.Layer1 ->
      intArrayOf(0, 32, 48, 56, 64, 80, 96, 112, 128, 144, 160, 176, 192, 224, 256)
    else ->
      intArrayOf(0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160)
  }
  return table.getOrNull(idx)?.times(1000)
}

private fun lookupSampleRate(ver: MpegVersion, idx: Int): Int? {
  if (idx == 3) return null
  val table = when (ver) {
    MpegVersion.Mpeg1 -> intArrayOf(44100, 48000, 32000)
    MpegVersion.Mpeg2 -> intArrayOf(22050, 24000, 16000)
    MpegVersion.Mpeg25 -> intArrayOf(11025, 12000, 8000)
  }
  return table.getOrNull(idx)
}
