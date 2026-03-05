@file:Suppress("unused")

package dev.transmute.model.structure.audio.types

import dev.transmute.model.core.Bytes
import dev.transmute.model.core.Channels
import dev.transmute.model.core.Hertz
import dev.transmute.model.core.RawMediaStructure
import dev.transmute.model.core.concatToBytes
import dev.transmute.model.structure.common.OggPage
import dev.transmute.model.structure.common.OggSerialNumber
import kotlinx.serialization.Serializable

// --- Typed model for Vorbis identification header ---

/**
 * Parsed Vorbis identification header (first packet in the Vorbis stream).
 */
@Serializable
data class VorbisIdentification(
  val vorbisVersion: UInt,
  val channels: UByte,
  val sampleRate: UInt,
  val bitrateMaximum: Int,
  val bitrateNominal: Int,
  val bitrateMinimum: Int,
  val blockSize0: Int,
  val blockSize1: Int,
)

// --- Ogg Vorbis file - complete on-disk representation ---

/**
 * Canonical representation of an Ogg Vorbis file as written to disk.
 *
 * The file is a sequence of Ogg pages.  The first few pages of each
 * logical stream carry the Vorbis identification, comment, and setup
 * headers.
 */
@Serializable
data class OggAudioRaw(
  /** All Ogg pages in file order. */
  val pages: List<OggPage>,
) : RawMediaStructure {

  // --- Binary serialization ---

  override fun toBytes(): Bytes = pages.concatToBytes()
}

// --- Typed extension accessors ---

/** Distinct logical stream serial numbers present in the file. */
val OggAudioRaw.streamSerialNumbers: List<OggSerialNumber>
  get() = pages.map { it.serialNumber }.distinct()

/** Pages belonging to a given serial number. */
fun OggAudioRaw.pagesForStream(serial: OggSerialNumber): List<OggPage> = pages.filter { it.serialNumber == serial }

/** Parsed Vorbis identification header from the first BOS page. */
val OggAudioRaw.vorbisIdentification: VorbisIdentification?
  get() {
    val bos = pages.firstOrNull { (it.headerType.toInt() and 0x02) != 0 } ?: return null
    val d = bos.data.data
    if (d.size < 30) return null
    if (d[0].toInt() != 1) return null
    val magic = d.decodeToString(1, 7)
    if (magic != "vorbis") return null
    fun u32(off: Int) = (d[off].toUInt() and 0xFFu) or ((d[off + 1].toUInt() and 0xFFu) shl 8) or
      ((d[off + 2].toUInt() and 0xFFu) shl 16) or ((d[off + 3].toUInt() and 0xFFu) shl 24)
    fun i32(off: Int) = u32(off).toInt()
    val blockSizes = d[28].toInt() and 0xFF
    return VorbisIdentification(
      vorbisVersion = u32(7),
      channels = d[11].toUByte(),
      sampleRate = u32(12),
      bitrateMaximum = i32(16),
      bitrateNominal = i32(20),
      bitrateMinimum = i32(24),
      blockSize0 = 1 shl (blockSizes and 0x0F),
      blockSize1 = 1 shl ((blockSizes shr 4) and 0x0F),
    )
  }

/** Sample rate from the Vorbis identification header. */
val OggAudioRaw.sampleRate: Hertz?
  get() = vorbisIdentification?.sampleRate?.toInt()?.let { Hertz(it) }

/** Channel count from the Vorbis identification header. */
val OggAudioRaw.channels: Channels?
  get() = vorbisIdentification?.channels?.toInt()?.let { Channels(it) }
