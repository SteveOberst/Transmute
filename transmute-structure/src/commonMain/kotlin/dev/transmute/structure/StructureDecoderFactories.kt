package dev.transmute.structure

import dev.transmute.codec.Decoder
import dev.transmute.common.PipelineContext
import dev.transmute.model.core.Bytes
import dev.transmute.model.core.MediaFormat
import dev.transmute.model.core.MediaStructure
import dev.transmute.model.core.NoDecodeOptions
import dev.transmute.model.core.RawMediaStructure
import dev.transmute.model.structure.StructureReader

/**
 * Creates a [Decoder] that delegates to [reader] and returns a [RawMediaStructure].
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
): Decoder<F, R, NoDecodeOptions> = object : Decoder<F, R, NoDecodeOptions> {
    override val decodableFormats: Set<F> = setOf(format)
    override fun sniff(data: Bytes): F? = if (reader.canRead(data)) format else null
    override suspend fun decode(source: Bytes, options: NoDecodeOptions, context: PipelineContext): R =
        reader.read(source)
}

/**
 * Creates a [Decoder] that delegates to [reader] and applies [toStructure] to produce a [MediaStructure].
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
): Decoder<F, S, NoDecodeOptions> = object : Decoder<F, S, NoDecodeOptions> {
    override val decodableFormats: Set<F> = setOf(format)
    override fun sniff(data: Bytes): F? = if (reader.canRead(data)) format else null
    override suspend fun decode(source: Bytes, options: NoDecodeOptions, context: PipelineContext): S =
        reader.read(source).toStructure()
}
