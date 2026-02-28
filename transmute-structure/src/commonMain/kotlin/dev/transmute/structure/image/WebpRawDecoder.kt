@file:Suppress("unused")

package dev.transmute.structure.image

import dev.transmute.codec.Decoder
import dev.transmute.common.PipelineContext
import dev.transmute.image.ImageFormat
import dev.transmute.model.core.Bytes
import dev.transmute.model.core.NoDecodeOptions
import dev.transmute.model.structure.image.WebpRaw

/** Decodes raw bytes into a [WebpRaw] structure. */
class WebpRawDecoder : Decoder<ImageFormat, WebpRaw, NoDecodeOptions> {
    private val reader = WebpStructureReader()
    override val decodableFormats: Set<ImageFormat> = setOf(ImageFormat.Webp)
    override fun sniff(data: Bytes): ImageFormat? =
        if (reader.canRead(data)) ImageFormat.Webp else null
    override suspend fun decode(source: Bytes, options: NoDecodeOptions, context: PipelineContext): WebpRaw =
        reader.read(source)
}
