@file:Suppress("unused")

package dev.transmute.structure.audio

import dev.transmute.audio.AudioFormat
import dev.transmute.codec.Decoder
import dev.transmute.common.PipelineContext
import dev.transmute.model.core.Bytes
import dev.transmute.model.core.NoDecodeOptions
import dev.transmute.model.structure.audio.OggAudioRaw

/** Decodes raw bytes into a [OggAudioRaw] structure. */
class OggAudioRawDecoder : Decoder<AudioFormat, OggAudioRaw, NoDecodeOptions> {
    private val reader = OggAudioStructureReader()
    override val decodableFormats: Set<AudioFormat> = setOf(AudioFormat.Ogg)
    override fun sniff(data: Bytes): AudioFormat? =
        if (reader.canRead(data)) AudioFormat.Ogg else null
    override suspend fun decode(source: Bytes, options: NoDecodeOptions, context: PipelineContext): OggAudioRaw =
        reader.read(source)
}
