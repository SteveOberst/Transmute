@file:Suppress("unused")

package dev.transmute.structure.image

import dev.transmute.codec.Decoder
import dev.transmute.common.PipelineContext
import dev.transmute.image.ImageFormat
import dev.transmute.model.core.Bytes
import dev.transmute.model.core.NoDecodeOptions
import dev.transmute.model.structure.image.GifRaw

/** Decodes raw bytes into a [GifRaw] structure. */
class GifRawDecoder : Decoder<ImageFormat, GifRaw, NoDecodeOptions> {
    private val reader = GifStructureReader()
    override val decodableFormats: Set<ImageFormat> = setOf(ImageFormat.Gif)
    override fun sniff(data: Bytes): ImageFormat? =
        if (reader.canRead(data)) ImageFormat.Gif else null
    override suspend fun decode(source: Bytes, options: NoDecodeOptions, context: PipelineContext): GifRaw =
        reader.read(source)
}
