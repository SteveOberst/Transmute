package dev.transmute.audio

import dev.transmute.audio.codecs.jvm.JvmFlacCodec
import dev.transmute.audio.codecs.jvm.JvmMp3Codec
import dev.transmute.audio.codecs.jvm.JvmOggVorbisCodec

actual fun installPlatformAudioCodecs(
  decoders: MutableAudioDecoderRegistry,
  encoders: MutableAudioEncoderRegistry,
) {
  // Full codec – decode via JLayer, encode via Jump3r (LAME).
  val mp3Codec = JvmMp3Codec()
  decoders.register(mp3Codec)
  encoders.register(mp3Codec)

  // Decode-only codecs – encoding requires the transmute-gstreamer module.
  val flacCodec = JvmFlacCodec()
  decoders.register(flacCodec)

  val oggCodec = JvmOggVorbisCodec()
  decoders.register(oggCodec)

  // Note: AAC, M4A, and Opus codecs are provided by the optional
  // transmute-gstreamer module. Add it as a dependency and configure
  // via TransmuteContext { gstreamer() } to enable them.
}
