@file:Suppress("MagicNumber")

package dev.transmute.testing.audio

import dev.transmute.audio.AudioIR
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Audio measurement and assertion utilities for test validation.
 *
 * Provides both raw measurement functions (returning numeric values for
 * custom assertions) and convenience `assert*` functions that throw
 * [AssertionError] with descriptive messages on failure.
 *
 * ### Quick start
 * ```kotlin
 * val decoded = codec.decode(encoded, options, ctx)
 * AudioAssertions.assertNotSilent(decoded)
 * AudioAssertions.assertRoundTripPlausible(original, decoded)
 * ```
 */
object AudioAssertions {

  // ---
  // Measurements
  // ---

  /**
   * Peak absolute sample value across all channels.
   *
   * Returns 0 for empty audio. Useful for silence detection and clipping checks.
   */
  fun peakAmplitude(ir: AudioIR): Float =
    ir.samples.data.maxOfOrNull { abs(it) } ?: 0f

  /**
   * Root-mean-square (RMS) level across all samples.
   *
   * Returns 0 for empty audio. A common loudness proxy - useful for verifying
   * that lossy codecs don't drastically alter perceived volume.
   */
  fun rms(ir: AudioIR): Float {
    val data = ir.samples.data
    if (data.isEmpty()) return 0f
    val sumSquares = data.sumOf { (it * it).toDouble() }
    return sqrt(sumSquares / data.size).toFloat()
  }

  /**
   * Energy ratio between two [AudioIR]s (decoded / original).
   *
   * Values close to 1.0 indicate good energy preservation. Values significantly
   * below 1.0 indicate energy loss (e.g. aggressive lossy compression).
   *
   * Returns [Float.NaN] if the original has zero energy.
   */
  fun energyRatio(original: AudioIR, decoded: AudioIR): Float {
    val origEnergy = original.samples.data.sumOf { (it * it).toDouble() }
    if (origEnergy == 0.0) return Float.NaN
    val decodedEnergy = decoded.samples.data.sumOf { (it * it).toDouble() }
    return (decodedEnergy / origEnergy).toFloat()
  }

  /**
   * Mean absolute error between two [AudioIR]s, sample by sample.
   *
   * Compares up to `min(a.size, b.size)` samples. Returns 0 if both are empty.
   * For lossless codecs this should be close to 0; for lossy codecs the value
   * depends on codec settings.
   */
  fun meanAbsoluteError(a: AudioIR, b: AudioIR): Float {
    val aData = a.samples.data
    val bData = b.samples.data
    val count = minOf(aData.size, bData.size)
    if (count == 0) return 0f
    var total = 0.0
    for (i in 0 until count) {
      total += abs(aData[i] - bData[i])
    }
    return (total / count).toFloat()
  }

  // ---
  // Assertion helpers
  // ---

  /**
   * Asserts that the audio duration is within +/-[toleranceMs] of [expectedMs].
   */
  fun assertDurationNear(ir: AudioIR, expectedMs: Long, toleranceMs: Long = 50) {
    val actual = ir.durationMs
    check(abs(actual - expectedMs) <= toleranceMs) {
      "Duration $actual ms is not within ±${toleranceMs}ms of expected $expectedMs ms"
    }
  }

  /**
   * Asserts that the sample rate matches exactly.
   */
  fun assertSampleRate(ir: AudioIR, expectedRate: Int) {
    check(ir.sampleRate == expectedRate) {
      "Sample rate ${ir.sampleRate} does not match expected $expectedRate"
    }
  }

  /**
   * Asserts that the channel count matches.
   */
  fun assertChannelCount(ir: AudioIR, expectedCount: Int) {
    check(ir.channelCount == expectedCount) {
      "Channel count ${ir.channelCount} does not match expected $expectedCount"
    }
  }

  /**
   * Asserts that the audio contains non-silent samples.
   *
   * @param minPeak Minimum peak amplitude to consider non-silent.
   */
  fun assertNotSilent(ir: AudioIR, minPeak: Float = 0.01f) {
    val peak = peakAmplitude(ir)
    check(peak > minPeak) {
      "Audio is silent: peak amplitude $peak ≤ threshold $minPeak"
    }
  }

  /**
   * Asserts that the audio is silent (or near-silent).
   *
   * @param maxPeak Maximum peak amplitude to consider silent.
   */
  fun assertSilent(ir: AudioIR, maxPeak: Float = 0.001f) {
    val peak = peakAmplitude(ir)
    check(peak <= maxPeak) {
      "Audio is not silent: peak amplitude $peak > threshold $maxPeak"
    }
  }

  /**
   * Asserts that the audio has a minimum number of samples.
   *
   * Useful for verifying that a decode didn't produce truncated output.
   */
  fun assertMinSamples(ir: AudioIR, minCount: Int) {
    check(ir.samples.data.size >= minCount) {
      "Audio has ${ir.samples.data.size} samples, expected at least $minCount"
    }
  }

  /**
   * Asserts that the RMS level is within an expected range.
   *
   * Useful for verifying that lossy codecs don't drastically change loudness.
   */
  fun assertRmsInRange(ir: AudioIR, minRms: Float, maxRms: Float) {
    val actual = rms(ir)
    check(actual in minRms..maxRms) {
      "RMS $actual is outside expected range [$minRms, $maxRms]"
    }
  }

  /**
   * Asserts that all sample values are within the valid PCM range [-1.0, 1.0].
   *
   * Detects clipping or overflow bugs in codecs/transforms.
   */
  fun assertNoClipping(ir: AudioIR) {
    for (i in ir.samples.data.indices) {
      val v = ir.samples.data[i]
      check(v in -1.0f..1.0f) {
        "Sample[$i] = $v is outside valid PCM range [-1.0, 1.0]"
      }
    }
  }

  /**
   * Asserts that a decoded [AudioIR] is a plausible round-trip result of [original].
   *
   * Checks: matching sample rate, matching channel count, and duration within tolerance.
   * Does **not** compare sample data (lossy codecs change it).
   */
  fun assertRoundTripPlausible(
    original: AudioIR,
    decoded: AudioIR,
    durationToleranceMs: Long = 100,
  ) {
    assertSampleRate(decoded, original.sampleRate)
    assertChannelCount(decoded, original.channelCount)
    assertDurationNear(decoded, original.durationMs, durationToleranceMs)
    assertNotSilent(decoded)
  }

  /**
   * Asserts that a decoded [AudioIR] has high fidelity relative to [original].
   *
   * Performs [assertRoundTripPlausible] checks plus verifies that the
   * [energyRatio] is within the given bounds (default: 0.5-2.0, i.e. within
   * a factor of 2).
   */
  fun assertHighFidelity(
    original: AudioIR,
    decoded: AudioIR,
    durationToleranceMs: Long = 100,
    minEnergyRatio: Float = 0.5f,
    maxEnergyRatio: Float = 2.0f,
  ) {
    assertRoundTripPlausible(original, decoded, durationToleranceMs)
    val ratio = energyRatio(original, decoded)
    check(!ratio.isNaN()) { "Original has zero energy — cannot compute fidelity ratio" }
    check(ratio in minEnergyRatio..maxEnergyRatio) {
      "Energy ratio $ratio is outside expected range [$minEnergyRatio, $maxEnergyRatio]"
    }
  }
}
