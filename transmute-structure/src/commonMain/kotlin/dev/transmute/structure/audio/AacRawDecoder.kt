@file:Suppress("unused")

package dev.transmute.structure.audio

import dev.transmute.audio.AudioFormat
import dev.transmute.codec.Decoder
import dev.transmute.common.PipelineContext
import dev.transmute.model.core.Bytes
import dev.transmute.model.core.NoDecodeOptions
import dev.transmute.model.structure.audio.AacRaw

/** Decodes raw bytes into a [AacRaw] structure. */
class AacRawDecoder : Decoder<AudioFormat, AacRaw, NoDecodeOptions> {
    private val reader = AacStructureReader()
    override val decodableFormats: Set<AudioFormat> = setOf(AudioFormat.Aac)
    override fun sniff(data: Bytes): AudioFormat? =
        if (reader.canRead(data)) AudioFormat.Aac else null
    override suspend fun decode(source: Bytes, options: NoDecodeOptions, context: PipelineContext): AacRaw =
        reader.read(source)
}
