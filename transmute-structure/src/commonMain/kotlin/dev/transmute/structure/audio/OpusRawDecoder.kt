@file:Suppress("unused")

package dev.transmute.structure.audio

import dev.transmute.audio.AudioFormat
import dev.transmute.codec.Decoder
import dev.transmute.common.PipelineContext
import dev.transmute.model.core.Bytes
import dev.transmute.model.core.NoDecodeOptions
import dev.transmute.model.structure.audio.OpusRaw

/** Decodes raw bytes into a [OpusRaw] structure. */
class OpusRawDecoder : Decoder<AudioFormat, OpusRaw, NoDecodeOptions> {
    private val reader = OpusStructureReader()
    override val decodableFormats: Set<AudioFormat> = setOf(AudioFormat.Opus)
    override fun sniff(data: Bytes): AudioFormat? =
        if (reader.canRead(data)) AudioFormat.Opus else null
    override suspend fun decode(source: Bytes, options: NoDecodeOptions, context: PipelineContext): OpusRaw =
        reader.read(source)
}
