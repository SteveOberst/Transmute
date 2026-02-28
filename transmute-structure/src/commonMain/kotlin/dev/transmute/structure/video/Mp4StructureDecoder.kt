@file:Suppress("unused")

package dev.transmute.structure.video

import dev.transmute.codec.Decoder
import dev.transmute.common.PipelineContext
import dev.transmute.model.core.Bytes
import dev.transmute.model.core.NoDecodeOptions
import dev.transmute.model.structure.video.Mp4Structure
import dev.transmute.model.structure.video.toStructure
import dev.transmute.video.VideoFormat

/** Decodes raw bytes into a [Mp4Structure]. */
class Mp4StructureDecoder : Decoder<VideoFormat, Mp4Structure, NoDecodeOptions> {
    private val rawDecoder = Mp4RawDecoder()
    override val decodableFormats: Set<VideoFormat> = setOf(VideoFormat.Mp4)
    override fun sniff(data: Bytes): VideoFormat? = rawDecoder.sniff(data)
    override suspend fun decode(source: Bytes, options: NoDecodeOptions, context: PipelineContext): Mp4Structure =
        rawDecoder.decode(source, options, context).toStructure()
}
