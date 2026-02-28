@file:Suppress("unused")

package dev.transmute.structure.video

import dev.transmute.codec.Decoder
import dev.transmute.common.PipelineContext
import dev.transmute.model.core.Bytes
import dev.transmute.model.core.NoDecodeOptions
import dev.transmute.model.structure.video.MovRaw
import dev.transmute.video.VideoFormat

/** Decodes raw bytes into a [MovRaw] structure. */
class MovRawDecoder : Decoder<VideoFormat, MovRaw, NoDecodeOptions> {
    private val reader = MovStructureReader()
    override val decodableFormats: Set<VideoFormat> = setOf(VideoFormat.Mov)
    override fun sniff(data: Bytes): VideoFormat? =
        if (reader.canRead(data)) VideoFormat.Mov else null
    override suspend fun decode(source: Bytes, options: NoDecodeOptions, context: PipelineContext): MovRaw =
        reader.read(source)
}
