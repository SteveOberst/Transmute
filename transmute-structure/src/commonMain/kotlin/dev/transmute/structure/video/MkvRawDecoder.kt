@file:Suppress("unused")

package dev.transmute.structure.video

import dev.transmute.codec.Decoder
import dev.transmute.common.PipelineContext
import dev.transmute.model.core.Bytes
import dev.transmute.model.core.NoDecodeOptions
import dev.transmute.model.structure.video.MkvRaw
import dev.transmute.video.VideoFormat

/** Decodes raw bytes into a [MkvRaw] structure. */
class MkvRawDecoder : Decoder<VideoFormat, MkvRaw, NoDecodeOptions> {
    private val reader = MkvStructureReader()
    override val decodableFormats: Set<VideoFormat> = setOf(VideoFormat.Mkv)
    override fun sniff(data: Bytes): VideoFormat? =
        if (reader.canRead(data)) VideoFormat.Mkv else null
    override suspend fun decode(source: Bytes, options: NoDecodeOptions, context: PipelineContext): MkvRaw =
        reader.read(source)
}
