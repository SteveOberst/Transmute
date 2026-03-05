@file:Suppress("unused")

package dev.transmute.structure

import dev.transmute.structure.audio.AacStructureReader
import dev.transmute.structure.audio.FlacStructureReader
import dev.transmute.structure.audio.M4aStructureReader
import dev.transmute.structure.audio.Mp3StructureReader
import dev.transmute.structure.audio.OggAudioStructureReader
import dev.transmute.structure.audio.OpusStructureReader
import dev.transmute.structure.audio.WavStructureReader
import dev.transmute.structure.image.AvifStructureReader
import dev.transmute.structure.image.BmpStructureReader
import dev.transmute.structure.image.GifStructureReader
import dev.transmute.structure.image.HeifStructureReader
import dev.transmute.structure.image.JpegStructureReader
import dev.transmute.structure.image.PngStructureReader
import dev.transmute.structure.image.TiffStructureReader
import dev.transmute.structure.image.WebpStructureReader
import dev.transmute.structure.video.AviStructureReader
import dev.transmute.structure.video.MkvStructureReader
import dev.transmute.structure.video.MovStructureReader
import dev.transmute.structure.video.Mp4StructureReader
import dev.transmute.structure.video.WebmStructureReader

/**
 * Pre-built [StructureReader][dev.transmute.model.structure.StructureReader]
 * instances for all formats shipped in this module.
 *
 * These are **not** automatically registered. Register them via the plugin
 * scope's codec registries:
 *
 * ```kotlin
 * // Inside a TransmutePlugin.install(scope, config) block:
 * scope.codecs.image.structureDecoders.register(ImageFormat.Png, DefaultStructureDecoders.png)
 *
 * // Or bulk-register all structure decoders for a domain:
 * DefaultStructureDecoders.allImageDecoders.forEach { scope.codecs.image.structureDecoders.register(it.decodableFormats.first(), it) }
 * ```
 */
object DefaultStructureReaders {

  // -- Image --
  val png = PngStructureReader()
  val jpeg = JpegStructureReader()
  val bmp = BmpStructureReader()
  val gif = GifStructureReader()
  val tiff = TiffStructureReader()
  val webp = WebpStructureReader()
  val heif = HeifStructureReader()
  val avif = AvifStructureReader()

  // -- Audio --
  val wav = WavStructureReader()
  val mp3 = Mp3StructureReader()
  val flac = FlacStructureReader()
  val aac = AacStructureReader()
  val m4a = M4aStructureReader()
  val oggAudio = OggAudioStructureReader()
  val opus = OpusStructureReader()

  // -- Video --
  val mp4 = Mp4StructureReader()
  val mov = MovStructureReader()
  val webm = WebmStructureReader()
  val mkv = MkvStructureReader()
  val avi = AviStructureReader()

  /**
   * The full list of built-in readers, in recommended priority order.
   *
   * The order is: image readers first (fast magic-byte checks),
   * then audio, then video.
   */
  val all = listOf(
    // Image
    png, jpeg, bmp, gif, tiff, webp, heif, avif,
    // Audio
    wav, mp3, flac, aac, m4a, oggAudio, opus,
    // Video
    mp4, mov, webm, mkv, avi,
  )
}
