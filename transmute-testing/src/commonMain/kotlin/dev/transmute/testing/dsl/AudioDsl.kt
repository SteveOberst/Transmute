@file:Suppress("MagicNumber", "TooManyFunctions")

package dev.transmute.testing.dsl

import dev.transmute.audio.AudioIR
import dev.transmute.audio.AudioMetadata
import dev.transmute.audio.AudioSamples
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sin
import kotlin.random.Random

// ===
//  Entry point
// ===

/**
 * Build a synthetic [AudioIR] using the audio DSL.
 *
 * ```kotlin
 * // Simple sine tone
 * val tone = syntheticAudio {
 *     duration = 1.seconds
 *     sine(440.hz)
 * }
 *
 * // Multi-tone mix with envelope
 * val chord = syntheticAudio {
 *     duration = 2.seconds
 *     mix {
 *         sine(440.hz, amplitude = 0.4f)
 *         sine(554.hz, amplitude = 0.3f)
 *         sine(659.hz, amplitude = 0.3f)
 *     }
 *     adsr { attack = 80.ms; decay = 120.ms; sustain = 0.6f; release = 300.ms }
 *     fadeOut(200.ms)
 * }
 *
 * // Temporal sequence
 * val melody = syntheticAudio {
 *     sampleRate = 48000
 *     sequence {
 *         segment(250.ms) { sine(440.hz) }
 *         segment(250.ms) { sine(554.hz) }
 *         segment(250.ms) { sine(659.hz) }
 *         segment(250.ms) { silence() }
 *     }
 * }
 *
 * // Custom per-sample generator
 * val custom = syntheticAudio {
 *     duration = 500.ms
 *     generate { index, sr ->
 *         sin(2.0 * PI * 440.0 * index / sr).toFloat() * 0.5f
 *     }
 * }
 *
 * // Stereo with pan
 * val panned = syntheticAudio {
 *     duration = 1.seconds
 *     sine(440.hz)
 *     stereo { pan = 0.7f }
 * }
 * ```
 */
fun syntheticAudio(block: AudioScope.() -> Unit): AudioIR =
  AudioScope().apply(block).build()

// ===
//  Root scope
// ===

@SyntheticMediaDsl
class AudioScope {
  /** Sample rate in Hz (default 44 100). */
  var sampleRate: Int = 44100

  /** Channel count - 1 = mono, 2 = stereo (default 1). */
  var channels: Int = 1

  /** Total duration in milliseconds (default 1 000). Use [Int.seconds] / [Int.ms] helpers. */
  var duration: Long = 1000L

  // --- internal bookkeeping ---
  internal var signal: SignalNode = SilenceNode
  internal val effects = mutableListOf<EffectNode>()

  // --- Waveforms ---

  /** Pure sine wave. */
  fun sine(frequencyHz: Double = 440.0, amplitude: Float = 0.8f, phase: Float = 0f) {
    signal = SineNode(frequencyHz, amplitude, phase)
  }

  /** Square wave (rich in odd harmonics). */
  fun square(frequencyHz: Double = 440.0, amplitude: Float = 0.8f) {
    signal = SquareNode(frequencyHz, amplitude)
  }

  /** Sawtooth wave (all harmonics). */
  fun sawtooth(frequencyHz: Double = 440.0, amplitude: Float = 0.8f) {
    signal = SawtoothNode(frequencyHz, amplitude)
  }

  /** Triangle wave (odd harmonics, -6 dB/octave roll-off). */
  fun triangle(frequencyHz: Double = 440.0, amplitude: Float = 0.8f) {
    signal = TriangleNode(frequencyHz, amplitude)
  }

  /** Digital silence (all zeros). */
  fun silence() {
    signal = SilenceNode
  }

  /** Constant DC offset. */
  fun dc(level: Float) {
    signal = DcNode(level)
  }

  // --- Noise ---

  /** White noise (uniform spectral density). */
  fun whiteNoise(amplitude: Float = 0.5f, seed: Long = 42L) {
    signal = WhiteNoiseNode(amplitude, seed)
  }

  /** Pink noise (1/f spectral density). */
  fun pinkNoise(amplitude: Float = 0.5f, seed: Long = 42L) {
    signal = PinkNoiseNode(amplitude, seed)
  }

  // --- Sweeps ---

  /** Frequency sweep (chirp). */
  fun chirp(
    startHz: Double = 20.0,
    endHz: Double = 20_000.0,
    amplitude: Float = 0.8f,
    sweep: SweepType = SweepType.LINEAR,
  ) {
    signal = ChirpNode(startHz, endHz, amplitude, sweep)
  }

  // --- Impulse ---

  /** Single-sample impulse at [positionMs]. */
  fun impulse(positionMs: Long = 0, amplitude: Float = 1f) {
    signal = ImpulseNode(positionMs, amplitude)
  }

  // --- Composition ---

  /** Additive mix of multiple signals (summed). */
  fun mix(block: MixScope.() -> Unit) {
    signal = MixNode(MixScope().apply(block).signals.toList())
  }

  /** Temporal concatenation of segments - duration is summed automatically. */
  fun sequence(block: SequenceScope.() -> Unit) {
    val scope = SequenceScope().apply(block)
    signal = SequenceNode(scope.segments.toList())
    duration = scope.segments.sumOf { it.durationMs }
  }

  /** Arbitrary per-sample generator: `(sampleIndex, sampleRate) -> Float`. */
  fun generate(block: (sampleIndex: Int, sampleRate: Int) -> Float) {
    signal = CustomNode(block)
  }

  // --- Effects ---

  /** Linear fade-in from silence at the start. */
  fun fadeIn(ms: Long) {
    effects += FadeInNode(ms)
  }

  /** Linear fade-out to silence at the end. */
  fun fadeOut(ms: Long) {
    effects += FadeOutNode(ms)
  }

  /** Multiply all samples by [factor]. */
  fun amplify(factor: Float) {
    effects += AmplifyNode(factor)
  }

  /** Normalize peak amplitude to [ceiling] (0-1). */
  fun normalize(ceiling: Float = 1f) {
    effects += NormalizeNode(ceiling)
  }

  /** Apply an ADSR (attack-decay-sustain-release) envelope. */
  fun adsr(block: AdsrScope.() -> Unit) {
    val env = AdsrScope().apply(block)
    effects += AdsrNode(env.attack, env.decay, env.sustain, env.release)
  }

  // --- Stereo ---

  /**
   * Enable stereo and set a constant pan position.
   *
   * Sets [channels] to 2 and applies constant-power panning.
   * `pan` ranges from -1.0 (hard left) to +1.0 (hard right); 0.0 = centre.
   */
  fun stereo(block: StereoScope.() -> Unit) {
    channels = 2
    val scope = StereoScope().apply(block)
    effects += StereoPanNode(scope.pan)
  }

  // --- Build ---

  internal fun build(): AudioIR {
    val monoCount = (sampleRate.toLong() * duration / 1000).toInt()
    var mono = signal.render(monoCount, sampleRate)

    for (effect in effects) {
      mono = effect.apply(mono, sampleRate, duration)
    }

    val finalSamples = if (channels <= 1) {
      mono
    } else {
      // Expand mono -> interleaved multi-channel (unless stereo pan node already handled it)
      val hasStereoPan = effects.any { it is StereoPanNode }
      if (hasStereoPan) mono else FloatArray(monoCount * channels) { mono[it / channels] }
    }

    val channelCount = if (effects.any { it is StereoPanNode }) 2 else channels
    return AudioIR(
      samples = AudioSamples(finalSamples, sampleRate, channelCount),
      sampleRate = sampleRate,
      channelCount = channelCount,
      durationMs = duration,
    )
  }
}

// ===
//  Sub-scopes
// ===

/** Scope for [AudioScope.mix]. Each call adds a signal to be summed. */
@SyntheticMediaDsl
class MixScope {
  internal val signals = mutableListOf<SignalNode>()

  fun sine(frequencyHz: Double = 440.0, amplitude: Float = 0.3f, phase: Float = 0f) {
    signals += SineNode(frequencyHz, amplitude, phase)
  }
  fun square(frequencyHz: Double = 440.0, amplitude: Float = 0.3f) {
    signals += SquareNode(frequencyHz, amplitude)
  }
  fun sawtooth(frequencyHz: Double = 440.0, amplitude: Float = 0.3f) {
    signals += SawtoothNode(frequencyHz, amplitude)
  }
  fun triangle(frequencyHz: Double = 440.0, amplitude: Float = 0.3f) {
    signals += TriangleNode(frequencyHz, amplitude)
  }
  fun whiteNoise(amplitude: Float = 0.2f, seed: Long = 42L) {
    signals += WhiteNoiseNode(amplitude, seed)
  }
  fun pinkNoise(amplitude: Float = 0.2f, seed: Long = 42L) {
    signals += PinkNoiseNode(amplitude, seed)
  }
  fun dc(level: Float) { signals += DcNode(level) }
  fun silence() { signals += SilenceNode }
  fun generate(block: (sampleIndex: Int, sampleRate: Int) -> Float) {
    signals += CustomNode(block)
  }
}

/** Scope for [AudioScope.sequence]. Each [segment] is rendered and concatenated. */
@SyntheticMediaDsl
class SequenceScope {
  internal val segments = mutableListOf<SequenceSegment>()

  /** Append a segment of [durationMs] rendered with its own audio scope. */
  fun segment(durationMs: Long, block: AudioScope.() -> Unit) {
    val scope = AudioScope().apply { duration = durationMs; block() }
    segments += SequenceSegment(durationMs, scope.signal)
  }
}

/** Scope for the ADSR envelope. */
@SyntheticMediaDsl
class AdsrScope {
  /** Attack time in ms (ramp 0 -> peak). */
  var attack: Long = 50L
  /** Decay time in ms (ramp peak -> [sustain]). */
  var decay: Long = 100L
  /** Sustain level (0-1) relative to peak. */
  var sustain: Float = 0.7f
  /** Release time in ms (ramp sustain -> 0, applied at end). */
  var release: Long = 150L
}

/** Scope for stereo configuration. */
@SyntheticMediaDsl
class StereoScope {
  /** Pan position: -1.0 (hard left) ... 0.0 (centre) ... +1.0 (hard right). */
  var pan: Float = 0f
}

// ===
//  Signal graph nodes (internal)
// ===

internal sealed interface SignalNode {
  fun render(sampleCount: Int, sampleRate: Int): FloatArray
}

internal data object SilenceNode : SignalNode {
  override fun render(sampleCount: Int, sampleRate: Int) = FloatArray(sampleCount)
}

internal data class SineNode(val hz: Double, val amp: Float, val phase: Float) : SignalNode {
  override fun render(sampleCount: Int, sampleRate: Int): FloatArray {
    val out = FloatArray(sampleCount)
    val twoPi = 2.0 * PI
    for (i in 0 until sampleCount) {
      out[i] = (amp * sin(twoPi * hz * i / sampleRate + phase)).toFloat()
    }
    return out
  }
}

internal data class SquareNode(val hz: Double, val amp: Float) : SignalNode {
  override fun render(sampleCount: Int, sampleRate: Int): FloatArray {
    val out = FloatArray(sampleCount)
    for (i in 0 until sampleCount) {
      val phase = (hz * i / sampleRate) % 1.0
      out[i] = if (phase < 0.5) amp else -amp
    }
    return out
  }
}

internal data class SawtoothNode(val hz: Double, val amp: Float) : SignalNode {
  override fun render(sampleCount: Int, sampleRate: Int): FloatArray {
    val out = FloatArray(sampleCount)
    for (i in 0 until sampleCount) {
      val phase = (hz * i / sampleRate) % 1.0
      out[i] = (amp * (2.0 * phase - 1.0)).toFloat()
    }
    return out
  }
}

internal data class TriangleNode(val hz: Double, val amp: Float) : SignalNode {
  override fun render(sampleCount: Int, sampleRate: Int): FloatArray {
    val out = FloatArray(sampleCount)
    for (i in 0 until sampleCount) {
      val phase = (hz * i / sampleRate) % 1.0
      out[i] = (amp * (2.0 * kotlin.math.abs(2.0 * phase - 1.0) - 1.0)).toFloat()
    }
    return out
  }
}

internal data class DcNode(val level: Float) : SignalNode {
  override fun render(sampleCount: Int, sampleRate: Int) = FloatArray(sampleCount) { level }
}

internal data class WhiteNoiseNode(val amp: Float, val seed: Long) : SignalNode {
  override fun render(sampleCount: Int, sampleRate: Int): FloatArray {
    val rng = Random(seed)
    return FloatArray(sampleCount) { amp * (rng.nextFloat() * 2f - 1f) }
  }
}

internal data class PinkNoiseNode(val amp: Float, val seed: Long) : SignalNode {
  override fun render(sampleCount: Int, sampleRate: Int): FloatArray {
    val rng = Random(seed)
    val rows = 8
    val rv = FloatArray(rows) { rng.nextFloat() * 2f - 1f }
    var sum = rv.sum()
    val out = FloatArray(sampleCount)
    for (i in 0 until sampleCount) {
      val row = if (i == 0) 0 else i.countTrailingZeroBits() % rows
      sum -= rv[row]
      rv[row] = rng.nextFloat() * 2f - 1f
      sum += rv[row]
      out[i] = (amp * sum / rows).coerceIn(-1f, 1f)
    }
    return out
  }
}

internal data class ChirpNode(
  val startHz: Double,
  val endHz: Double,
  val amp: Float,
  val sweep: SweepType,
) : SignalNode {
  override fun render(sampleCount: Int, sampleRate: Int): FloatArray {
    val out = FloatArray(sampleCount)
    val dur = sampleCount.toDouble() / sampleRate
    val twoPi = 2.0 * PI
    when (sweep) {
      SweepType.LINEAR -> {
        for (i in 0 until sampleCount) {
          val t = i.toDouble() / sampleRate
          val freq = startHz + (endHz - startHz) * (t / dur)
          out[i] = (amp * sin(twoPi * freq * t)).toFloat()
        }
      }
      SweepType.LOGARITHMIC -> {
        val logRatio = ln(endHz / startHz)
        for (i in 0 until sampleCount) {
          val t = i.toDouble() / sampleRate
          val phase = twoPi * startHz * dur * (exp(logRatio * t / dur) - 1.0) / logRatio
          out[i] = (amp * sin(phase)).toFloat()
        }
      }
    }
    return out
  }
}

internal data class ImpulseNode(val posMs: Long, val amp: Float) : SignalNode {
  override fun render(sampleCount: Int, sampleRate: Int): FloatArray {
    val out = FloatArray(sampleCount)
    val idx = (posMs * sampleRate / 1000).toInt()
    if (idx in out.indices) out[idx] = amp
    return out
  }
}

internal data class MixNode(val children: List<SignalNode>) : SignalNode {
  override fun render(sampleCount: Int, sampleRate: Int): FloatArray {
    val out = FloatArray(sampleCount)
    for (child in children) {
      val buf = child.render(sampleCount, sampleRate)
      for (i in out.indices) out[i] += buf[i]
    }
    return out
  }
}

internal data class SequenceSegment(val durationMs: Long, val signal: SignalNode)

internal data class SequenceNode(val segments: List<SequenceSegment>) : SignalNode {
  override fun render(sampleCount: Int, sampleRate: Int): FloatArray {
    val parts = mutableListOf<FloatArray>()
    for (seg in segments) {
      val n = (seg.durationMs * sampleRate / 1000).toInt()
      parts += seg.signal.render(n, sampleRate)
    }
    val total = parts.sumOf { it.size }
    val out = FloatArray(total)
    var off = 0
    for (part in parts) {
      part.copyInto(out, off)
      off += part.size
    }
    return out
  }
}

internal data class CustomNode(val gen: (Int, Int) -> Float) : SignalNode {
  override fun render(sampleCount: Int, sampleRate: Int) =
    FloatArray(sampleCount) { gen(it, sampleRate) }
}

// ===
//  Effect nodes (internal)
// ===

internal sealed interface EffectNode {
  fun apply(samples: FloatArray, sampleRate: Int, durationMs: Long): FloatArray
}

internal data class FadeInNode(val ms: Long) : EffectNode {
  override fun apply(samples: FloatArray, sampleRate: Int, durationMs: Long): FloatArray {
    val n = (ms * sampleRate / 1000).toInt().coerceAtMost(samples.size)
    for (i in 0 until n) samples[i] *= i.toFloat() / n
    return samples
  }
}

internal data class FadeOutNode(val ms: Long) : EffectNode {
  override fun apply(samples: FloatArray, sampleRate: Int, durationMs: Long): FloatArray {
    val n = (ms * sampleRate / 1000).toInt().coerceAtMost(samples.size)
    val start = samples.size - n
    for (i in start until samples.size) {
      samples[i] *= 1f - (i - start).toFloat() / n
    }
    return samples
  }
}

internal data class AmplifyNode(val factor: Float) : EffectNode {
  override fun apply(samples: FloatArray, sampleRate: Int, durationMs: Long): FloatArray {
    for (i in samples.indices) samples[i] *= factor
    return samples
  }
}

internal data class NormalizeNode(val ceiling: Float) : EffectNode {
  override fun apply(samples: FloatArray, sampleRate: Int, durationMs: Long): FloatArray {
    val peak = samples.maxOfOrNull { kotlin.math.abs(it) } ?: return samples
    if (peak == 0f) return samples
    val scale = ceiling / peak
    for (i in samples.indices) samples[i] *= scale
    return samples
  }
}

internal data class AdsrNode(
  val attackMs: Long,
  val decayMs: Long,
  val sustainLevel: Float,
  val releaseMs: Long,
) : EffectNode {
  override fun apply(samples: FloatArray, sampleRate: Int, durationMs: Long): FloatArray {
    val durSec = durationMs / 1000f
    val aSec = attackMs / 1000f
    val dSec = decayMs / 1000f
    val rSec = releaseMs / 1000f
    val susStart = aSec + dSec
    val relStart = durSec - rSec
    for (i in samples.indices) {
      val t = i.toFloat() / sampleRate
      val env = when {
        t < aSec -> t / aSec
        t < susStart -> 1f - (1f - sustainLevel) * ((t - aSec) / dSec)
        t < relStart -> sustainLevel
        else -> (sustainLevel * (1f - (t - relStart) / rSec)).coerceAtLeast(0f)
      }
      samples[i] *= env
    }
    return samples
  }
}

internal data class StereoPanNode(val pan: Float) : EffectNode {
  override fun apply(samples: FloatArray, sampleRate: Int, durationMs: Long): FloatArray {
    val angle = (pan.coerceIn(-1f, 1f) + 1f) / 2f * (PI.toFloat() / 2f)
    val gainL = cos(angle)
    val gainR = sin(angle)
    val stereo = FloatArray(samples.size * 2)
    for (i in samples.indices) {
      stereo[i * 2] = samples[i] * gainL
      stereo[i * 2 + 1] = samples[i] * gainR
    }
    return stereo
  }
}
