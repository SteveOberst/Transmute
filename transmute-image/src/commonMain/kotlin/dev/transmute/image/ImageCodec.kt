package dev.transmute.image

import dev.transmute.core.ConversionContext
import dev.transmute.core.ImageFormat

// ── Decoder ──

interface ImageDecoder {
  val supportedFormats: Set<ImageFormat>
  suspend fun decode(source: ByteArray, context: ConversionContext): ImageIR
}

interface ImageDecoderRegistry {
  fun decoderFor(format: ImageFormat): ImageDecoder?
}

// ── Encoder ──

interface ImageEncoder {
  val supportedFormats: Set<ImageFormat>
  suspend fun encode(ir: ImageIR, context: ConversionContext): ByteArray
}

interface ImageEncoderRegistry {
  fun encoderFor(format: ImageFormat): ImageEncoder?
}
