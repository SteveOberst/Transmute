@file:Suppress("unused")

package dev.transmute.structure.video

import dev.transmute.codec.Decoder
import dev.transmute.common.PipelineContext
import dev.transmute.model.core.Bytes
import dev.transmute.model.core.NoDecodeOptions
import dev.transmute.model.structure.video.AviRaw
import dev.transmute.video.VideoFormat

/** Decodes raw bytes into a [AviRaw] structure. */
class AviRawDecoder : Decoder<VideoFormat, AviRaw, NoDecodeOptions> {
    private val reader = AviStructureReader()
    override val decodableFormats: Set<VideoFormat> = setOf(VideoFormat.Avi)
    override fun sniff(data: Bytes): VideoFormat? =
        if (reader.canRead(data)) VideoFormat.Avi else null
    override suspend fun decode(source: Bytes, options: NoDecodeOptions, context: PipelineContext): AviRaw =
        reader.read(source)
}
