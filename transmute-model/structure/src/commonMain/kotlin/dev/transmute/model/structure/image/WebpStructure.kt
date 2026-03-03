@file:Suppress("unused")

package dev.transmute.model.structure.image

import dev.transmute.model.core.MediaStructure
import dev.transmute.model.structure.common.RiffChunkTree
import dev.transmute.model.structure.common.toTree
import dev.transmute.model.structure.image.types.WebpFormat
import dev.transmute.model.structure.image.types.WebpRaw
import dev.transmute.model.structure.image.types.chunks
import dev.transmute.model.structure.image.types.format
import dev.transmute.model.structure.image.types.hasAlpha
import dev.transmute.model.structure.image.types.hasAnimation
import kotlinx.serialization.Serializable

/**
 * Summary of a single WebP RIFF sub-chunk (4CC + size, no payload data).
 *
 * Known IDs: `VP8 ` (lossy), `VP8L` (lossless), `VP8X` (extended),
 * `ALPH` (alpha), `ANIM` (animation control), `ANMF` (animation frame),
 * `EXIF`, `XMP `, `ICCP`.
 */
@Serializable
data class WebpChunkSummary(
    /** 4-character ASCII chunk ID. */
    val id: String,
    /** Payload size in bytes (excludes the 8-byte chunk header). */
    val dataSizeBytes: UInt,
)

/**
 * Structured representation of a WebP file, mirroring the on-disk RIFF layout.
 *
 * ```
 * RIFF WEBP
 *   VP8  / VP8L / VP8X          <- compressed image / feature block
 *   [ALPH]                       <- alpha bitstream (VP8X only)
 *   [ANIM] [ANMF ...]            <- animation control / frames
 *   [EXIF] [XMP ] [ICCP]        <- metadata chunks
 * ```
 *
 * Compressed image / alpha bitstream payloads are excluded; [chunks] lists
 * all sub-chunks with their IDs and payload sizes.
 */
@Serializable
data class WebpStructure(
    /** Encoding variant inferred from the first sub-chunk ID. */
    val format: WebpFormat,
    /** `true` when an alpha channel is present (`ALPH` chunk or `VP8X.alphaFlag`). */
    val hasAlpha: Boolean,
    /** `true` when the file is an animated WebP (`ANIM` + `ANMF` chunks). */
    val hasAnimation: Boolean,
    /** Full recursive RIFF chunk hierarchy (payload bytes excluded). */
    val riff: RiffChunkTree,
    /** All RIFF sub-chunks in file order (payload data excluded). */
    val chunks: List<WebpChunkSummary>,
) : MediaStructure

/**
 * Parse this [dev.transmute.model.structure.image.types.WebpRaw] into a [WebpStructure].
 */
fun WebpRaw.toStructure(): WebpStructure =
    WebpStructure(
        format = format,
        hasAlpha = hasAlpha,
        hasAnimation = hasAnimation,
        riff = riff.toTree(),
        chunks = chunks.map { WebpChunkSummary(it.id.value, it.size) },
    )
