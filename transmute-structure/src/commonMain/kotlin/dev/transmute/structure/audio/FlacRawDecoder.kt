@file:Suppress("unused")

package dev.transmute.structure.audio

import dev.transmute.audio.AudioFormat
import dev.transmute.codec.Decoder
import dev.transmute.common.PipelineContext
import dev.transmute.model.core.Bytes
import dev.transmute.model.core.NoDecodeOptions
import dev.transmute.model.structure.audio.FlacRaw

/** Decodes raw bytes into a [FlacRaw] structure. */
class FlacRawDecoder : Decoder<AudioFormat, FlacRaw, NoDecodeOptions> {
    private val reader = FlacStructureReader()
    override val decodableFormats: Set<AudioFormat> = setOf(AudioFormat.Flac)
    override fun sniff(data: Bytes): AudioFormat? =
        if (reader.canRead(data)) AudioFormat.Flac else null
    override suspend fun decode(source: Bytes, options: NoDecodeOptions, context: PipelineContext): FlacRaw =
        reader.read(source)
}
