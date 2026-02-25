package dev.transmute.image

import dev.transmute.image.codecs.jvm.JvmImageIoDecoder
import dev.transmute.image.codecs.jvm.JvmImageIoEncoder

actual fun installPlatformImageCodecs(
  decoders: MutableImageDecoderRegistry,
  encoders: MutableImageEncoderRegistry,
) {
  decoders.register(JvmImageIoDecoder())
  encoders.register(JvmImageIoEncoder())

  // Note: HEIF, HEIC, and AVIF codecs are provided by the optional
  // transmute-gstreamer module. Add it as a dependency and configure
  // via TransmuteContext { gstreamer() } to enable them.
}
