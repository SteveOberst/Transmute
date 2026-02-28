@file:Suppress("unused")

package dev.transmute.structure.image

import dev.transmute.codec.Decoder
import dev.transmute.common.PipelineContext
import dev.transmute.image.ImageFormat
import dev.transmute.model.core.Bytes
import dev.transmute.model.core.NoDecodeOptions
import dev.transmute.model.structure.image.PngRaw

/** Decodes raw bytes into a [PngRaw] structure. */
class PngRawDecoder : Decoder<ImageFormat, PngRaw, NoDecodeOptions> {
    private val reader = PngStructureReader()
    override val decodableFormats: Set<ImageFormat> = setOf(ImageFormat.Png)
    override fun sniff(data: Bytes): ImageFormat? =
        if (reader.canRead(data)) ImageFormat.Png else null
    override suspend fun decode(source: Bytes, options: NoDecodeOptions, context: PipelineContext): PngRaw =
        reader.read(source)
}
