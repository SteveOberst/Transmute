@file:Suppress("unused")

package dev.transmute.structure

import dev.transmute.audio.AudioFormat
import dev.transmute.codec.MediaDecoder
import dev.transmute.image.ImageFormat
import dev.transmute.model.core.MediaMetadata
import dev.transmute.model.core.NoDecodeOptions
import dev.transmute.structure.audio.extractMetadata
import dev.transmute.structure.image.extractMetadata
import dev.transmute.structure.video.extractMetadata
import dev.transmute.video.VideoFormat

/**
 * Pre-built metadata decoder instances for formats that contain
 * extractable metadata blocks (EXIF, XMP, ICC, ID3, Vorbis Comment, etc.).
 *
 * Metadata decoders reuse the same [StructureReader] as structure decoders
 * (no duplicate parsing) and return `List<MediaMetadata>` - one entry per
 * metadata block found in the file.
 *
 * Register individual decoders:
 * ```kotlin
 * scope.codecs.image.metadataDecoders.register(ImageFormat.Jpeg, DefaultMetadataDecoders.jpeg)
 * ```
 */
object DefaultMetadataDecoders {

  // -- Image metadata decoders ----------------------------------------------

  val jpeg: MediaDecoder<ImageFormat, List<MediaMetadata>, NoDecodeOptions> =
    metadataDecoderFor(ImageFormat.Jpeg, DefaultStructureReaders.jpeg) { extractMetadata() }

  val tiff: MediaDecoder<ImageFormat, List<MediaMetadata>, NoDecodeOptions> =
    metadataDecoderFor(ImageFormat.Tiff, DefaultStructureReaders.tiff) { extractMetadata() }

  val png: MediaDecoder<ImageFormat, List<MediaMetadata>, NoDecodeOptions> =
    metadataDecoderFor(ImageFormat.Png, DefaultStructureReaders.png) { extractMetadata() }

  val webp: MediaDecoder<ImageFormat, List<MediaMetadata>, NoDecodeOptions> =
    metadataDecoderFor(ImageFormat.Webp, DefaultStructureReaders.webp) { extractMetadata() }

  val heif: MediaDecoder<ImageFormat, List<MediaMetadata>, NoDecodeOptions> =
    metadataDecoderFor(ImageFormat.Heif, DefaultStructureReaders.heif) { extractMetadata() }

  /** HEIC uses the same ISO BMFF container as HEIF; the same reader extracts EXIF/XMP metadata from both. */
  val heic: MediaDecoder<ImageFormat, List<MediaMetadata>, NoDecodeOptions> =
    metadataDecoderFor(ImageFormat.Heic, DefaultStructureReaders.heif) { extractMetadata() }

  val avif: MediaDecoder<ImageFormat, List<MediaMetadata>, NoDecodeOptions> =
    metadataDecoderFor(ImageFormat.Avif, DefaultStructureReaders.avif) { extractMetadata() }

  // -- Audio metadata decoders ----------------------------------------------

  val mp3: MediaDecoder<AudioFormat, List<MediaMetadata>, NoDecodeOptions> =
    metadataDecoderFor(AudioFormat.Mp3, DefaultStructureReaders.mp3) { extractMetadata() }

  val flac: MediaDecoder<AudioFormat, List<MediaMetadata>, NoDecodeOptions> =
    metadataDecoderFor(AudioFormat.Flac, DefaultStructureReaders.flac) { extractMetadata() }

  val oggAudio: MediaDecoder<AudioFormat, List<MediaMetadata>, NoDecodeOptions> =
    metadataDecoderFor(AudioFormat.Ogg, DefaultStructureReaders.oggAudio) { extractMetadata() }

  val opus: MediaDecoder<AudioFormat, List<MediaMetadata>, NoDecodeOptions> =
    metadataDecoderFor(AudioFormat.Opus, DefaultStructureReaders.opus) { extractMetadata() }

  val wav: MediaDecoder<AudioFormat, List<MediaMetadata>, NoDecodeOptions> =
    metadataDecoderFor(AudioFormat.Wav, DefaultStructureReaders.wav) { extractMetadata() }

  val m4a: MediaDecoder<AudioFormat, List<MediaMetadata>, NoDecodeOptions> =
    metadataDecoderFor(AudioFormat.M4a, DefaultStructureReaders.m4a) { extractMetadata() }

  val aac: MediaDecoder<AudioFormat, List<MediaMetadata>, NoDecodeOptions> =
    metadataDecoderFor(AudioFormat.Aac, DefaultStructureReaders.aac) { extractMetadata() }

  // -- Video metadata decoders ----------------------------------------------

  val mp4: MediaDecoder<VideoFormat, List<MediaMetadata>, NoDecodeOptions> =
    metadataDecoderFor(VideoFormat.Mp4, DefaultStructureReaders.mp4) { extractMetadata() }

  val mov: MediaDecoder<VideoFormat, List<MediaMetadata>, NoDecodeOptions> =
    metadataDecoderFor(VideoFormat.Mov, DefaultStructureReaders.mov) { extractMetadata() }

  val avi: MediaDecoder<VideoFormat, List<MediaMetadata>, NoDecodeOptions> =
    metadataDecoderFor(VideoFormat.Avi, DefaultStructureReaders.avi) { extractMetadata() }

  val webm: MediaDecoder<VideoFormat, List<MediaMetadata>, NoDecodeOptions> =
    metadataDecoderFor(VideoFormat.Webm, DefaultStructureReaders.webm) { extractMetadata() }

  val mkv: MediaDecoder<VideoFormat, List<MediaMetadata>, NoDecodeOptions> =
    metadataDecoderFor(VideoFormat.Mkv, DefaultStructureReaders.mkv) { extractMetadata() }

  // -- Domain lists ---------------------------------------------------------

  val allImageDecoders: List<MediaDecoder<ImageFormat, List<MediaMetadata>, NoDecodeOptions>> =
    listOf(jpeg, tiff, png, webp, heif, heic, avif)

  val allAudioDecoders: List<MediaDecoder<AudioFormat, List<MediaMetadata>, NoDecodeOptions>> =
    listOf(mp3, flac, oggAudio, opus, wav, m4a, aac)

  val allVideoDecoders: List<MediaDecoder<VideoFormat, List<MediaMetadata>, NoDecodeOptions>> =
    listOf(mp4, mov, avi, webm, mkv)
}
