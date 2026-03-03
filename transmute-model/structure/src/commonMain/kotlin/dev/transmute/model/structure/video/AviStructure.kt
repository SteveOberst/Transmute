@file:Suppress("unused")

package dev.transmute.model.structure.video

import dev.transmute.model.core.MediaStructure
import dev.transmute.model.structure.common.RiffChunkTree
import dev.transmute.model.structure.common.toTree
import dev.transmute.model.structure.video.types.AviMainHeader
import dev.transmute.model.structure.video.types.AviRaw
import dev.transmute.model.structure.video.types.headerList
import dev.transmute.model.structure.video.types.indexChunk
import dev.transmute.model.structure.video.types.mainHeader
import dev.transmute.model.structure.video.types.movieList
import kotlinx.serialization.Serializable

/**
 * Parsed AVI stream header (`strh` chunk, 56 bytes).
 *
 * ```
 * | fccType (4 B) | fccHandler (4 B) | flags (4 B) | priority (2 B) |
 * | language (2 B) | initialFrames (4 B) | scale (4 B) | rate (4 B) |
 * | start (4 B) | length (4 B) | suggestedBufferSize (4 B) |
 * | quality (4 B) | sampleSize (4 B) | frame RECT (8 B) |
 * ```
 */
@Serializable
data class AviStreamHeader(
    /** Stream type: `vids` (video), `auds` (audio), `mids` (MIDI), `txts` (text). */
    val fccType: String,
    /** Codec / handler FOURCC (e.g. `xvid`, `MP4V`, `mp3 `, `avc1`). */
    val fccHandler: String,
    /** Time scale denominator (frame-rate = rate / scale). */
    val scale: UInt,
    /** Time scale numerator. */
    val rate: UInt,
    /** Stream start offset in scale / rate units. */
    val start: UInt,
    /** Length of the stream in scale / rate units. */
    val length: UInt,
    /** Suggested playback buffer size in bytes (0 = unspecified). */
    val suggestedBufferSize: UInt,
    /** Encoding quality (0-10 000; 1 = default). */
    val quality: Int,
    /** Sample size in bytes (0 = variable-size samples). */
    val sampleSize: UInt,
)

/**
 * Descriptor for one logical AVI stream (one `strl` LIST inside `hdrl`).
 *
 * Pairs the typed `strh` (stream header) with the byte size of the
 * `strf` (stream format) payload.  The raw format bytes are codec-specific
 * (`BITMAPINFOHEADER` for video, `WAVEFORMATEX` for audio, etc.) and are
 * excluded from the structure.
 */
@Serializable
data class AviStreamDescriptor(
    /** Parsed `strh` chunk. `null` if the chunk is absent or malformed. */
    val header: AviStreamHeader?,
    /** Size of the `strf` (stream format) payload in bytes. */
    val formatDataBytes: Int,
)

/**
 * Structured representation of an AVI (RIFF AVI ) file, following the RIFF container layout.
 *
 * ```
 * RIFF AVI
 *   LIST hdrl
 *     avih         <- main AVI header (micro-seconds/frame, frame count, ...)
 *     LIST strl    <- one per logical stream
 *       strh       <- stream header (type, codec, timing)
 *       strf       <- stream format (BITMAPINFOHEADER / WAVEFORMATEX / ...)
 *       [strn]     <- optional stream name
 *       [strd]     <- optional codec-specific data
 *   LIST movi      <- interleaved audio/video frame data (excluded)
 *   [idx1]         <- optional legacy chunk index
 * ```
 *
 * Movie data (`movi` LIST payload) is excluded; [movieDataBytes] records
 * its total size.  All header metadata is preserved in full.
 */
@Serializable
data class AviStructure(
    /** Parsed `avih` chunk - global stream parameters. */
    val mainHeader: AviMainHeader?,
    /** Descriptor for each logical stream (video, audio, ...) in `hdrl` order. */
    val streams: List<AviStreamDescriptor>,
    /** Payload size of the `movi` LIST (all interleaved frame data) in bytes. */
    val movieDataBytes: Long,
    /** `true` when a legacy `idx1` chunk index is present. */
    val hasIndex: Boolean,
    /** Full recursive RIFF chunk hierarchy (payload bytes excluded). */
    val riff: RiffChunkTree,
) : MediaStructure

/**
 * Parse this [dev.transmute.model.structure.video.types.AviRaw] into an [AviStructure].
 */
fun AviRaw.toStructure(): AviStructure =
    AviStructure(
        mainHeader = mainHeader,
        streams = parseAviStreams(),
        movieDataBytes = movieList?.size?.toLong() ?: 0L,
        hasIndex = indexChunk != null,
        riff = riff.toTree(),
    )

private fun AviRaw.parseAviStreams(): List<AviStreamDescriptor> {
    val hdrl = headerList ?: return emptyList()
    return hdrl.children
        .filter { it.id.value == "LIST" && it.formType?.value == "strl" }
        .map { strl ->
            val strh = strl.children.firstOrNull { it.id.value == "strh" }
            val strf = strl.children.firstOrNull { it.id.value == "strf" }
            AviStreamDescriptor(
                header = strh?.let { parseAviStreamHeader(it.data.data) },
                formatDataBytes = strf?.data?.size ?: 0,
            )
        }
}

private fun parseAviStreamHeader(d: ByteArray): AviStreamHeader? {
    if (d.size < 56) return null
    fun fcc(off: Int) = String(CharArray(4) { d[off + it].toInt().and(0xFF).toChar() })
    fun u32(off: Int): UInt = (d[off].toUInt() and 0xFFu) or
        ((d[off + 1].toUInt() and 0xFFu) shl 8) or
        ((d[off + 2].toUInt() and 0xFFu) shl 16) or
        ((d[off + 3].toUInt() and 0xFFu) shl 24)
    return AviStreamHeader(
        fccType = fcc(0),
        fccHandler = fcc(4),
        scale = u32(20),
        rate = u32(24),
        start = u32(28),
        length = u32(32),
        suggestedBufferSize = u32(36),
        quality = u32(40).toInt(),
        sampleSize = u32(44),
    )
}
