@file:Suppress("unused")

package dev.transmute.structure.video

import dev.transmute.codec.Decoder
import dev.transmute.common.PipelineContext
import dev.transmute.model.core.Bytes
import dev.transmute.model.core.NoDecodeOptions
import dev.transmute.model.structure.video.WebmRaw
import dev.transmute.video.VideoFormat

/** Decodes raw bytes into a [WebmRaw] structure. */
class WebmRawDecoder : Decoder<VideoFormat, WebmRaw, NoDecodeOptions> {
    private val reader = WebmStructureReader()
    override val decodableFormats: Set<VideoFormat> = setOf(VideoFormat.Webm)
    override fun sniff(data: Bytes): VideoFormat? =
        if (reader.canRead(data)) VideoFormat.Webm else null
    override suspend fun decode(source: Bytes, options: NoDecodeOptions, context: PipelineContext): WebmRaw =
        reader.read(source)
}
