@file:Suppress("unused")

package dev.transmute.structure.image

import dev.transmute.codec.Decoder
import dev.transmute.common.PipelineContext
import dev.transmute.image.ImageFormat
import dev.transmute.model.core.Bytes
import dev.transmute.model.core.NoDecodeOptions
import dev.transmute.model.structure.image.HeifRaw

/** Decodes raw bytes into a [HeifRaw] structure. */
class HeifRawDecoder : Decoder<ImageFormat, HeifRaw, NoDecodeOptions> {
    private val reader = HeifStructureReader()
    override val decodableFormats: Set<ImageFormat> = setOf(ImageFormat.Heif)
    override fun sniff(data: Bytes): ImageFormat? =
        if (reader.canRead(data)) ImageFormat.Heif else null
    override suspend fun decode(source: Bytes, options: NoDecodeOptions, context: PipelineContext): HeifRaw =
        reader.read(source)
}
