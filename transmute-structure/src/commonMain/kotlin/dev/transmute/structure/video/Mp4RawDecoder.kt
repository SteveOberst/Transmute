@file:Suppress("unused")

package dev.transmute.structure.video

import dev.transmute.codec.Decoder
import dev.transmute.common.PipelineContext
import dev.transmute.model.core.Bytes
import dev.transmute.model.core.NoDecodeOptions
import dev.transmute.model.structure.video.Mp4Raw
import dev.transmute.video.VideoFormat

/** Decodes raw bytes into a [Mp4Raw] structure. */
class Mp4RawDecoder : Decoder<VideoFormat, Mp4Raw, NoDecodeOptions> {
    private val reader = Mp4StructureReader()
    override val decodableFormats: Set<VideoFormat> = setOf(VideoFormat.Mp4)
    override fun sniff(data: Bytes): VideoFormat? =
        if (reader.canRead(data)) VideoFormat.Mp4 else null
    override suspend fun decode(source: Bytes, options: NoDecodeOptions, context: PipelineContext): Mp4Raw =
        reader.read(source)
}
