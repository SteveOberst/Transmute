@file:Suppress("unused")

package dev.transmute.model.structure

import dev.transmute.model.core.MediaStructure as CoreMediaStructure
import dev.transmute.model.core.RawMediaStructure
import dev.transmute.model.structure.audio.AacRaw
import dev.transmute.model.structure.audio.FlacRaw
import dev.transmute.model.structure.audio.M4aRaw
import dev.transmute.model.structure.audio.Mp3Raw
import dev.transmute.model.structure.audio.OggAudioRaw
import dev.transmute.model.structure.audio.OpusRaw
import dev.transmute.model.structure.audio.WavRaw
import dev.transmute.model.structure.audio.toStructure
import dev.transmute.model.structure.image.AvifRaw
import dev.transmute.model.structure.image.BmpRaw
import dev.transmute.model.structure.image.GifRaw
import dev.transmute.model.structure.image.HeifRaw
import dev.transmute.model.structure.image.JpegRaw
import dev.transmute.model.structure.image.PngRaw
import dev.transmute.model.structure.image.TiffRaw
import dev.transmute.model.structure.image.WebpRaw
import dev.transmute.model.structure.image.toStructure
import dev.transmute.model.structure.video.AviRaw
import dev.transmute.model.structure.video.MkvRaw
import dev.transmute.model.structure.video.MovRaw
import dev.transmute.model.structure.video.Mp4Raw
import dev.transmute.model.structure.video.WebmRaw
import dev.transmute.model.structure.video.toStructure

/**
 * Converts any built-in [RawMediaStructure] to its corresponding
 * JSON-safe [dev.transmute.model.core.MediaStructure] type.
 *
 * The conversion routes to the format-specific `toStructure()` extension
 * defined alongside each `*Structure` data class, which maps byte-faithful
 * raw fields to the summary / typed view.
 *
 * @throws IllegalArgumentException if [this] is not a recognised built-in format.
 */
fun RawMediaStructure.toMediaStructure(): CoreMediaStructure = when (this) {
    // ── Image ────────────────────────────────────────────────────────────────
    is PngRaw      -> toStructure()
    is JpegRaw     -> toStructure()
    is BmpRaw      -> toStructure()
    is GifRaw      -> toStructure()
    is TiffRaw     -> toStructure()
    is WebpRaw     -> toStructure()
    is HeifRaw     -> toStructure()
    is AvifRaw     -> toStructure()
    // ── Audio ────────────────────────────────────────────────────────────────
    is WavRaw      -> toStructure()
    is Mp3Raw      -> toStructure()
    is FlacRaw     -> toStructure()
    is AacRaw      -> toStructure()
    is M4aRaw      -> toStructure()
    is OggAudioRaw -> toStructure()
    is OpusRaw     -> toStructure()
    // ── Video ────────────────────────────────────────────────────────────────
    is Mp4Raw  -> toStructure()
    is MovRaw  -> toStructure()
    is WebmRaw -> toStructure()
    is MkvRaw  -> toStructure()
    is AviRaw  -> toStructure()

    else -> throw IllegalArgumentException(
        "No MediaStructure conversion registered for ${this::class.simpleName}"
    )
}
