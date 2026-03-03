package dev.transmute.structure

import dev.transmute.io.TSource
import dev.transmute.codec.MediaDecoder
import dev.transmute.common.PipelineContext
import dev.transmute.model.core.Bytes
import dev.transmute.model.core.MediaFormat
import dev.transmute.model.core.MediaMetadata
import dev.transmute.model.core.MediaStructure
import dev.transmute.model.core.NoDecodeOptions
import dev.transmute.model.core.RawMediaStructure
import dev.transmute.model.structure.StructureReader

/**
 * Creates a [MediaDecoder] that delegates to [reader] and returns a [RawMediaStructure].
 *
 * Use this instead of creating a named `*RawDecoder` subclass.
 *
 * ```kotlin
 * val pngRawDecoder = rawDecoderFor(ImageFormat.Png, PngStructureReader())
 * scope.codecs.image.rawStructureDecoders.register(ImageFormat.Png, pngRawDecoder)
 * ```
 */
fun <F : MediaFormat<*, *>, R : RawMediaStructure> rawDecoderFor(
    format: F,
    reader: StructureReader<R>,
): MediaDecoder<F, R, NoDecodeOptions> = object : MediaDecoder<F, R, NoDecodeOptions> {
    override val decodableFormats: Set<F> = setOf(format)
    override suspend fun decode(source: TSource, options: NoDecodeOptions, context: PipelineContext): R {
        val bytes = if (source is Bytes) source else Bytes(source.readAll())
        return reader.read(bytes)
    }
}

/**
 * Creates a [MediaDecoder] that delegates to [reader] and applies [toStructure] to produce a [MediaStructure].
 *
 * Use this instead of creating a named `*StructureDecoder` subclass.
 *
 * ```kotlin
 * val pngDecoder = structureDecoderFor(ImageFormat.Png, PngStructureReader()) { toStructure() }
 * scope.codecs.image.structureDecoders.register(ImageFormat.Png, pngDecoder)
 * ```
 */
fun <F : MediaFormat<*, *>, R : RawMediaStructure, S : MediaStructure> structureDecoderFor(
    format: F,
    reader: StructureReader<R>,
    toStructure: R.() -> S,
): MediaDecoder<F, S, NoDecodeOptions> = object : MediaDecoder<F, S, NoDecodeOptions> {
    override val decodableFormats: Set<F> = setOf(format)
    override suspend fun decode(source: TSource, options: NoDecodeOptions, context: PipelineContext): S {
        val bytes = if (source is Bytes) source else Bytes(source.readAll())
        return reader.read(bytes).toStructure()
    }
}

/**
 * Creates a [MediaDecoder] that delegates to [reader] and applies [extract] to
 * produce a list of [MediaMetadata] blocks found in the file.
 *
 * This reuses the same [StructureReader] that powers raw/structure decoding,
 * so the file is only parsed once when the caller already has the raw model.
 *
 * ```kotlin
 * val jpegMeta = metadataDecoderFor(ImageFormat.Jpeg, JpegStructureReader()) { extractMetadata() }
 * scope.codecs.image.metadataDecoders.register(ImageFormat.Jpeg, jpegMeta)
 * ```
 */
fun <F : MediaFormat<*, *>, R : RawMediaStructure> metadataDecoderFor(
    format: F,
    reader: StructureReader<R>,
    extract: R.() -> List<MediaMetadata>,
): MediaDecoder<F, List<MediaMetadata>, NoDecodeOptions> = object : MediaDecoder<F, List<MediaMetadata>, NoDecodeOptions> {
    override val decodableFormats: Set<F> = setOf(format)
    override suspend fun decode(source: TSource, options: NoDecodeOptions, context: PipelineContext): List<MediaMetadata> {
        val bytes = if (source is Bytes) source else Bytes(source.readAll())
        return reader.read(bytes).extract()
    }
}
