@file:Suppress("unused")

package dev.transmute.structure.audio

import dev.transmute.audio.AudioFormat
import dev.transmute.codec.Decoder
import dev.transmute.common.PipelineContext
import dev.transmute.model.core.Bytes
import dev.transmute.model.core.NoDecodeOptions
import dev.transmute.model.structure.audio.WavRaw

/** Decodes raw bytes into a [WavRaw] structure. */
class WavRawDecoder : Decoder<AudioFormat, WavRaw, NoDecodeOptions> {
    private val reader = WavStructureReader()
    override val decodableFormats: Set<AudioFormat> = setOf(AudioFormat.Wav)
    override fun sniff(data: Bytes): AudioFormat? =
        if (reader.canRead(data)) AudioFormat.Wav else null
    override suspend fun decode(source: Bytes, options: NoDecodeOptions, context: PipelineContext): WavRaw =
        reader.read(source)
}
