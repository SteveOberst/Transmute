@file:Suppress("unused")

package dev.transmute.structure.audio

import dev.transmute.audio.AudioFormat
import dev.transmute.codec.Decoder
import dev.transmute.common.PipelineContext
import dev.transmute.model.core.Bytes
import dev.transmute.model.core.NoDecodeOptions
import dev.transmute.model.structure.audio.M4aStructure
import dev.transmute.model.structure.audio.toStructure

/** Decodes raw bytes into a [M4aStructure]. */
class M4aStructureDecoder : Decoder<AudioFormat, M4aStructure, NoDecodeOptions> {
    private val rawDecoder = M4aRawDecoder()
    override val decodableFormats: Set<AudioFormat> = setOf(AudioFormat.M4a)
    override fun sniff(data: Bytes): AudioFormat? = rawDecoder.sniff(data)
    override suspend fun decode(source: Bytes, options: NoDecodeOptions, context: PipelineContext): M4aStructure =
        rawDecoder.decode(source, options, context).toStructure()
}
