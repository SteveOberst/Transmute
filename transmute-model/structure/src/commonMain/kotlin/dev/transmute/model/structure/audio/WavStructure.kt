@file:Suppress("unused")

package dev.transmute.model.structure.audio

import dev.transmute.model.core.MediaStructure
import kotlinx.serialization.Serializable

/**
 * Structured, JSON-safe representation of a WAV file.
 *
 * Audio sample data (the `data` chunk) is excluded; only its byte count
 * is captured in [dataBytesTotal].  Format metadata from the `fmt ` chunk
 * is included in full.
 */
@Serializable
data class WavStructure(
    /** Audio format code (1 = PCM, 3 = IEEE float, 0xFFFE = Extensible, …). */
    val audioFormat: UShort?,
    /** Number of audio channels. */
    val channels: UShort?,
    /** Sample rate in Hz. */
    val sampleRate: UInt?,
    /** Average byte rate. */
    val byteRate: UInt?,
    /** Block alignment. */
    val blockAlign: UShort?,
    /** Bit depth. */
    val bitsPerSample: UShort?,
    /** Total size of the `data` chunk (audio samples) in bytes. */
    val dataBytesTotal: Long,
) : MediaStructure

/**
 * Parse this [WavRaw] into a [WavStructure].
 */
fun WavRaw.toStructure(): WavStructure {
    val f = fmt
    val dataSize = dataChunk?.data?.size?.toLong() ?: 0L
    return WavStructure(
        audioFormat = f?.audioFormat,
        channels = f?.numChannels,
        sampleRate = f?.sampleRate,
        byteRate = f?.byteRate,
        blockAlign = f?.blockAlign,
        bitsPerSample = f?.bitsPerSample,
        dataBytesTotal = dataSize,
    )
}
