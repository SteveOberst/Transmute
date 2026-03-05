package dev.transmute.audio.transform

import dev.transmute.audio.AudioHint
import dev.transmute.audio.AudioIR
import dev.transmute.audio.AudioSamples
import dev.transmute.audio.AudioTransform
import dev.transmute.codec.pipeline.TransformId
import dev.transmute.common.PipelineContext
import kotlin.math.abs
import kotlin.math.pow

/**
 * Dynamic range compressor - reduces the volume difference between
 * loud and quiet parts of the audio.
 *
 * Works by applying gain reduction to samples that exceed [thresholdDb].
 * The amount of reduction is controlled by [ratio]: at 4:1, a signal
 * that exceeds the threshold by 4 dB will only exceed by 1 dB in output.
 *
 * Attack and release times smooth the gain changes to avoid audible
 * artefacts (clicks, "pumping"). Short attack catches transients;
 * longer release sounds more natural.
 *
 * Optional [makeupGainDb] can restore overall loudness after compression.
 *
 * @param thresholdDb Level above which compression kicks in (dBFS). Default 20 dB.
 * @param ratio       Compression ratio. 1.0 = no compression,  = limiter. Default 4.0.
 * @param attackMs    How quickly the compressor responds to loud signals (ms). Default 10.
 * @param releaseMs   How quickly gain recovers after the signal drops (ms). Default 100.
 * @param makeupGainDb Extra gain applied after compression to restore loudness. Default 0.
 */
class AudioCompressorTransform(
  val thresholdDb: Float = -20f,
  val ratio: Float = 4f,
  val attackMs: Float = 10f,
  val releaseMs: Float = 100f,
  val makeupGainDb: Float = 0f,
) : AudioTransform {

  override fun wouldTransform(hint: AudioHint): Boolean = ratio > 1f

  override val id = TransformId("audio.compressor")

  override suspend fun apply(ir: AudioIR, context: PipelineContext): AudioIR {
    if (ratio <= 1f) {
      context.logger.debug("AudioCompressorTransform: ratio <= 1 - skipping")
      return ir
    }

    context.logger.info(
      "AudioCompressorTransform: threshold=${thresholdDb}dB ratio=$ratio:1 " +
        "attack=${attackMs}ms release=${releaseMs}ms makeup=${makeupGainDb}dB",
    )

    val samples = ir.samples.data
    val channelCount = ir.channelCount
    val sampleRate = ir.sampleRate
    val frameCount = samples.size / channelCount

    val thresholdLin = 10.0.pow(thresholdDb / 20.0).toFloat()
    val makeupLin = 10.0.pow(makeupGainDb / 20.0).toFloat()

    // Smoothing coefficients - derived from the time constants.
    //  = 1  e^(1/(SRxT)) where T is in seconds.
    val attackCoeff = 1f - kotlin.math.exp(-1.0 / (sampleRate * attackMs / 1000.0)).toFloat()
    val releaseCoeff = 1f - kotlin.math.exp(-1.0 / (sampleRate * releaseMs / 1000.0)).toFloat()

    val output = FloatArray(samples.size)
    var envelope = 0f

    for (frame in 0 until frameCount) {
      // Peak detect across all channels in this frame.
      var peak = 0f
      for (ch in 0 until channelCount) {
        peak = maxOf(peak, abs(samples[frame * channelCount + ch]))
      }

      // Envelope follower - fast attack, slow release.
      val coeff = if (peak > envelope) attackCoeff else releaseCoeff
      envelope += coeff * (peak - envelope)

      // Calculate gain reduction.
      val gain = if (envelope > thresholdLin) {
        // Amount above threshold in linear domain.
        val overDb = 20.0 * kotlin.math.log10(envelope / thresholdLin.toDouble())
        val reducedDb = overDb / ratio
        val targetDb = 20.0 * kotlin.math.log10(thresholdLin.toDouble()) + reducedDb
        val targetLin = 10.0.pow(targetDb / 20.0).toFloat()
        if (envelope > 0f) targetLin / envelope else 1f
      } else {
        1f
      }

      for (ch in 0 until channelCount) {
        val idx = frame * channelCount + ch
        output[idx] = (samples[idx] * gain * makeupLin).coerceIn(-1f, 1f)
      }
    }

    return ir.copy(
      samples = AudioSamples(output, sampleRate, channelCount),
    )
  }
}
