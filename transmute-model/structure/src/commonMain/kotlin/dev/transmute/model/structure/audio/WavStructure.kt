@file:Suppress("unused")

package dev.transmute.model.structure.audio

import dev.transmute.model.core.MediaStructure
import dev.transmute.model.structure.audio.types.WavFmtChunk
import dev.transmute.model.structure.audio.types.WavRaw
import dev.transmute.model.structure.audio.types.chunks
import dev.transmute.model.structure.audio.types.dataChunk
import dev.transmute.model.structure.audio.types.fmt
import dev.transmute.model.structure.common.RiffChunkTree
import dev.transmute.model.structure.common.toTree
import kotlinx.serialization.Serializable

/**
 * Summary of a single WAV RIFF sub-chunk (4CC + size, no payload data).
 *
 * Examples: `LIST` (INFO metadata), `fact` (non-PCM sample count),
 * `smpl` (sampler loops), `cue ` (cue points), `id3 `, `bext` (broadcast).
 */
@Serializable
data class WavChunkSummary(
    /** 4-character ASCII chunk ID. */
    val id: String,
    /** Payload size in bytes (the RIFF `size` field, excluding the 8-byte header). */
    val dataSizeBytes: Long,
)

/**
 * Structured representation of a WAV file, following the RIFF WAVE container layout.
 *
 * ```
 * RIFF WAVE
 *   fmt    <- audio format, sample rate, channel count, bit depth
 *   data   <- raw audio samples (excluded - size in [dataChunkBytes])
 *   [fact] <- non-PCM sample count
 *   [LIST] <- INFO metadata / associated data list
 *   [smpl / cue  / id3  / bext / ...]
 * ```
 *
 * Audio sample data (`data` chunk payload) is excluded; [dataChunkBytes]
 * records its total size in bytes.
 */
@Serializable
data class WavStructure(
    /** Parsed `fmt ` chunk - audio format, sample rate, channels, bit depth, and extensions. */
    val fmtChunk: WavFmtChunk?,
    /** Payload size of the `data` chunk (raw PCM / IEEE-float samples) in bytes. */
    val dataChunkBytes: Long,
    /** Full recursive RIFF chunk hierarchy (payload bytes excluded). */
    val riff: RiffChunkTree,
    /** All RIFF sub-chunks other than `fmt ` and `data`, in file order. */
    val otherChunks: List<WavChunkSummary>,
) : MediaStructure

/**
 * Parse this [dev.transmute.model.structure.audio.types.WavRaw] into a [WavStructure].
 */
fun WavRaw.toStructure(): WavStructure =
    WavStructure(
        fmtChunk = fmt,
        dataChunkBytes = dataChunk?.size?.toLong() ?: 0L,
        riff = riff.toTree(),
        otherChunks = chunks
            .filter { it.id.value != "fmt " && it.id.value != "data" }
            .map { WavChunkSummary(it.id.value, it.size.toLong()) },
    )
