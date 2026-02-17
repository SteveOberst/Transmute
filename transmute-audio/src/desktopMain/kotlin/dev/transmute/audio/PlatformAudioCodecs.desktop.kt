package dev.transmute.audio

import dev.transmute.audio.codecs.jvm.FfmpegAudioEngine
import dev.transmute.audio.codecs.jvm.JvmAacCodec
import dev.transmute.audio.codecs.jvm.JvmFlacCodec
import dev.transmute.audio.codecs.jvm.JvmM4aCodec
import dev.transmute.audio.codecs.jvm.JvmMp3Codec
import dev.transmute.audio.codecs.jvm.JvmOggVorbisCodec
import dev.transmute.audio.codecs.jvm.JvmOpusCodec

actual fun installPlatformAudioCodecs(
  decoders: MutableAudioDecoderRegistry,
  encoders: MutableAudioEncoderRegistry,
) {
  // Full codec - decode via JLayer, encode via Jump3r (LAME).
  val mp3Codec = JvmMp3Codec()
  decoders.register(mp3Codec)
  encoders.register(mp3Codec)

  // Mixed codecs - pure-Java decode, FFmpeg encode (if available).
  val flacCodec = JvmFlacCodec()
  decoders.register(flacCodec)
  encoders.register(flacCodec) // no-op if FFmpeg unavailable

  val oggCodec = JvmOggVorbisCodec()
  decoders.register(oggCodec)
  encoders.register(oggCodec) // no-op if FFmpeg unavailable

  // Always register decoders for format detection (sniffing doesn't need FFmpeg).
  // Decode/encode will throw at runtime if FFmpeg is absent.
  val aacCodec = JvmAacCodec()
  decoders.register(aacCodec)
  if (FfmpegAudioEngine.available) encoders.register(aacCodec)

  val m4aCodec = JvmM4aCodec()
  decoders.register(m4aCodec)
  if (FfmpegAudioEngine.available) encoders.register(m4aCodec)

  val opusCodec = JvmOpusCodec()
  decoders.register(opusCodec)
  if (FfmpegAudioEngine.available) encoders.register(opusCodec)
}
