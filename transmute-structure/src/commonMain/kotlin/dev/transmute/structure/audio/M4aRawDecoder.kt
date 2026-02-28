@file:Suppress("unused")

package dev.transmute.structure.audio

import dev.transmute.audio.AudioFormat
import dev.transmute.codec.Decoder
import dev.transmute.common.PipelineContext
import dev.transmute.model.core.Bytes
import dev.transmute.model.core.NoDecodeOptions
import dev.transmute.model.structure.audio.M4aRaw

/** Decodes raw bytes into a [M4aRaw] structure. */
class M4aRawDecoder : Decoder<AudioFormat, M4aRaw, NoDecodeOptions> {
    private val reader = M4aStructureReader()
    override val decodableFormats: Set<AudioFormat> = setOf(AudioFormat.M4a)
    override fun sniff(data: Bytes): AudioFormat? =
        if (reader.canRead(data)) AudioFormat.M4a else null
    override suspend fun decode(source: Bytes, options: NoDecodeOptions, context: PipelineContext): M4aRaw =
        reader.read(source)
}
