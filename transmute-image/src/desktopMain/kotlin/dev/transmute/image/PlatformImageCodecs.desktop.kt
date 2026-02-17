package dev.transmute.image

import dev.transmute.core.FfmpegResolver
import dev.transmute.image.codecs.jvm.FfmpegImageDecoder
import dev.transmute.image.codecs.jvm.FfmpegImageEncoder
import dev.transmute.image.codecs.jvm.JvmImageIoDecoder
import dev.transmute.image.codecs.jvm.JvmImageIoEncoder

actual fun installPlatformImageCodecs(
  decoders: MutableImageDecoderRegistry,
  encoders: MutableImageEncoderRegistry,
) {
  decoders.register(JvmImageIoDecoder())
  encoders.register(JvmImageIoEncoder())

  // Always register for format detection (sniffing doesn't need FFmpeg).
  // Decode/encode will throw at runtime if FFmpeg is absent.
  decoders.register(FfmpegImageDecoder())
  if (FfmpegResolver.available) {
    encoders.register(FfmpegImageEncoder())
  }
}
