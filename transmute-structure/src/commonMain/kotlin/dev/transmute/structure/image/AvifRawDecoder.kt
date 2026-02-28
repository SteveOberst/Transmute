@file:Suppress("unused")

package dev.transmute.structure.image

import dev.transmute.codec.Decoder
import dev.transmute.common.PipelineContext
import dev.transmute.image.ImageFormat
import dev.transmute.model.core.Bytes
import dev.transmute.model.core.NoDecodeOptions
import dev.transmute.model.structure.image.AvifRaw

/** Decodes raw bytes into a [AvifRaw] structure. */
class AvifRawDecoder : Decoder<ImageFormat, AvifRaw, NoDecodeOptions> {
    private val reader = AvifStructureReader()
    override val decodableFormats: Set<ImageFormat> = setOf(ImageFormat.Avif)
    override fun sniff(data: Bytes): ImageFormat? =
        if (reader.canRead(data)) ImageFormat.Avif else null
    override suspend fun decode(source: Bytes, options: NoDecodeOptions, context: PipelineContext): AvifRaw =
        reader.read(source)
}
