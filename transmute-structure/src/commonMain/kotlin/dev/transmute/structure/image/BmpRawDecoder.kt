@file:Suppress("unused")

package dev.transmute.structure.image

import dev.transmute.codec.Decoder
import dev.transmute.common.PipelineContext
import dev.transmute.image.ImageFormat
import dev.transmute.model.core.Bytes
import dev.transmute.model.core.NoDecodeOptions
import dev.transmute.model.structure.image.BmpRaw

/** Decodes raw bytes into a [BmpRaw] structure. */
class BmpRawDecoder : Decoder<ImageFormat, BmpRaw, NoDecodeOptions> {
    private val reader = BmpStructureReader()
    override val decodableFormats: Set<ImageFormat> = setOf(ImageFormat.Bmp)
    override fun sniff(data: Bytes): ImageFormat? =
        if (reader.canRead(data)) ImageFormat.Bmp else null
    override suspend fun decode(source: Bytes, options: NoDecodeOptions, context: PipelineContext): BmpRaw =
        reader.read(source)
}
