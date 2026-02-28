@file:Suppress("unused")

package dev.transmute.structure.audio

import dev.transmute.audio.AudioFormat
import dev.transmute.codec.Decoder
import dev.transmute.common.PipelineContext
import dev.transmute.model.core.Bytes
import dev.transmute.model.core.NoDecodeOptions
import dev.transmute.model.structure.audio.FlacStructure
import dev.transmute.model.structure.audio.toStructure

/** Decodes raw bytes into a [FlacStructure]. */
class FlacStructureDecoder : Decoder<AudioFormat, FlacStructure, NoDecodeOptions> {
    private val rawDecoder = FlacRawDecoder()
    override val decodableFormats: Set<AudioFormat> = setOf(AudioFormat.Flac)
    override fun sniff(data: Bytes): AudioFormat? = rawDecoder.sniff(data)
    override suspend fun decode(source: Bytes, options: NoDecodeOptions, context: PipelineContext): FlacStructure =
        rawDecoder.decode(source, options, context).toStructure()
}
