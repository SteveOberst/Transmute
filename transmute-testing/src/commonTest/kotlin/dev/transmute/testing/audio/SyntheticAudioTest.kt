package dev.transmute.testing.audio

import dev.transmute.testing.audio.AudioAssertions.assertChannelCount
import dev.transmute.testing.audio.AudioAssertions.assertDurationNear
import dev.transmute.testing.audio.AudioAssertions.assertHighFidelity
import dev.transmute.testing.audio.AudioAssertions.assertMinSamples
import dev.transmute.testing.audio.AudioAssertions.assertNoClipping
import dev.transmute.testing.audio.AudioAssertions.assertNotSilent
import dev.transmute.testing.audio.AudioAssertions.assertRmsInRange
import dev.transmute.testing.audio.AudioAssertions.assertSampleRate
import dev.transmute.testing.audio.AudioAssertions.assertSilent
import dev.transmute.testing.audio.AudioAssertions.meanAbsoluteError
import dev.transmute.testing.audio.AudioAssertions.peakAmplitude
import dev.transmute.testing.audio.AudioAssertions.rms
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SyntheticAudioTest {

  @Test
  fun sineWaveBasicProperties() {
    val ir = SyntheticAudio.sineWave(durationMs = 500, frequency = 440f)
    assertEquals(44100, ir.sampleRate)
    assertEquals(1, ir.channelCount)
    assertDurationNear(ir, 500L, toleranceMs = 50)
    assertNotSilent(ir)
    assertNoClipping(ir)
  }

  @Test
  fun silenceIsSilent() {
    val ir = SyntheticAudio.silence(durationMs = 200)
    assertSilent(ir)
    assertEquals(0f, peakAmplitude(ir))
  }

  @Test
  fun whiteNoiseIsNotSilent() {
    val ir = SyntheticAudio.whiteNoise(durationMs = 200)
    assertNotSilent(ir)
    assertTrue(rms(ir) > 0.01f)
  }

  @Test
  fun pinkNoiseIsNotSilent() {
    val ir = SyntheticAudio.pinkNoise(durationMs = 200)
    assertNotSilent(ir)
  }

  @Test
  fun chirpCoversFrequencyRange() {
    val ir = SyntheticAudio.chirp(durationMs = 500)
    assertNotSilent(ir)
    assertSampleRate(ir, 44100)
  }

  @Test
  fun stereoSampleCount() {
    val ir = SyntheticAudio.stereoPingPong(durationMs = 300)
    assertEquals(2, ir.channelCount)
    assertChannelCount(ir, 2)
    // Stereo: samples = sampleRate * channels * durationSec
    val expectedSamples = (44100 * 2 * 0.3).toInt()
    assertMinSamples(ir, expectedSamples - 100)
  }

  @Test
  fun fullScaleReachesMax() {
    val ir = SyntheticAudio.fullScale(durationMs = 100)
    assertEquals(1f, peakAmplitude(ir))
  }

  @Test
  fun dcOffsetHasConstantValue() {
    val ir = SyntheticAudio.dcOffset(durationMs = 100, offset = 0.5f)
    val peak = peakAmplitude(ir)
    assertTrue(peak in 0.49f..0.51f, "DC offset peak should be ~0.5, got $peak")
  }

  @Test
  fun identityRoundTripHighFidelity() {
    val ir = SyntheticAudio.sineWave(durationMs = 300)
    // Comparing to self should always pass
    assertHighFidelity(ir, ir)
  }

  @Test
  fun meanAbsoluteErrorOfIdentity() {
    val ir = SyntheticAudio.sineWave(durationMs = 100)
    assertEquals(0f, meanAbsoluteError(ir, ir), "MAE of identical signals should be 0")
  }

  @Test
  fun squareWaveProperties() {
    val ir = SyntheticAudio.squareWave(durationMs = 200)
    assertNotSilent(ir)
    assertRmsInRange(ir, 0.5f, 1.0f) // square wave has high RMS
  }

  @Test
  fun fadeInStartsSilent() {
    val ir = SyntheticAudio.fadeIn(durationMs = 500)
    // First few samples should be near zero
    val first100 = ir.samples.data.take(100).map { kotlin.math.abs(it) }.average()
    assertTrue(first100 < 0.05, "Fade-in start should be near silent, avg=$first100")
  }

  @Test
  fun fadeOutEndsSilent() {
    val ir = SyntheticAudio.fadeOut(durationMs = 500)
    // Last few samples should be near zero
    val last100 = ir.samples.data.takeLast(100).map { kotlin.math.abs(it) }.average()
    assertTrue(last100 < 0.05, "Fade-out end should be near silent, avg=$last100")
  }

  @Test
  fun multiToneContainsEnergy() {
    val ir = SyntheticAudio.multiTone(durationMs = 300, frequencies = listOf(440f, 880f, 1320f))
    assertNotSilent(ir)
    assertNoClipping(ir)
  }

  @Test
  fun impulseHasTransient() {
    val ir = SyntheticAudio.impulse(durationMs = 200)
    assertNotSilent(ir)
    // Most samples should be silent except the click region
    val aboveThreshold = ir.samples.data.count { kotlin.math.abs(it) > 0.5f }
    assertTrue(aboveThreshold < ir.samples.data.size / 2, "Impulse should be mostly silent")
  }

  @Test
  fun staircaseHasSteps() {
    val ir = SyntheticAudio.staircase(durationMs = 500, steps = 8)
    assertNotSilent(ir)
    // Verify we have distinct amplitude levels
    val uniqueLevels = ir.samples.data.map { (it * 100).toInt() }.toSet()
    assertTrue(uniqueLevels.size >= 4, "Staircase should have multiple distinct levels, got ${uniqueLevels.size}")
  }
}
