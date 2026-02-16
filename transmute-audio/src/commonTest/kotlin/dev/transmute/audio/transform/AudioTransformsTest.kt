package dev.transmute.audio.transform

import dev.transmute.audio.AudioIR
import dev.transmute.audio.AudioSamples
import dev.transmute.audio.AudioTestHelpers.peakAmplitude
import dev.transmute.audio.AudioTestHelpers.rms
import dev.transmute.audio.AudioTestHelpers.sineWave
import dev.transmute.audio.AudioTestHelpers.silence
import dev.transmute.audio.AudioTestHelpers.testContext
import kotlinx.coroutines.test.runTest
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AudioNormalizeTransformTest {

  private val context = testContext()

  @Test
  fun normalizesToTargetPeak() = runTest {
    val quiet = sineWave(amplitude = 0.25f, durationMs = 100)
    val transform = AudioNormalizeTransform(targetPeak = 0.9f)

    val result = transform.apply(quiet, context)

    val peak = peakAmplitude(result)
    assertTrue(abs(peak - 0.9f) < 0.01f, "Peak should be ~0.9, was $peak")
  }

  @Test
  fun doesNotAmplifyLoudAudio() = runTest {
    val loud = sineWave(amplitude = 0.95f, durationMs = 100)
    val transform = AudioNormalizeTransform(targetPeak = 0.9f)

    val result = transform.apply(loud, context)

    // Should not be amplified since it's already > target
    val originalPeak = peakAmplitude(loud)
    val resultPeak = peakAmplitude(result)
    assertTrue(abs(originalPeak - resultPeak) < 0.01f)
  }

  @Test
  fun handlesSilence() = runTest {
    val silent = silence(durationMs = 100)
    val transform = AudioNormalizeTransform()

    val result = transform.apply(silent, context)

    // All samples should still be 0
    assertTrue(result.samples.data.all { it == 0f })
  }
}

class AudioResampleTransformTest {

  private val context = testContext()

  @Test
  fun upsamples44100to48000() = runTest {
    val original = sineWave(sampleRate = 44100, durationMs = 100)
    val transform = AudioResampleTransform(targetSampleRate = 48000)

    val result = transform.apply(original, context)

    assertEquals(48000, result.sampleRate)
    assertTrue(result.samples.data.size > original.samples.data.size)
  }

  @Test
  fun downsamples48000to22050() = runTest {
    val original = sineWave(sampleRate = 48000, durationMs = 100)
    val transform = AudioResampleTransform(targetSampleRate = 22050)

    val result = transform.apply(original, context)

    assertEquals(22050, result.sampleRate)
    assertTrue(result.samples.data.size < original.samples.data.size)
  }

  @Test
  fun noChangeIfSameSampleRate() = runTest {
    val original = sineWave(sampleRate = 44100, durationMs = 100)
    val transform = AudioResampleTransform(targetSampleRate = 44100)

    val result = transform.apply(original, context)

    assertEquals(original.samples.data.size, result.samples.data.size)
  }

  @Test
  fun preservesStereoChannels() = runTest {
    val stereo = sineWave(sampleRate = 44100, durationMs = 100, channelCount = 2)
    val transform = AudioResampleTransform(targetSampleRate = 22050)

    val result = transform.apply(stereo, context)

    assertEquals(2, result.channelCount)
  }
}

class AudioFadeTransformTest {

  private val context = testContext()

  @Test
  fun fadeInStartsAtZero() = runTest {
    val audio = sineWave(amplitude = 0.8f, durationMs = 500)
    val transform = AudioFadeTransform(fadeInMs = 100)

    val result = transform.apply(audio, context)

    // First sample should be ~0
    assertTrue(abs(result.samples.data[0]) < 0.01f)
  }

  @Test
  fun fadeOutEndsAtZero() = runTest {
    val audio = sineWave(amplitude = 0.8f, durationMs = 500)
    val transform = AudioFadeTransform(fadeOutMs = 100)

    val result = transform.apply(audio, context)

    // Last sample should be ~0
    val lastSample = result.samples.data.last()
    assertTrue(abs(lastSample) < 0.01f)
  }

  @Test
  fun noChangeIfNoFade() = runTest {
    val audio = sineWave(durationMs = 100)
    val transform = AudioFadeTransform(fadeInMs = 0, fadeOutMs = 0)

    val result = transform.apply(audio, context)

    assertTrue(result.samples.data.contentEquals(audio.samples.data))
  }
}

class AudioTrimTransformTest {

  private val context = testContext()

  @Test
  fun trimStartOnly() = runTest {
    val audio = sineWave(durationMs = 1000, sampleRate = 1000, channelCount = 1)
    val transform = AudioTrimTransform(startMs = 500)

    val result = transform.apply(audio, context)

    // Should be ~half the original length
    assertTrue(result.samples.data.size < audio.samples.data.size)
    assertTrue(result.durationMs in 400..600)
  }

  @Test
  fun trimEndOnly() = runTest {
    val audio = sineWave(durationMs = 1000, sampleRate = 1000, channelCount = 1)
    val transform = AudioTrimTransform(startMs = 0, endMs = 500)

    val result = transform.apply(audio, context)

    assertTrue(result.samples.data.size < audio.samples.data.size)
    assertTrue(result.durationMs in 400..600)
  }

  @Test
  fun trimBothEnds() = runTest {
    val audio = sineWave(durationMs = 1000, sampleRate = 1000, channelCount = 1)
    val transform = AudioTrimTransform(startMs = 250, endMs = 750)

    val result = transform.apply(audio, context)

    assertTrue(result.durationMs in 400..600)
  }
}

class AudioGainTransformTest {

  private val context = testContext()

  @Test
  fun positiveGainMakesLouder() = runTest {
    val audio = sineWave(amplitude = 0.3f, durationMs = 100)
    val originalPeak = peakAmplitude(audio)
    val transform = AudioGainTransform(gainDb = 6f) // ~2x volume

    val result = transform.apply(audio, context)

    val resultPeak = peakAmplitude(result)
    assertTrue(resultPeak > originalPeak * 1.5f)
  }

  @Test
  fun negativeGainMakesQuieter() = runTest {
    val audio = sineWave(amplitude = 0.8f, durationMs = 100)
    val originalPeak = peakAmplitude(audio)
    val transform = AudioGainTransform(gainDb = -6f) // ~0.5x volume

    val result = transform.apply(audio, context)

    val resultPeak = peakAmplitude(result)
    assertTrue(resultPeak < originalPeak * 0.75f)
  }

  @Test
  fun clipsAtUnity() = runTest {
    val audio = sineWave(amplitude = 0.8f, durationMs = 100)
    val transform = AudioGainTransform(gainDb = 20f) // Way too loud

    val result = transform.apply(audio, context)

    // All samples should be clamped to [-1, 1]
    for (sample in result.samples.data) {
      assertTrue(sample >= -1f && sample <= 1f)
    }
  }
}

class AudioMonoTransformTest {

  private val context = testContext()

  @Test
  fun convertsToMono() = runTest {
    val stereo = sineWave(channelCount = 2, durationMs = 100, sampleRate = 1000)
    val transform = AudioMonoTransform()

    val result = transform.apply(stereo, context)

    assertEquals(1, result.channelCount)
    assertEquals(stereo.samples.data.size / 2, result.samples.data.size)
  }

  @Test
  fun monoRemainsUnchanged() = runTest {
    val mono = sineWave(channelCount = 1, durationMs = 100)
    val transform = AudioMonoTransform()

    val result = transform.apply(mono, context)

    assertEquals(mono.samples.data.size, result.samples.data.size)
  }
}

class AudioReverseTransformTest {

  private val context = testContext()

  @Test
  fun reversesAudio() = runTest {
    // Create audio where first sample is 0.1 and last is 0.9
    val samples = FloatArray(100) { it / 100f }
    val audio = AudioIR(
      samples = dev.transmute.audio.AudioSamples(samples, 1000, 1),
      sampleRate = 1000,
      channelCount = 1,
      durationMs = 100,
    )
    val transform = AudioReverseTransform()

    val result = transform.apply(audio, context)

    // First should now be ~0.99, last should be ~0
    assertTrue(result.samples.data[0] > 0.9f)
    assertTrue(result.samples.data[99] < 0.1f)
  }

  @Test
  fun doubleReverseRestoresOriginal() = runTest {
    val audio = sineWave(durationMs = 100)
    val transform = AudioReverseTransform()

    val reversed = transform.apply(audio, context)
    val restored = transform.apply(reversed, context)

    assertTrue(audio.samples.data.contentEquals(restored.samples.data))
  }
}

// ── Speed ──

class AudioSpeedTransformTest {

  private val context = testContext()

  @Test
  fun speed2xRoughlyHalvesDuration() = runTest {
    val audio = sineWave(durationMs = 1000, sampleRate = 8000, channelCount = 1)
    val result = AudioSpeedTransform(speed = 2f).apply(audio, context)

    val inputFrames = audio.samples.data.size
    val outputFrames = result.samples.data.size

    // Output should be roughly half the input length (±20%).
    assertTrue(
      outputFrames in (inputFrames / 3)..(inputFrames * 3 / 4),
      "Expected ~half frames: input=$inputFrames, output=$outputFrames"
    )
    assertTrue(result.durationMs < audio.durationMs, "Duration should be shorter")
  }

  @Test
  fun speed0_5xRoughlyDoublesDuration() = runTest {
    val audio = sineWave(durationMs = 500, sampleRate = 8000, channelCount = 1)
    val result = AudioSpeedTransform(speed = 0.5f).apply(audio, context)

    val inputFrames = audio.samples.data.size
    val outputFrames = result.samples.data.size

    assertTrue(
      outputFrames > inputFrames,
      "Expected more frames: input=$inputFrames, output=$outputFrames"
    )
    assertTrue(result.durationMs > audio.durationMs, "Duration should be longer")
  }

  @Test
  fun speed1xIsIdentity() = runTest {
    val audio = sineWave(durationMs = 200, sampleRate = 8000)
    val result = AudioSpeedTransform(speed = 1f).apply(audio, context)

    assertTrue(audio.samples.data.contentEquals(result.samples.data))
    assertEquals(audio.durationMs, result.durationMs)
  }

  @Test
  fun preservesSampleRate() = runTest {
    val audio = sineWave(durationMs = 500, sampleRate = 22050)
    val result = AudioSpeedTransform(speed = 1.5f).apply(audio, context)

    assertEquals(22050, result.sampleRate)
  }

  @Test
  fun preservesChannelCount() = runTest {
    val audio = sineWave(durationMs = 500, sampleRate = 8000, channelCount = 2)
    val result = AudioSpeedTransform(speed = 2f).apply(audio, context)

    assertEquals(2, result.channelCount)
    assertEquals(0, result.samples.data.size % 2, "Output samples should be multiple of channel count")
  }
}

// ── Silence Trim ──

class AudioSilenceTrimTransformTest {

  private val context = testContext()

  @Test
  fun trimsLeadingSilence() = runTest {
    // 500ms silence + 500ms sine
    val sampleRate = 8000
    val silenceSamples = sampleRate / 2 // 500ms
    val toneSamples = sampleRate / 2
    val data = FloatArray(silenceSamples + toneSamples)
    for (i in silenceSamples until data.size) {
      data[i] = 0.5f * kotlin.math.sin(2.0 * kotlin.math.PI * 440.0 * (i - silenceSamples) / sampleRate).toFloat()
    }

    val audio = AudioIR(
      samples = AudioSamples(data, sampleRate, 1),
      sampleRate = sampleRate,
      channelCount = 1,
      durationMs = 1000,
    )

    val result = AudioSilenceTrimTransform(thresholdDb = -40f, trimEnd = false).apply(audio, context)

    // Output should be shorter — leading silence removed.
    assertTrue(
      result.samples.data.size < audio.samples.data.size,
      "Should have fewer samples after trimming leading silence"
    )
    // First sample of result should be non-silent.
    assertTrue(abs(result.samples.data[0]) > 0.001f, "First sample should be non-silent")
  }

  @Test
  fun trimsTrailingSilence() = runTest {
    val sampleRate = 8000
    val toneSamples = sampleRate / 2
    val silenceSamples = sampleRate / 2
    val data = FloatArray(toneSamples + silenceSamples)
    for (i in 0 until toneSamples) {
      data[i] = 0.5f * kotlin.math.sin(2.0 * kotlin.math.PI * 440.0 * i / sampleRate).toFloat()
    }

    val audio = AudioIR(
      samples = AudioSamples(data, sampleRate, 1),
      sampleRate = sampleRate,
      channelCount = 1,
      durationMs = 1000,
    )

    val result = AudioSilenceTrimTransform(thresholdDb = -40f, trimStart = false).apply(audio, context)

    assertTrue(
      result.samples.data.size < audio.samples.data.size,
      "Should have fewer samples after trimming trailing silence"
    )
  }

  @Test
  fun allSilenceReturnsEmpty() = runTest {
    val audio = silence(durationMs = 500, sampleRate = 8000)

    val result = AudioSilenceTrimTransform(thresholdDb = -40f).apply(audio, context)

    assertEquals(0, result.samples.data.size)
    assertEquals(0L, result.durationMs)
  }

  @Test
  fun noSilenceNoChange() = runTest {
    // Constant signal well above threshold — nothing to trim.
    val sampleRate = 8000
    val frames = (200 * sampleRate) / 1000
    val data = FloatArray(frames) { 0.8f }
    val audio = AudioIR(
      samples = AudioSamples(data, sampleRate, 1),
      sampleRate = sampleRate,
      channelCount = 1,
      durationMs = 200,
    )

    val result = AudioSilenceTrimTransform(thresholdDb = -40f).apply(audio, context)

    assertEquals(audio.samples.data.size, result.samples.data.size)
  }

  @Test
  fun noTrimFlagsReturnsIdentity() = runTest {
    val audio = silence(durationMs = 500, sampleRate = 8000)

    val result = AudioSilenceTrimTransform(trimStart = false, trimEnd = false).apply(audio, context)

    assertTrue(audio.samples.data.contentEquals(result.samples.data))
  }
}

// ── Compressor ──

class AudioCompressorTransformTest {

  private val context = testContext()

  @Test
  fun reducesLoudPeak() = runTest {
    val audio = sineWave(durationMs = 500, sampleRate = 8000, amplitude = 0.9f)
    val inputPeak = peakAmplitude(audio)

    val result = AudioCompressorTransform(
      thresholdDb = -6f, ratio = 4f, attackMs = 1f, releaseMs = 10f,
    ).apply(audio, context)
    val outputPeak = peakAmplitude(result)

    assertTrue(
      outputPeak < inputPeak,
      "Peak should be reduced: input=$inputPeak → output=$outputPeak"
    )
  }

  @Test
  fun softAudioNotAffected() = runTest {
    val audio = sineWave(durationMs = 200, sampleRate = 8000, amplitude = 0.01f)
    val inputRms = rms(audio)

    val result = AudioCompressorTransform(
      thresholdDb = -6f, ratio = 4f,
    ).apply(audio, context)
    val outputRms = rms(result)

    // Soft audio should be essentially unchanged (within 10%).
    assertTrue(
      abs(outputRms - inputRms) / (inputRms + 1e-9f) < 0.1f,
      "Soft audio should be mostly unchanged: input=$inputRms, output=$outputRms"
    )
  }

  @Test
  fun outputWithinBounds() = runTest {
    val audio = sineWave(durationMs = 300, sampleRate = 8000, amplitude = 0.95f)

    val result = AudioCompressorTransform(
      thresholdDb = -10f, ratio = 8f, makeupGainDb = 6f,
    ).apply(audio, context)

    val peak = peakAmplitude(result)
    assertTrue(peak <= 1.0f, "Output should be clamped to [-1,1], got $peak")
  }

  @Test
  fun preservesDuration() = runTest {
    val audio = sineWave(durationMs = 500, sampleRate = 8000)

    val result = AudioCompressorTransform(
      thresholdDb = -20f, ratio = 4f,
    ).apply(audio, context)

    assertEquals(audio.samples.data.size, result.samples.data.size)
    assertEquals(audio.durationMs, result.durationMs)
  }

  @Test
  fun ratio1xSkips() = runTest {
    val audio = sineWave(durationMs = 200, sampleRate = 8000)

    val result = AudioCompressorTransform(ratio = 1f).apply(audio, context)

    assertTrue(audio.samples.data.contentEquals(result.samples.data))
  }
}

// ── Channel Map ──

class AudioChannelMapTransformTest {

  private val context = testContext()

  @Test
  fun identityMappingIsNoOp() = runTest {
    val audio = sineWave(durationMs = 200, sampleRate = 8000, channelCount = 2)

    val result = AudioChannelMapTransform(mapping = intArrayOf(0, 1)).apply(audio, context)

    assertTrue(audio.samples.data.contentEquals(result.samples.data))
    assertEquals(2, result.channelCount)
  }

  @Test
  fun swapLeftRight() = runTest {
    val sampleRate = 8000
    val frames = 100
    val data = FloatArray(frames * 2)
    for (f in 0 until frames) {
      data[f * 2] = 1.0f      // L = 1.0
      data[f * 2 + 1] = -1.0f // R = -1.0
    }

    val audio = AudioIR(
      samples = AudioSamples(data, sampleRate, 2),
      sampleRate = sampleRate,
      channelCount = 2,
      durationMs = (frames.toLong() * 1000) / sampleRate,
    )

    val result = AudioChannelMapTransform(mapping = intArrayOf(1, 0)).apply(audio, context)

    // After swap: L should be -1.0, R should be 1.0
    for (f in 0 until frames) {
      assertEquals(-1.0f, result.samples.data[f * 2], "Frame $f L should be swapped")
      assertEquals(1.0f, result.samples.data[f * 2 + 1], "Frame $f R should be swapped")
    }
  }

  @Test
  fun monoToStereo() = runTest {
    val sampleRate = 8000
    val frames = 50
    val data = FloatArray(frames) { 0.5f } // mono channel = 0.5

    val audio = AudioIR(
      samples = AudioSamples(data, sampleRate, 1),
      sampleRate = sampleRate,
      channelCount = 1,
      durationMs = (frames.toLong() * 1000) / sampleRate,
    )

    // Duplicate mono to both stereo channels.
    val result = AudioChannelMapTransform(mapping = intArrayOf(0, 0)).apply(audio, context)

    assertEquals(2, result.channelCount)
    assertEquals(frames * 2, result.samples.data.size)
    for (f in 0 until frames) {
      assertEquals(0.5f, result.samples.data[f * 2])
      assertEquals(0.5f, result.samples.data[f * 2 + 1])
    }
  }

  @Test
  fun stereoToMono() = runTest {
    val sampleRate = 8000
    val frames = 50
    val data = FloatArray(frames * 2)
    for (f in 0 until frames) {
      data[f * 2] = 0.8f
      data[f * 2 + 1] = -0.3f
    }

    val audio = AudioIR(
      samples = AudioSamples(data, sampleRate, 2),
      sampleRate = sampleRate,
      channelCount = 2,
      durationMs = (frames.toLong() * 1000) / sampleRate,
    )

    // Pick only Left channel.
    val result = AudioChannelMapTransform(mapping = intArrayOf(0)).apply(audio, context)

    assertEquals(1, result.channelCount)
    assertEquals(frames, result.samples.data.size)
    for (f in 0 until frames) {
      assertEquals(0.8f, result.samples.data[f])
    }
  }

  @Test
  fun channelCountChanges() = runTest {
    val audio = sineWave(durationMs = 200, sampleRate = 8000, channelCount = 1)

    // 1→4: duplicate mono to all channels.
    val result = AudioChannelMapTransform(mapping = intArrayOf(0, 0, 0, 0)).apply(audio, context)

    assertEquals(4, result.channelCount)
    assertEquals(audio.samples.data.size * 4, result.samples.data.size)
  }
}
