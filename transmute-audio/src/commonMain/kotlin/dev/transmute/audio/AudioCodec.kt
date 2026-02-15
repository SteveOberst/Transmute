package dev.transmute.audio

import dev.transmute.core.AudioFormat
import dev.transmute.core.ConversionContext

// ── Decoder ──

interface AudioDecoder {
  val supportedFormats: Set<AudioFormat>
  suspend fun decode(source: ByteArray, context: ConversionContext): AudioIR
}

interface AudioDecoderRegistry {
  fun decoderFor(format: AudioFormat): AudioDecoder?
}

// ── Encoder ──

interface AudioEncoder {
  val supportedFormats: Set<AudioFormat>
  suspend fun encode(ir: AudioIR, context: ConversionContext): ByteArray
}

interface AudioEncoderRegistry {
  fun encoderFor(format: AudioFormat): AudioEncoder?
}
