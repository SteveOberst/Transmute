@file:Suppress("unused")

package dev.transmute.structure

import dev.transmute.audio.AudioFormat
import dev.transmute.codec.MediaDecoder
import dev.transmute.image.ImageFormat
import dev.transmute.model.core.MediaStructure
import dev.transmute.model.core.NoDecodeOptions
import dev.transmute.model.core.RawMediaStructure
import dev.transmute.model.structure.audio.toStructure as toAudioStructure
import dev.transmute.model.structure.image.toStructure as toImageStructure
import dev.transmute.model.structure.video.toStructure as toVideoStructure
import dev.transmute.video.VideoFormat

/**
 * Pre-built raw and structure decoder instances for all formats shipped by this module.
 *
 * Decoders are created via [rawDecoderFor] and [structureDecoderFor] - no named subclasses needed.
 *
 * Register individual decoders:
 * ```kotlin
 * scope.codecs.image.structureDecoders.register(ImageFormat.Png, DefaultStructureDecoders.png)
 * scope.codecs.audio.rawStructureDecoders.register(AudioFormat.Flac, DefaultStructureDecoders.flacRaw)
 * ```
 *
 * Or bulk-register via the domain lists:
 * ```kotlin
 * DefaultStructureDecoders.allImageDecoders.forEach { dec ->
 *     dec.decodableFormats.forEach { fmt ->
 *         scope.codecs.image.structureDecoders.register(fmt as ImageFormat, dec)
 *     }
 * }
 * ```
 */
object DefaultStructureDecoders {

    // -- Image raw decoders (bytes -> *Raw) ------------------------------------

    val pngRaw  = rawDecoderFor(ImageFormat.Png,  DefaultStructureReaders.png)
    val jpegRaw = rawDecoderFor(ImageFormat.Jpeg, DefaultStructureReaders.jpeg)
    val bmpRaw  = rawDecoderFor(ImageFormat.Bmp,  DefaultStructureReaders.bmp)
    val gifRaw  = rawDecoderFor(ImageFormat.Gif,  DefaultStructureReaders.gif)
    val tiffRaw = rawDecoderFor(ImageFormat.Tiff, DefaultStructureReaders.tiff)
    val webpRaw = rawDecoderFor(ImageFormat.Webp, DefaultStructureReaders.webp)
    val heifRaw = rawDecoderFor(ImageFormat.Heif, DefaultStructureReaders.heif)
    /** HEIC uses the same ISO BMFF container as HEIF; the [HeifStructureReader][dev.transmute.structure.image.HeifStructureReader] handles both. */
    val heicRaw = rawDecoderFor(ImageFormat.Heic, DefaultStructureReaders.heif)
    val avifRaw = rawDecoderFor(ImageFormat.Avif, DefaultStructureReaders.avif)

    // -- Image structure decoders (bytes -> *Structure) ------------------------

    val png  = structureDecoderFor(ImageFormat.Png,  DefaultStructureReaders.png)  { toImageStructure() }
    val jpeg = structureDecoderFor(ImageFormat.Jpeg, DefaultStructureReaders.jpeg) { toImageStructure() }
    val bmp  = structureDecoderFor(ImageFormat.Bmp,  DefaultStructureReaders.bmp)  { toImageStructure() }
    val gif  = structureDecoderFor(ImageFormat.Gif,  DefaultStructureReaders.gif)  { toImageStructure() }
    val tiff = structureDecoderFor(ImageFormat.Tiff, DefaultStructureReaders.tiff) { toImageStructure() }
    val webp = structureDecoderFor(ImageFormat.Webp, DefaultStructureReaders.webp) { toImageStructure() }
    val heif = structureDecoderFor(ImageFormat.Heif, DefaultStructureReaders.heif) { toImageStructure() }
    /** HEIC uses the same ISO BMFF container as HEIF; the [HeifStructureReader][dev.transmute.structure.image.HeifStructureReader] handles both. */
    val heic = structureDecoderFor(ImageFormat.Heic, DefaultStructureReaders.heif) { toImageStructure() }
    val avif = structureDecoderFor(ImageFormat.Avif, DefaultStructureReaders.avif) { toImageStructure() }

    // -- Audio raw decoders (bytes -> *Raw) ------------------------------------

    val wavRaw      = rawDecoderFor(AudioFormat.Wav,  DefaultStructureReaders.wav)
    val mp3Raw      = rawDecoderFor(AudioFormat.Mp3,  DefaultStructureReaders.mp3)
    val flacRaw     = rawDecoderFor(AudioFormat.Flac, DefaultStructureReaders.flac)
    val aacRaw      = rawDecoderFor(AudioFormat.Aac,  DefaultStructureReaders.aac)
    val m4aRaw      = rawDecoderFor(AudioFormat.M4a,  DefaultStructureReaders.m4a)
    val oggAudioRaw = rawDecoderFor(AudioFormat.Ogg,  DefaultStructureReaders.oggAudio)
    val opusRaw     = rawDecoderFor(AudioFormat.Opus, DefaultStructureReaders.opus)

    // -- Audio structure decoders (bytes -> *Structure) ------------------------

    val wav      = structureDecoderFor(AudioFormat.Wav,  DefaultStructureReaders.wav)      { toAudioStructure() }
    val mp3      = structureDecoderFor(AudioFormat.Mp3,  DefaultStructureReaders.mp3)      { toAudioStructure() }
    val flac     = structureDecoderFor(AudioFormat.Flac, DefaultStructureReaders.flac)     { toAudioStructure() }
    val aac      = structureDecoderFor(AudioFormat.Aac,  DefaultStructureReaders.aac)      { toAudioStructure() }
    val m4a      = structureDecoderFor(AudioFormat.M4a,  DefaultStructureReaders.m4a)      { toAudioStructure() }
    val oggAudio = structureDecoderFor(AudioFormat.Ogg,  DefaultStructureReaders.oggAudio) { toAudioStructure() }
    val opus     = structureDecoderFor(AudioFormat.Opus, DefaultStructureReaders.opus)     { toAudioStructure() }

    // -- Video raw decoders (bytes -> *Raw) ------------------------------------

    val mp4Raw  = rawDecoderFor(VideoFormat.Mp4,  DefaultStructureReaders.mp4)
    val movRaw  = rawDecoderFor(VideoFormat.Mov,  DefaultStructureReaders.mov)
    val webmRaw = rawDecoderFor(VideoFormat.Webm, DefaultStructureReaders.webm)
    val mkvRaw  = rawDecoderFor(VideoFormat.Mkv,  DefaultStructureReaders.mkv)
    val aviRaw  = rawDecoderFor(VideoFormat.Avi,  DefaultStructureReaders.avi)

    // -- Video structure decoders (bytes -> *Structure) ------------------------

    val mp4  = structureDecoderFor(VideoFormat.Mp4,  DefaultStructureReaders.mp4)  { toVideoStructure() }
    val mov  = structureDecoderFor(VideoFormat.Mov,  DefaultStructureReaders.mov)  { toVideoStructure() }
    val webm = structureDecoderFor(VideoFormat.Webm, DefaultStructureReaders.webm) { toVideoStructure() }
    val mkv  = structureDecoderFor(VideoFormat.Mkv,  DefaultStructureReaders.mkv)  { toVideoStructure() }
    val avi  = structureDecoderFor(VideoFormat.Avi,  DefaultStructureReaders.avi)  { toVideoStructure() }

    // -- Domain lists ---------------------------------------------------------

    /** All image structure decoders, in recommended priority order. */
    val allImageDecoders: List<MediaDecoder<ImageFormat, MediaStructure, NoDecodeOptions>> =
        listOf(png, jpeg, bmp, gif, tiff, webp, heif, heic, avif)

    /** All audio structure decoders, in recommended priority order. */
    val allAudioDecoders: List<MediaDecoder<AudioFormat, MediaStructure, NoDecodeOptions>> =
        listOf(wav, mp3, flac, aac, m4a, oggAudio, opus)

    /** All video structure decoders, in recommended priority order. */
    val allVideoDecoders: List<MediaDecoder<VideoFormat, MediaStructure, NoDecodeOptions>> =
        listOf(mp4, mov, webm, mkv, avi)

    /** All image raw decoders, in recommended priority order. */
    val allImageRawDecoders: List<MediaDecoder<ImageFormat, RawMediaStructure, NoDecodeOptions>> =
        listOf(pngRaw, jpegRaw, bmpRaw, gifRaw, tiffRaw, webpRaw, heifRaw, heicRaw, avifRaw)

    /** All audio raw decoders, in recommended priority order. */
    val allAudioRawDecoders: List<MediaDecoder<AudioFormat, RawMediaStructure, NoDecodeOptions>> =
        listOf(wavRaw, mp3Raw, flacRaw, aacRaw, m4aRaw, oggAudioRaw, opusRaw)

    /** All video raw decoders, in recommended priority order. */
    val allVideoRawDecoders: List<MediaDecoder<VideoFormat, RawMediaStructure, NoDecodeOptions>> =
        listOf(mp4Raw, movRaw, webmRaw, mkvRaw, aviRaw)
}
