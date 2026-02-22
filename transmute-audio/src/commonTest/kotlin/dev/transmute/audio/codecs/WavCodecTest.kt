package dev.transmute.audio.codecs

import dev.transmute.audio.AudioTestHelpers.sineWave
import dev.transmute.audio.AudioTestHelpers.testContext
import dev.transmute.audio.AudioTestHelpers.peakAmplitude
import dev.transmute.audio.AudioFormat
import kotlinx.coroutines.test.runTest
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import dev.transmute.audio.CanonicalAudioEncodeOptions
import dev.transmute.audio.CanonicalAudioDecodeOptions

class WavCodecTest {

  private val decoder = WavDecoder()
  private val encoder = WavEncoder()
  private val context = testContext()

  @Test
  fun supportedFormats() {
    assertTrue(AudioFormat.Wav in decoder.supportedFormats)
    assertTrue(AudioFormat.Wav in encoder.supportedFormats)
  }

  @Test
  fun roundTripMono() = runTest {
    val original = sineWave(
      frequency = 440f,
      durationMs = 100,
      sampleRate = 44100,
      amplitude = 0.8f,
      channelCount = 1,
    )

    val encoded = encoder.encode(original, AudioFormat.Wav, CanonicalAudioEncodeOptions(), context)
    val decoded = decoder.decode(encoded, CanonicalAudioDecodeOptions(), context)

    assertEquals(original.sampleRate, decoded.sampleRate)
    assertEquals(original.channelCount, decoded.channelCount)
    assertEquals(original.samples.data.size, decoded.samples.data.size)

    // Check samples are within quantization error (16-bit = 1/32768 ≈ 0.00003)
    for (i in original.samples.data.indices) {
      val diff = abs(original.samples.data[i] - decoded.samples.data[i])
      assertTrue(diff < 0.001f, "Sample $i differs by $diff")
    }
  }

  @Test
  fun roundTripStereo() = runTest {
    val original = sineWave(
      frequency = 880f,
      durationMs = 50,
      sampleRate = 48000,
      amplitude = 0.5f,
      channelCount = 2,
    )

    val encoded = encoder.encode(original, AudioFormat.Wav, CanonicalAudioEncodeOptions(), context)
    val decoded = decoder.decode(encoded, CanonicalAudioDecodeOptions(), context)

    assertEquals(original.sampleRate, decoded.sampleRate)
    assertEquals(original.channelCount, decoded.channelCount)

    // Verify peak amplitude is preserved
    val originalPeak = peakAmplitude(original)
    val decodedPeak = peakAmplitude(decoded)
    assertTrue(abs(originalPeak - decodedPeak) < 0.01f)
  }

  @Test
  fun preservesSampleRate() = runTest {
    val rates = listOf(8000, 22050, 44100, 48000, 96000)

    for (rate in rates) {
      val original = sineWave(sampleRate = rate, durationMs = 10)
      val encoded = encoder.encode(original, AudioFormat.Wav, CanonicalAudioEncodeOptions(), context)
      val decoded = decoder.decode(encoded, CanonicalAudioDecodeOptions(), context)
      assertEquals(rate, decoded.sampleRate, "Sample rate $rate not preserved")
    }
  }

  @Test
  fun handlesSmallAudio() = runTest {
    // Very short audio: 10 samples
    val tiny = sineWave(durationMs = 1, sampleRate = 8000)
    val encoded = encoder.encode(tiny, AudioFormat.Wav, CanonicalAudioEncodeOptions(), context)
    val decoded = decoder.decode(encoded, CanonicalAudioDecodeOptions(), context)

    assertTrue(decoded.samples.data.isNotEmpty())
  }

  @Test
  fun clampingPreventsSaturation() = runTest {
    // Audio with samples > 1.0
    val overdriven = sineWave(amplitude = 1.5f, durationMs = 10)
    val encoded = encoder.encode(overdriven, AudioFormat.Wav, CanonicalAudioEncodeOptions(), context)
    val decoded = decoder.decode(encoded, CanonicalAudioDecodeOptions(), context)

    // All decoded samples should be in [-1, 1]
    for (sample in decoded.samples.data) {
      assertTrue(sample >= -1f && sample <= 1f, "Sample out of range: $sample")
    }
  }
}
