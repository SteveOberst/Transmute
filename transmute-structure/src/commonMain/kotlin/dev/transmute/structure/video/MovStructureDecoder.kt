@file:Suppress("unused")

package dev.transmute.structure.video

import dev.transmute.codec.Decoder
import dev.transmute.common.PipelineContext
import dev.transmute.model.core.Bytes
import dev.transmute.model.core.NoDecodeOptions
import dev.transmute.model.structure.video.MovStructure
import dev.transmute.model.structure.video.toStructure
import dev.transmute.video.VideoFormat

/** Decodes raw bytes into a [MovStructure]. */
class MovStructureDecoder : Decoder<VideoFormat, MovStructure, NoDecodeOptions> {
    private val rawDecoder = MovRawDecoder()
    override val decodableFormats: Set<VideoFormat> = setOf(VideoFormat.Mov)
    override fun sniff(data: Bytes): VideoFormat? = rawDecoder.sniff(data)
    override suspend fun decode(source: Bytes, options: NoDecodeOptions, context: PipelineContext): MovStructure =
        rawDecoder.decode(source, options, context).toStructure()
}
