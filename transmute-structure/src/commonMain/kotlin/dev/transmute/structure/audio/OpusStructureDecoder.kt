@file:Suppress("unused")

package dev.transmute.structure.audio

import dev.transmute.audio.AudioFormat
import dev.transmute.codec.Decoder
import dev.transmute.common.PipelineContext
import dev.transmute.model.core.Bytes
import dev.transmute.model.core.NoDecodeOptions
import dev.transmute.model.structure.audio.OpusStructure
import dev.transmute.model.structure.audio.toStructure

/** Decodes raw bytes into a [OpusStructure]. */
class OpusStructureDecoder : Decoder<AudioFormat, OpusStructure, NoDecodeOptions> {
    private val rawDecoder = OpusRawDecoder()
    override val decodableFormats: Set<AudioFormat> = setOf(AudioFormat.Opus)
    override fun sniff(data: Bytes): AudioFormat? = rawDecoder.sniff(data)
    override suspend fun decode(source: Bytes, options: NoDecodeOptions, context: PipelineContext): OpusStructure =
        rawDecoder.decode(source, options, context).toStructure()
}
