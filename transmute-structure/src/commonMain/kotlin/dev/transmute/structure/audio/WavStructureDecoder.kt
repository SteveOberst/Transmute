@file:Suppress("unused")

package dev.transmute.structure.audio

import dev.transmute.audio.AudioFormat
import dev.transmute.codec.Decoder
import dev.transmute.common.PipelineContext
import dev.transmute.model.core.Bytes
import dev.transmute.model.core.NoDecodeOptions
import dev.transmute.model.structure.audio.WavStructure
import dev.transmute.model.structure.audio.toStructure

/** Decodes raw bytes into a [WavStructure]. */
class WavStructureDecoder : Decoder<AudioFormat, WavStructure, NoDecodeOptions> {
    private val rawDecoder = WavRawDecoder()
    override val decodableFormats: Set<AudioFormat> = setOf(AudioFormat.Wav)
    override fun sniff(data: Bytes): AudioFormat? = rawDecoder.sniff(data)
    override suspend fun decode(source: Bytes, options: NoDecodeOptions, context: PipelineContext): WavStructure =
        rawDecoder.decode(source, options, context).toStructure()
}
