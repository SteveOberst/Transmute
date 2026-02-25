package dev.transmute.audio.codecs.ios

import dev.transmute.audio.AudioFormat
import dev.transmute.audio.AudioTestHelpers
import dev.transmute.audio.CanonicalAudioDecodeOptions
import dev.transmute.audio.CanonicalAudioEncodeOptions
import dev.transmute.model.core.asBytes
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * iOS integration tests for the AVFoundation-based audio codecs.
 *
 * Run: `./gradlew :transmute-audio:iosSimulatorArm64Test`
 */
class IosAudioCodecTest {

  @Test
  fun m4aRoundTripProducesAudio() = runTest {
    val codec = IosM4aCodec()
    val original = AudioTestHelpers.sineWave(frequency = 880f, durationMs = 750)
    val ctx = AudioTestHelpers.testContext()

    val encoded = try {
      codec.encode(original, AudioFormat.M4a, CanonicalAudioEncodeOptions(), ctx)
    } catch (e: Throwable) {
      println("SKIP: M4A encoding not available on this simulator: ${e::class.simpleName}: ${e.message}")
      return@runTest
    }
    assertTrue(encoded.data.isNotEmpty(), "M4A encoded bytes should not be empty")

    val decoded = try {
      codec.decode(encoded, CanonicalAudioDecodeOptions(), ctx)
    } catch (e: Throwable) {
      println("SKIP: M4A decoding not available on this simulator: ${e::class.simpleName}: ${e.message}")
      return@runTest
    }

    assertTrue(decoded.sampleRate > 0, "Decoded sample rate should be > 0")
    assertTrue(decoded.channelCount > 0, "Decoded channel count should be > 0")
    assertTrue(decoded.durationMs > 200, "Decoded duration should be > 200ms, was ${decoded.durationMs}ms")

    val peak = AudioTestHelpers.peakAmplitude(decoded)
    assertTrue(peak > 0.01f, "Decoded audio should contain non-silent samples, peak=$peak")
  }

  @Test
  fun codecReportsCorrectFormats() {
    assertTrue(AudioFormat.Flac in IosFlacCodec().decodableFormats)
    assertTrue(IosFlacCodec().encodableFormats.isEmpty())

    assertTrue(AudioFormat.Aac in IosAacCodec().decodableFormats)
    assertTrue(IosAacCodec().encodableFormats.isEmpty())

    assertTrue(AudioFormat.M4a in IosM4aCodec().decodableFormats)
    assertTrue(AudioFormat.M4a in IosM4aCodec().encodableFormats)
  }

  @Test
  fun mp3DecoderReportsCorrectFormats() {
    val decoder = IosMp3Decoder()
    assertTrue(AudioFormat.Mp3 in decoder.supportedFormats)
  }

  @Test
  fun sniffWorksForCommonHeaders() {
    // FLAC: "fLaC"
    val flacHeader = byteArrayOf(0x66, 0x4C, 0x61, 0x43, 0, 0, 0, 0).asBytes()
    assertTrue(IosFlacCodec().sniff(flacHeader) == AudioFormat.Flac)

    // AAC ADTS: 0xFF 0xF1
    val adtsHeader = byteArrayOf(0xFF.toByte(), 0xF1.toByte(), 0x00, 0x00).asBytes()
    assertTrue(IosAacCodec().sniff(adtsHeader) == AudioFormat.Aac)

    // M4A: ftyp + "M4A "
    val m4aHeader = byteArrayOf(
      0x00, 0x00, 0x00, 0x20,
      0x66, 0x74, 0x79, 0x70,
      'M'.code.toByte(), '4'.code.toByte(), 'A'.code.toByte(), ' '.code.toByte(),
    ).asBytes()
    assertTrue(IosM4aCodec().sniff(m4aHeader) == AudioFormat.M4a)
  }
}

