package dev.transmute.image

import dev.transmute.image.codecs.ios.IosImageIoDecoder
import dev.transmute.image.codecs.ios.IosImageIoEncoder

actual fun installPlatformImageCodecs(
  decoders: MutableImageDecoderRegistry,
  encoders: MutableImageEncoderRegistry,
) {
  decoders.register(IosImageIoDecoder())
  encoders.register(IosImageIoEncoder())
}
