package dev.transmute.audio

import dev.transmute.audio.codecs.jvm.JvmFlacCodec
import dev.transmute.audio.codecs.jvm.JvmMp3Codec
import dev.transmute.audio.codecs.jvm.JvmOggVorbisCodec

actual fun installPlatformAudioCodecs(
  decoders: MutableAudioDecoderRegistry,
  encoders: MutableAudioEncoderRegistry,
) {
  // Full codec - decode via JLayer, encode via Jump3r (LAME).
  val mp3Codec = JvmMp3Codec()
  decoders.register(mp3Codec)
  encoders.register(mp3Codec)

  // Decode-only codecs - encoding requires the transmute-gstreamer plugin.
  val flacCodec = JvmFlacCodec()
  decoders.register(flacCodec)

  val oggCodec = JvmOggVorbisCodec()
  decoders.register(oggCodec)
}
