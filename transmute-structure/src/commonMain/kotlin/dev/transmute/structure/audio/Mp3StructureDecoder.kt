@file:Suppress("unused")

package dev.transmute.structure.audio

import dev.transmute.audio.AudioFormat
import dev.transmute.codec.Decoder
import dev.transmute.common.PipelineContext
import dev.transmute.model.core.Bytes
import dev.transmute.model.core.NoDecodeOptions
import dev.transmute.model.structure.audio.Mp3Structure
import dev.transmute.model.structure.audio.toStructure

/** Decodes raw bytes into a [Mp3Structure]. */
class Mp3StructureDecoder : Decoder<AudioFormat, Mp3Structure, NoDecodeOptions> {
    private val rawDecoder = Mp3RawDecoder()
    override val decodableFormats: Set<AudioFormat> = setOf(AudioFormat.Mp3)
    override fun sniff(data: Bytes): AudioFormat? = rawDecoder.sniff(data)
    override suspend fun decode(source: Bytes, options: NoDecodeOptions, context: PipelineContext): Mp3Structure =
        rawDecoder.decode(source, options, context).toStructure()
}
