@file:Suppress("unused")

package dev.transmute.structure.image

import dev.transmute.codec.Decoder
import dev.transmute.common.PipelineContext
import dev.transmute.image.ImageFormat
import dev.transmute.model.core.Bytes
import dev.transmute.model.core.NoDecodeOptions
import dev.transmute.model.structure.image.TiffStructure
import dev.transmute.model.structure.image.toStructure

/** Decodes raw bytes into a [TiffStructure]. */
class TiffStructureDecoder : Decoder<ImageFormat, TiffStructure, NoDecodeOptions> {
    private val rawDecoder = TiffRawDecoder()
    override val decodableFormats: Set<ImageFormat> = setOf(ImageFormat.Tiff)
    override fun sniff(data: Bytes): ImageFormat? = rawDecoder.sniff(data)
    override suspend fun decode(source: Bytes, options: NoDecodeOptions, context: PipelineContext): TiffStructure =
        rawDecoder.decode(source, options, context).toStructure()
}
