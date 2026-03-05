package dev.transmute.testing.dsl

import dev.transmute.testing.Color
import dev.transmute.testing.audio.AudioAssertions.assertChannelCount
import dev.transmute.testing.audio.AudioAssertions.assertDurationNear
import dev.transmute.testing.audio.AudioAssertions.assertNotSilent
import dev.transmute.testing.audio.AudioAssertions.assertSampleRate
import dev.transmute.testing.audio.AudioAssertions.assertSilent
import dev.transmute.testing.audio.AudioAssertions.peakAmplitude
import dev.transmute.testing.audio.AudioAssertions.rms
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AudioDslTest {

  @Test
  fun simpleSine() {
    val ir = syntheticAudio {
      duration = 500.ms
      sine(440.hz)
    }
    assertEquals(44100, ir.sampleRate)
    assertEquals(1, ir.channelCount)
    assertDurationNear(ir, 500L, toleranceMs = 50)
    assertNotSilent(ir)
  }

  @Test
  fun silenceProducesZeros() {
    val ir = syntheticAudio {
      duration = 200.ms
      silence()
    }
    assertSilent(ir)
  }

  @Test
  fun customSampleRate() {
    val ir = syntheticAudio {
      sampleRate = 48000
      duration = 100.ms
      sine(440.hz)
    }
    assertSampleRate(ir, 48000)
  }

  @Test
  fun squareWaveHighRms() {
    val ir = syntheticAudio {
      duration = 200.ms
      square(440.hz, amplitude = 0.8f)
    }
    assertTrue(rms(ir) > 0.5f, "Square wave should have high RMS")
  }

  @Test
  fun mixTwoSines() {
    val ir = syntheticAudio {
      duration = 500.ms
      mix {
        sine(440.hz, amplitude = 0.4f)
        sine(880.hz, amplitude = 0.3f)
      }
    }
    assertNotSilent(ir)
    // Peak should be less than sum of amplitudes (constructive interference not guaranteed)
    assertTrue(peakAmplitude(ir) <= 0.8f)
  }

  @Test
  fun mixSineAndNoise() {
    val ir = syntheticAudio {
      duration = 300.ms
      mix {
        sine(440.hz, amplitude = 0.5f)
        whiteNoise(amplitude = 0.1f)
      }
    }
    assertNotSilent(ir)
  }

  @Test
  fun sequenceConcatenatesSegments() {
    val ir = syntheticAudio {
      sequence {
        segment(250.ms) { sine(440.hz) }
        segment(250.ms) { sine(880.hz) }
      }
    }
    assertDurationNear(ir, 500L, toleranceMs = 50)
    assertNotSilent(ir)
  }

  @Test
  fun sequenceWithSilenceGap() {
    val ir = syntheticAudio {
      sequence {
        segment(200.ms) { sine(440.hz) }
        segment(100.ms) { silence() }
        segment(200.ms) { sine(880.hz) }
      }
    }
    assertDurationNear(ir, 500L, toleranceMs = 50)
  }

  @Test
  fun fadeInReducesStart() {
    val ir = syntheticAudio {
      duration = 500.ms
      sine(440.hz, amplitude = 0.8f)
      fadeIn(200.ms)
    }
    // First few samples should be near zero
    val first50 = ir.samples.data.take(50).map { abs(it) }.average()
    assertTrue(first50 < 0.1, "Fade-in start should be near silent, avg=$first50")
  }

  @Test
  fun fadeOutReducesEnd() {
    val ir = syntheticAudio {
      duration = 500.ms
      sine(440.hz, amplitude = 0.8f)
      fadeOut(200.ms)
    }
    val last50 = ir.samples.data.takeLast(50).map { abs(it) }.average()
    assertTrue(last50 < 0.1, "Fade-out end should be near silent, avg=$last50")
  }

  @Test
  fun amplifyScalesSamples() {
    val ir = syntheticAudio {
      duration = 100.ms
      sine(440.hz, amplitude = 0.5f)
      amplify(0.5f)
    }
    assertTrue(peakAmplitude(ir) < 0.3f, "Amplified 0.5*0.5 should peak near 0.25")
  }

  @Test
  fun normalizeScalesToCeiling() {
    val ir = syntheticAudio {
      duration = 100.ms
      sine(440.hz, amplitude = 0.3f)
      normalize(ceiling = 1f)
    }
    val peak = peakAmplitude(ir)
    assertTrue(peak in 0.95f..1.0f, "Normalized peak should be ~1.0, got $peak")
  }

  @Test
  fun adsrShapesEnvelope() {
    val ir = syntheticAudio {
      duration = 1.seconds
      sine(440.hz, amplitude = 1f)
      adsr {
        attack = 100.ms
        decay = 100.ms
        sustain = 0.5f
        release = 200.ms
      }
    }
    // Start should be near zero (attack)
    val first10 = ir.samples.data.take(10).map { abs(it) }.average()
    assertTrue(first10 < 0.1, "ADSR attack start should be near silent, avg=$first10")
    // End should be near zero (release)
    val last10 = ir.samples.data.takeLast(10).map { abs(it) }.average()
    assertTrue(last10 < 0.1, "ADSR release end should be near silent, avg=$last10")
  }

  @Test
  fun stereoDoublesSampleCount() {
    val ir = syntheticAudio {
      duration = 100.ms
      sine(440.hz)
      stereo { pan = 0f }
    }
    assertChannelCount(ir, 2)
    // Stereo has 2x samples per time unit
    val monoCount = (44100 * 100 / 1000)
    assertTrue(ir.samples.data.size >= monoCount * 2, "Stereo should have ≥${monoCount * 2} samples")
  }

  @Test
  fun stereoPanHardLeft() {
    val ir = syntheticAudio {
      duration = 200.ms
      sine(440.hz, amplitude = 0.8f)
      stereo { pan = -1f }
    }
    // Right channel (odd indices) should be near zero
    var rightSum = 0f
    for (i in 1 until ir.samples.data.size step 2) {
      rightSum += abs(ir.samples.data[i])
    }
    assertTrue(rightSum < 0.01f, "Hard left pan: right channel should be ~0, sum=$rightSum")
  }

  @Test
  fun stereoPanHardRight() {
    val ir = syntheticAudio {
      duration = 200.ms
      sine(440.hz, amplitude = 0.8f)
      stereo { pan = 1f }
    }
    // Left channel (even indices) should be near zero
    var leftSum = 0f
    for (i in 0 until ir.samples.data.size step 2) {
      leftSum += abs(ir.samples.data[i])
    }
    assertTrue(leftSum < 0.01f, "Hard right pan: left channel should be ~0, sum=$leftSum")
  }

  @Test
  fun customGenerator() {
    val ir = syntheticAudio {
      duration = 100.ms
      generate { index, sr ->
        (sin(2.0 * PI * 440.0 * index / sr) * 0.5).toFloat()
      }
    }
    assertNotSilent(ir)
  }

  @Test
  fun chirpLinear() {
    val ir = syntheticAudio {
      duration = 500.ms
      chirp(startHz = 100.0, endHz = 4000.0, sweep = SweepType.LINEAR)
    }
    assertNotSilent(ir)
  }

  @Test
  fun chirpLogarithmic() {
    val ir = syntheticAudio {
      duration = 500.ms
      chirp(startHz = 100.0, endHz = 4000.0, sweep = SweepType.LOGARITHMIC)
    }
    assertNotSilent(ir)
  }

  @Test
  fun pinkNoiseNotSilent() {
    val ir = syntheticAudio {
      duration = 200.ms
      pinkNoise(amplitude = 0.4f)
    }
    assertNotSilent(ir)
  }

  @Test
  fun dcOffset() {
    val ir = syntheticAudio {
      duration = 100.ms
      dc(0.5f)
    }
    assertTrue(ir.samples.data.all { abs(it - 0.5f) < 0.001f }, "DC should be constant 0.5")
  }

  @Test
  fun impulseHasSinglePeak() {
    val ir = syntheticAudio {
      duration = 100.ms
      impulse(positionMs = 0, amplitude = 1f)
    }
    assertEquals(1f, ir.samples.data[0])
    assertTrue(ir.samples.data.drop(1).all { it == 0f })
  }

  @Test
  fun durationWithSecondsUnit() {
    val ir = syntheticAudio {
      duration = 2.seconds
      silence()
    }
    assertDurationNear(ir, 2000L, toleranceMs = 50)
  }

  @Test
  fun multipleEffectsChained() {
    val ir = syntheticAudio {
      duration = 1.seconds
      sine(440.hz, amplitude = 1f)
      fadeIn(100.ms)
      fadeOut(200.ms)
      amplify(0.5f)
    }
    // Peak should be well below 1.0 due to amplify
    assertTrue(peakAmplitude(ir) <= 0.55f, "Chained effects should reduce amplitude")
  }
}
