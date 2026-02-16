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

  // FFmpeg-based codecs for HEIF, HEIC, AVIF (when FFmpeg is available)
  if (FfmpegResolver.available) {
    decoders.register(FfmpegImageDecoder())
    encoders.register(FfmpegImageEncoder())
  }
}
