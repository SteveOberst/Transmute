@file:Suppress("unused")

package dev.transmute.structure.image

import dev.transmute.codec.Decoder
import dev.transmute.common.PipelineContext
import dev.transmute.image.ImageFormat
import dev.transmute.model.core.Bytes
import dev.transmute.model.core.NoDecodeOptions
import dev.transmute.model.structure.image.TiffRaw

/** Decodes raw bytes into a [TiffRaw] structure. */
class TiffRawDecoder : Decoder<ImageFormat, TiffRaw, NoDecodeOptions> {
    private val reader = TiffStructureReader()
    override val decodableFormats: Set<ImageFormat> = setOf(ImageFormat.Tiff)
    override fun sniff(data: Bytes): ImageFormat? =
        if (reader.canRead(data)) ImageFormat.Tiff else null
    override suspend fun decode(source: Bytes, options: NoDecodeOptions, context: PipelineContext): TiffRaw =
        reader.read(source)
}
