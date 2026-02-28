@file:Suppress("unused")

package dev.transmute.structure.video

import dev.transmute.codec.Decoder
import dev.transmute.common.PipelineContext
import dev.transmute.model.core.Bytes
import dev.transmute.model.core.NoDecodeOptions
import dev.transmute.model.structure.video.MkvStructure
import dev.transmute.model.structure.video.toStructure
import dev.transmute.video.VideoFormat

/** Decodes raw bytes into a [MkvStructure]. */
class MkvStructureDecoder : Decoder<VideoFormat, MkvStructure, NoDecodeOptions> {
    private val rawDecoder = MkvRawDecoder()
    override val decodableFormats: Set<VideoFormat> = setOf(VideoFormat.Mkv)
    override fun sniff(data: Bytes): VideoFormat? = rawDecoder.sniff(data)
    override suspend fun decode(source: Bytes, options: NoDecodeOptions, context: PipelineContext): MkvStructure =
        rawDecoder.decode(source, options, context).toStructure()
}
