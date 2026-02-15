package dev.transmute.video

import dev.transmute.core.ConversionContext
import dev.transmute.core.VideoFormat

// ── Decoder ──

interface VideoDecoder {
  val supportedFormats: Set<VideoFormat>
  suspend fun decode(source: ByteArray, context: ConversionContext): VideoIR
}

interface VideoDecoderRegistry {
  fun decoderFor(format: VideoFormat): VideoDecoder?
}

// ── Encoder ──

interface VideoEncoder {
  val supportedFormats: Set<VideoFormat>
  suspend fun encode(ir: VideoIR, context: ConversionContext): ByteArray
}

interface VideoEncoderRegistry {
  fun encoderFor(format: VideoFormat): VideoEncoder?
}
