@file:Suppress("unused")

package dev.transmute.model.structure

import dev.transmute.model.core.MediaStructure as CoreMediaStructure
import dev.transmute.model.core.RawMediaStructure
import dev.transmute.model.structure.audio.toStructure
import dev.transmute.model.structure.audio.types.AacRaw
import dev.transmute.model.structure.audio.types.FlacRaw
import dev.transmute.model.structure.audio.types.M4aRaw
import dev.transmute.model.structure.audio.types.Mp3Raw
import dev.transmute.model.structure.audio.types.OggAudioRaw
import dev.transmute.model.structure.audio.types.OpusRaw
import dev.transmute.model.structure.audio.types.WavRaw
import dev.transmute.model.structure.image.toStructure
import dev.transmute.model.structure.image.types.AvifRaw
import dev.transmute.model.structure.image.types.BmpRaw
import dev.transmute.model.structure.image.types.GifRaw
import dev.transmute.model.structure.image.types.HeifRaw
import dev.transmute.model.structure.image.types.JpegRaw
import dev.transmute.model.structure.image.types.PngRaw
import dev.transmute.model.structure.image.types.TiffRaw
import dev.transmute.model.structure.image.types.WebpRaw
import dev.transmute.model.structure.video.toStructure
import dev.transmute.model.structure.video.types.AviRaw
import dev.transmute.model.structure.video.types.MkvRaw
import dev.transmute.model.structure.video.types.MovRaw
import dev.transmute.model.structure.video.types.Mp4Raw
import dev.transmute.model.structure.video.types.WebmRaw

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
  // -- Image ----------------------------------------------------------------
  is PngRaw -> toStructure()
  is JpegRaw -> toStructure()
  is BmpRaw -> toStructure()
  is GifRaw -> toStructure()
  is TiffRaw -> toStructure()
  is WebpRaw -> toStructure()
  is HeifRaw -> toStructure()
  is AvifRaw -> toStructure()
  // -- Audio ----------------------------------------------------------------
  is WavRaw -> toStructure()
  is Mp3Raw -> toStructure()
  is FlacRaw -> toStructure()
  is AacRaw -> toStructure()
  is M4aRaw -> toStructure()
  is OggAudioRaw -> toStructure()
  is OpusRaw -> toStructure()
  // -- Video ----------------------------------------------------------------
  is Mp4Raw -> toStructure()
  is MovRaw -> toStructure()
  is WebmRaw -> toStructure()
  is MkvRaw -> toStructure()
  is AviRaw -> toStructure()

  else -> throw IllegalArgumentException(
    "No MediaStructure conversion registered for ${this::class.simpleName}",
  )
}
