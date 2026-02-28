@file:Suppress("unused")

package dev.transmute.structure.video

import dev.transmute.codec.Decoder
import dev.transmute.common.PipelineContext
import dev.transmute.model.core.Bytes
import dev.transmute.model.core.NoDecodeOptions
import dev.transmute.model.structure.video.WebmStructure
import dev.transmute.model.structure.video.toStructure
import dev.transmute.video.VideoFormat

/** Decodes raw bytes into a [WebmStructure]. */
class WebmStructureDecoder : Decoder<VideoFormat, WebmStructure, NoDecodeOptions> {
    private val rawDecoder = WebmRawDecoder()
    override val decodableFormats: Set<VideoFormat> = setOf(VideoFormat.Webm)
    override fun sniff(data: Bytes): VideoFormat? = rawDecoder.sniff(data)
    override suspend fun decode(source: Bytes, options: NoDecodeOptions, context: PipelineContext): WebmStructure =
        rawDecoder.decode(source, options, context).toStructure()
}
