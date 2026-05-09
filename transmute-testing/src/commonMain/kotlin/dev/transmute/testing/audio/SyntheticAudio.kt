@file:Suppress("MagicNumber")

package dev.transmute.testing.audio

import dev.transmute.audio.AudioIR
import dev.transmute.audio.AudioMetadata
import dev.transmute.audio.AudioSamples
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Convenience shortcuts for generating synthetic [AudioIR] instances.
 *
 * Each function creates a fully-formed [AudioIR] with a single call.
 * For more flexible, composable audio generation - mixing, sequencing,
 * envelopes, and custom per-sample generators - use the **audio DSL**:
 *
 * ```kotlin
 * import dev.transmute.testing.dsl.*
 *
 * val audio = syntheticAudio {
 *     duration = 1.seconds
 *     mix {
 *         sine(440.hz, amplitude = 0.5f)
 *         sine(880.hz, amplitude = 0.3f)
 *     }
 *     adsr { attack = 50.ms; decay = 100.ms; sustain = 0.7f; release = 200.ms }
 * }
 * ```
 *
 * ### Quick start (static helpers)
 * ```kotlin
 * val tone  = SyntheticAudio.sineWave()                     // 440 Hz, 1 s, mono
 * val sweep = SyntheticAudio.chirp(startHz = 100f, endHz = 8000f)
 * val noise = SyntheticAudio.whiteNoise(seed = 123L)
 * ```
 *
 * ### Design notes
 * - Amplitude is always in the range `[-1.0, 1.0]`, matching standard float PCM.
 * - Multi-channel signals duplicate the same waveform across channels unless
 *   noted otherwise (e.g. [stereoPingPong]).
 * - Duration is specified in milliseconds for consistency with the Transmute IR API.
 *
 * @see dev.transmute.testing.dsl.syntheticAudio
 */
object SyntheticAudio {

  // ---
  // Basic waveforms
  // ---

  /**
   * Pure sine wave at the given [frequency].
   *
   * Default: 440 Hz (concert A4), 1 second, 44100 Hz, mono, half-amplitude.
   * Useful for basic encode/decode round-trip and sample-rate preservation tests.
   */
  fun sineWave(
    frequency: Float = 440f,
    durationMs: Long = 1000,
    sampleRate: Int = 44100,
    amplitude: Float = 0.5f,
    channelCount: Int = 1,
  ): AudioIR {
    val samples = generateSamples(durationMs, sampleRate, channelCount) { t, _ ->
      amplitude * sin(2.0f * PI.toFloat() * frequency * t)
    }
    return audioIR(samples, sampleRate, channelCount, durationMs)
  }

  /**
   * Square wave at the given [frequency].
   *
   * Alternates between +[amplitude] and -[amplitude] at [frequency] Hz.
   * Rich in odd harmonics - useful for testing how codecs handle steep
   * transients and high-frequency content.
   */
  fun squareWave(
    frequency: Float = 440f,
    durationMs: Long = 1000,
    sampleRate: Int = 44100,
    amplitude: Float = 0.5f,
    channelCount: Int = 1,
  ): AudioIR {
    val samples = generateSamples(durationMs, sampleRate, channelCount) { t, _ ->
      val phase = (frequency * t) % 1.0f
      if (phase < 0.5f) amplitude else -amplitude
    }
    return audioIR(samples, sampleRate, channelCount, durationMs)
  }

  /**
   * Sawtooth wave at the given [frequency].
   *
   * Linear ramp from -[amplitude] to +[amplitude] per cycle.
   * Contains all harmonics - useful for testing spectral preservation.
   */
  fun sawtoothWave(
    frequency: Float = 440f,
    durationMs: Long = 1000,
    sampleRate: Int = 44100,
    amplitude: Float = 0.5f,
    channelCount: Int = 1,
  ): AudioIR {
    val samples = generateSamples(durationMs, sampleRate, channelCount) { t, _ ->
      val phase = (frequency * t) % 1.0f
      amplitude * (2.0f * phase - 1.0f)
    }
    return audioIR(samples, sampleRate, channelCount, durationMs)
  }

  /**
   * Triangle wave at the given [frequency].
   *
   * Smooth ramp between -[amplitude] and +[amplitude].
   * Contains only odd harmonics that roll off quickly - useful for testing
   * gentle waveforms through lossy codecs.
   */
  fun triangleWave(
    frequency: Float = 440f,
    durationMs: Long = 1000,
    sampleRate: Int = 44100,
    amplitude: Float = 0.5f,
    channelCount: Int = 1,
  ): AudioIR {
    val samples = generateSamples(durationMs, sampleRate, channelCount) { t, _ ->
      val phase = (frequency * t) % 1.0f
      amplitude * (4.0f * abs(phase - 0.5f) - 1.0f)
    }
    return audioIR(samples, sampleRate, channelCount, durationMs)
  }

  // ---
  // Silence & noise
  // ---

  /**
   * Digital silence (all samples are 0.0).
   *
   * Useful for verifying that codecs don't inject artefacts into silent regions,
   * and for duration-preservation checks.
   */
  fun silence(
    durationMs: Long = 1000,
    sampleRate: Int = 44100,
    channelCount: Int = 1,
  ): AudioIR {
    val totalSamples = sampleCount(durationMs, sampleRate, channelCount)
    return audioIR(
      AudioSamples(FloatArray(totalSamples), sampleRate, channelCount),
      sampleRate,
      channelCount,
      durationMs,
    )
  }

  /**
   * Pseudo-random white noise.
   *
   * Covers all frequencies equally - useful for testing that lossy codecs
   * don't introduce unexpected tonal artefacts and for worst-case compression
   * (incompressible data).
   *
   * @param seed Deterministic RNG seed for reproducible test runs.
   */
  fun whiteNoise(
    durationMs: Long = 1000,
    sampleRate: Int = 44100,
    amplitude: Float = 0.3f,
    channelCount: Int = 1,
    seed: Long = 42L,
  ): AudioIR {
    val rng = Random(seed)
    val totalSamples = sampleCount(durationMs, sampleRate, channelCount)
    val data = FloatArray(totalSamples) { amplitude * (rng.nextFloat() * 2.0f - 1.0f) }
    return audioIR(AudioSamples(data, sampleRate, channelCount), sampleRate, channelCount, durationMs)
  }

  /**
   * Pink noise approximation (1/f spectrum).
   *
   * Energy per octave is roughly constant, making it more representative of
   * natural audio content than white noise. Uses a simple Voss-McCartney
   * algorithm approximation.
   *
   * @param seed Deterministic RNG seed for reproducible test runs.
   */
  fun pinkNoise(
    durationMs: Long = 1000,
    sampleRate: Int = 44100,
    amplitude: Float = 0.3f,
    channelCount: Int = 1,
    seed: Long = 42L,
  ): AudioIR {
    val rng = Random(seed)
    val totalSamples = sampleCount(durationMs, sampleRate, channelCount)
    val monoSamples = totalSamples / channelCount

    // Voss-McCartney pink noise with 8 rows
    val rows = 8
    val rowValues = FloatArray(rows)
    val monoData = FloatArray(monoSamples)
    var runningSum = 0f
    for (i in rowValues.indices) {
      rowValues[i] = rng.nextFloat() * 2f - 1f
      runningSum += rowValues[i]
    }

    for (i in 0 until monoSamples) {
      // Update one row per sample (determined by trailing zeros of index)
      val trailingZeros = if (i == 0) 0 else i.countTrailingZeroBits() % rows
      runningSum -= rowValues[trailingZeros]
      rowValues[trailingZeros] = rng.nextFloat() * 2f - 1f
      runningSum += rowValues[trailingZeros]
      monoData[i] = (amplitude * runningSum / rows).coerceIn(-1f, 1f)
    }

    // Spread to channels
    val data = if (channelCount == 1) {
      monoData
    } else {
      FloatArray(totalSamples) { monoData[it / channelCount] }
    }
    return audioIR(AudioSamples(data, sampleRate, channelCount), sampleRate, channelCount, durationMs)
  }

  // ---
  // Frequency sweeps & composites
  // ---

  /**
   * Linear frequency sweep (chirp) from [startHz] to [endHz].
   *
   * Exercises codec behaviour across the audible spectrum.
   * Useful for detecting frequency-dependent artefacts and verifying
   * that codecs handle the full bandwidth.
   */
  fun chirp(
    startHz: Float = 20f,
    endHz: Float = 20_000f,
    durationMs: Long = 2000,
    sampleRate: Int = 44100,
    amplitude: Float = 0.5f,
    channelCount: Int = 1,
  ): AudioIR {
    val durationSec = durationMs / 1000.0f
    val samples = generateSamples(durationMs, sampleRate, channelCount) { t, _ ->
      val instantFreq = startHz + (endHz - startHz) * (t / durationSec)
      amplitude * sin(2.0f * PI.toFloat() * instantFreq * t)
    }
    return audioIR(samples, sampleRate, channelCount, durationMs)
  }

  /**
   * Logarithmic frequency sweep from [startHz] to [endHz].
   *
   * Spends equal time per octave, which is more perceptually uniform
   * than a linear sweep. Preferred for fidelity testing.
   */
  fun logChirp(
    startHz: Float = 20f,
    endHz: Float = 20_000f,
    durationMs: Long = 2000,
    sampleRate: Int = 44100,
    amplitude: Float = 0.5f,
    channelCount: Int = 1,
  ): AudioIR {
    val durationSec = durationMs / 1000.0f
    val logRatio = kotlin.math.ln(endHz / startHz)
    val samples = generateSamples(durationMs, sampleRate, channelCount) { t, _ ->
      val phase = 2.0f * PI.toFloat() * startHz * durationSec *
        (kotlin.math.exp(logRatio * t / durationSec) - 1f) / logRatio
      amplitude * sin(phase)
    }
    return audioIR(samples, sampleRate, channelCount, durationMs)
  }

  /**
   * Composite of multiple sine waves at different frequencies.
   *
   * Useful for testing that codecs preserve harmonic relationships
   * (e.g. musical chords) and for verifying spectral fidelity.
   *
   * @param frequencies List of frequencies (Hz) to superimpose.
   *   Amplitude is divided equally among tones to avoid clipping.
   */
  fun multiTone(
    frequencies: List<Float> = listOf(440f, 880f, 1320f),
    durationMs: Long = 1000,
    sampleRate: Int = 44100,
    amplitude: Float = 0.5f,
    channelCount: Int = 1,
  ): AudioIR {
    val perTone = amplitude / frequencies.size
    val samples = generateSamples(durationMs, sampleRate, channelCount) { t, _ ->
      var sum = 0f
      for (freq in frequencies) {
        sum += perTone * sin(2.0f * PI.toFloat() * freq * t)
      }
      sum
    }
    return audioIR(samples, sampleRate, channelCount, durationMs)
  }

  // ---
  // Transients & envelopes
  // ---

  /**
   * Single impulse (click) followed by silence.
   *
   * Useful for testing transient response and timing accuracy. The click is
   * a single full-scale sample placed at [clickPositionMs].
   */
  fun impulse(
    clickPositionMs: Long = 0,
    durationMs: Long = 500,
    sampleRate: Int = 44100,
    channelCount: Int = 1,
  ): AudioIR {
    val totalSamples = sampleCount(durationMs, sampleRate, channelCount)
    val data = FloatArray(totalSamples)
    val clickSample = ((clickPositionMs * sampleRate) / 1000).toInt() * channelCount
    if (clickSample in data.indices) {
      for (ch in 0 until channelCount) {
        data[clickSample + ch] = 1.0f
      }
    }
    return audioIR(AudioSamples(data, sampleRate, channelCount), sampleRate, channelCount, durationMs)
  }

  /**
   * Sine wave with an ADSR (attack-decay-sustain-release) envelope.
   *
   * Simulates a simple synthesizer note - useful for testing that codecs
   * handle amplitude variations over time.
   *
   * @param attackMs   Time to ramp from 0 to peak.
   * @param decayMs    Time to ramp from peak to sustain level.
   * @param sustainLevel Amplitude during sustain (0.0-1.0 relative to peak).
   * @param releaseMs  Time to ramp from sustain to 0 at the end.
   */
  fun adsrTone(
    frequency: Float = 440f,
    durationMs: Long = 1000,
    sampleRate: Int = 44100,
    amplitude: Float = 0.8f,
    channelCount: Int = 1,
    attackMs: Long = 50,
    decayMs: Long = 100,
    sustainLevel: Float = 0.6f,
    releaseMs: Long = 150,
  ): AudioIR {
    val durationSec = durationMs / 1000.0f
    val attackSec = attackMs / 1000.0f
    val decaySec = decayMs / 1000.0f
    val releaseSec = releaseMs / 1000.0f
    val sustainStart = attackSec + decaySec
    val releaseStart = durationSec - releaseSec

    val samples = generateSamples(durationMs, sampleRate, channelCount) { t, _ ->
      val envelope = when {
        t < attackSec -> t / attackSec
        t < sustainStart -> 1.0f - (1.0f - sustainLevel) * ((t - attackSec) / decaySec)
        t < releaseStart -> sustainLevel
        else -> sustainLevel * (1.0f - (t - releaseStart) / releaseSec).coerceAtLeast(0f)
      }
      amplitude * envelope * sin(2.0f * PI.toFloat() * frequency * t)
    }
    return audioIR(samples, sampleRate, channelCount, durationMs)
  }

  /**
   * Sine wave that fades in from silence over the full duration.
   *
   * Useful for testing amplitude ramps and duration accuracy.
   */
  fun fadeIn(
    frequency: Float = 440f,
    durationMs: Long = 1000,
    sampleRate: Int = 44100,
    amplitude: Float = 0.5f,
    channelCount: Int = 1,
  ): AudioIR {
    val durationSec = durationMs / 1000.0f
    val samples = generateSamples(durationMs, sampleRate, channelCount) { t, _ ->
      val env = (t / durationSec).coerceIn(0f, 1f)
      amplitude * env * sin(2.0f * PI.toFloat() * frequency * t)
    }
    return audioIR(samples, sampleRate, channelCount, durationMs)
  }

  /**
   * Sine wave that fades out to silence over the full duration.
   */
  fun fadeOut(
    frequency: Float = 440f,
    durationMs: Long = 1000,
    sampleRate: Int = 44100,
    amplitude: Float = 0.5f,
    channelCount: Int = 1,
  ): AudioIR {
    val durationSec = durationMs / 1000.0f
    val samples = generateSamples(durationMs, sampleRate, channelCount) { t, _ ->
      val env = (1.0f - t / durationSec).coerceIn(0f, 1f)
      amplitude * env * sin(2.0f * PI.toFloat() * frequency * t)
    }
    return audioIR(samples, sampleRate, channelCount, durationMs)
  }

  // ---
  // Stereo & spatial
  // ---

  /**
   * Stereo ping-pong tone that alternates between left and right channels.
   *
   * Each half-period of the alternation plays the sine wave in one channel
   * and silence in the other. Useful for verifying that channel layout is
   * preserved through encode/decode cycles.
   *
   * @param alternateMs Duration of each L/R alternation window in milliseconds.
   */
  fun stereoPingPong(
    frequency: Float = 440f,
    durationMs: Long = 1000,
    sampleRate: Int = 44100,
    amplitude: Float = 0.5f,
    alternateMs: Long = 250,
  ): AudioIR {
    val channelCount = 2
    val totalSamples = sampleCount(durationMs, sampleRate, channelCount)
    val data = FloatArray(totalSamples)
    val alternateSamples = ((alternateMs * sampleRate) / 1000).toInt()
    val monoSamples = totalSamples / channelCount

    for (i in 0 until monoSamples) {
      val t = i.toFloat() / sampleRate
      val sample = amplitude * sin(2.0f * PI.toFloat() * frequency * t)
      val isLeft = (i / alternateSamples) % 2 == 0
      val leftIdx = i * 2
      val rightIdx = leftIdx + 1
      data[leftIdx] = if (isLeft) sample else 0f
      data[rightIdx] = if (isLeft) 0f else sample
    }
    return audioIR(AudioSamples(data, sampleRate, channelCount), sampleRate, channelCount, durationMs)
  }

  /**
   * Stereo sine wave with a constant pan position.
   *
   * @param pan Pan position from -1.0 (hard left) through 0.0 (center)
   *   to +1.0 (hard right). Uses constant-power panning law.
   */
  fun stereoPanned(
    frequency: Float = 440f,
    durationMs: Long = 1000,
    sampleRate: Int = 44100,
    amplitude: Float = 0.5f,
    pan: Float = 0f,
  ): AudioIR {
    val channelCount = 2
    val totalSamples = sampleCount(durationMs, sampleRate, channelCount)
    val data = FloatArray(totalSamples)
    val monoSamples = totalSamples / channelCount
    // Constant-power pan law
    val angle = (pan.coerceIn(-1f, 1f) + 1f) / 2f * (PI.toFloat() / 2f)
    val gainL = cos(angle)
    val gainR = sin(angle)

    for (i in 0 until monoSamples) {
      val t = i.toFloat() / sampleRate
      val sample = amplitude * sin(2.0f * PI.toFloat() * frequency * t)
      data[i * 2] = sample * gainL
      data[i * 2 + 1] = sample * gainR
    }
    return audioIR(AudioSamples(data, sampleRate, channelCount), sampleRate, channelCount, durationMs)
  }

  // ---
  // Utility generators
  // ---

  /**
   * DC offset signal - constant value for every sample.
   *
   * Useful for testing DC-blocking filters and offset handling.
   */
  fun dcOffset(
    offset: Float = 0.5f,
    durationMs: Long = 1000,
    sampleRate: Int = 44100,
    channelCount: Int = 1,
  ): AudioIR {
    val totalSamples = sampleCount(durationMs, sampleRate, channelCount)
    val data = FloatArray(totalSamples) { offset }
    return audioIR(AudioSamples(data, sampleRate, channelCount), sampleRate, channelCount, durationMs)
  }

  /**
   * Full-scale signal (alternating +1.0 / -1.0 at Nyquist).
   *
   * Useful for testing clipping behavior and peak handling.
   */
  fun fullScale(
    durationMs: Long = 500,
    sampleRate: Int = 44100,
    channelCount: Int = 1,
  ): AudioIR {
    val totalSamples = sampleCount(durationMs, sampleRate, channelCount)
    val data = FloatArray(totalSamples) { if ((it / channelCount) % 2 == 0) 1.0f else -1.0f }
    return audioIR(AudioSamples(data, sampleRate, channelCount), sampleRate, channelCount, durationMs)
  }

  /**
   * Staircase signal that increments in discrete steps.
   *
   * Useful for testing bit-depth preservation - each step maps to a
   * quantization level.
   *
   * @param steps Number of distinct amplitude levels.
   */
  fun staircase(
    steps: Int = 16,
    durationMs: Long = 1000,
    sampleRate: Int = 44100,
    channelCount: Int = 1,
  ): AudioIR {
    val totalSamples = sampleCount(durationMs, sampleRate, channelCount)
    val monoSamples = totalSamples / channelCount
    val samplesPerStep = monoSamples / steps
    val data = FloatArray(totalSamples)
    for (i in 0 until monoSamples) {
      val step = (i / samplesPerStep.coerceAtLeast(1)).coerceAtMost(steps - 1)
      val value = -1.0f + 2.0f * step / (steps - 1).coerceAtLeast(1)
      for (ch in 0 until channelCount) {
        data[i * channelCount + ch] = value
      }
    }
    return audioIR(AudioSamples(data, sampleRate, channelCount), sampleRate, channelCount, durationMs)
  }

  // ---
  // Internal helpers
  // ---

  private fun sampleCount(durationMs: Long, sampleRate: Int, channelCount: Int): Int =
    ((durationMs * sampleRate * channelCount) / 1000).toInt()

  /**
   * Generates interleaved PCM samples using a per-sample generator function.
   *
   * @param generator Receives `(t: Float, sampleIndex: Int)` where `t` is
   *   the time in seconds and `sampleIndex` is the mono sample index.
   *   Returns the sample value for that point in time. The same value
   *   is written to all channels.
   */
  private inline fun generateSamples(
    durationMs: Long,
    sampleRate: Int,
    channelCount: Int,
    generator: (t: Float, sampleIndex: Int) -> Float,
  ): AudioSamples {
    val totalSamples = sampleCount(durationMs, sampleRate, channelCount)
    val monoSamples = totalSamples / channelCount
    val data = FloatArray(totalSamples)
    for (i in 0 until monoSamples) {
      val t = i.toFloat() / sampleRate
      val sample = generator(t, i)
      for (ch in 0 until channelCount) {
        data[i * channelCount + ch] = sample
      }
    }
    return AudioSamples(data, sampleRate, channelCount)
  }

  private fun audioIR(
    samples: AudioSamples,
    sampleRate: Int,
    channelCount: Int,
    durationMs: Long,
    metadata: AudioMetadata = AudioMetadata(),
  ): AudioIR = AudioIR(
    samples = samples,
    sampleRate = sampleRate,
    channelCount = channelCount,
    durationMs = durationMs,
    metadata = metadata,
  )
}
