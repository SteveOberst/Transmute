package dev.transmute

import dev.transmute.audio.AudioFormat
import dev.transmute.audio.transform.AudioChannelMapTransform
import dev.transmute.audio.transform.AudioCompressorTransform
import dev.transmute.audio.transform.AudioFadeTransform
import dev.transmute.audio.transform.AudioGainTransform
import dev.transmute.audio.transform.AudioMonoTransform
import dev.transmute.audio.transform.AudioNormalizeTransform
import dev.transmute.audio.transform.AudioResampleTransform
import dev.transmute.audio.transform.AudioReverseTransform
import dev.transmute.audio.transform.AudioSilenceTrimTransform
import dev.transmute.audio.transform.AudioSpeedTransform
import dev.transmute.audio.transform.AudioTrimTransform

/** Normalize peak amplitude. Default target 0.95. */
fun <IN> DynamicAudioTransmuterBuilder<IN>.normalize(targetPeak: Float = 0.95f): DynamicAudioTransmuterBuilder<IN> = apply {
  transform { add(AudioNormalizeTransform(targetPeak)) }
}

fun <IN, OUT : AudioFormat> AudioTransmuterBuilder<IN, OUT>.normalize(
  targetPeak: Float = 0.95f,
): AudioTransmuterBuilder<IN, OUT> = apply {
  transform { add(AudioNormalizeTransform(targetPeak)) }
}

/** Resample to [targetSampleRate] Hz using linear interpolation. */
fun <IN> DynamicAudioTransmuterBuilder<IN>.resample(targetSampleRate: Int): DynamicAudioTransmuterBuilder<IN> = apply {
  transform { add(AudioResampleTransform(targetSampleRate)) }
}

fun <IN, OUT : AudioFormat> AudioTransmuterBuilder<IN, OUT>.resample(
  targetSampleRate: Int,
): AudioTransmuterBuilder<IN, OUT> = apply {
  transform { add(AudioResampleTransform(targetSampleRate)) }
}

/** Apply fade-in / fade-out envelopes (milliseconds). */
fun <IN> DynamicAudioTransmuterBuilder<IN>.fade(fadeInMs: Long = 0, fadeOutMs: Long = 0): DynamicAudioTransmuterBuilder<IN> = apply {
  transform { add(AudioFadeTransform(fadeInMs, fadeOutMs)) }
}

fun <IN, OUT : AudioFormat> AudioTransmuterBuilder<IN, OUT>.fade(
  fadeInMs: Long = 0,
  fadeOutMs: Long = 0,
): AudioTransmuterBuilder<IN, OUT> = apply {
  transform { add(AudioFadeTransform(fadeInMs, fadeOutMs)) }
}

/** Trim to time range (milliseconds). [endMs] = null → end of audio. */
fun <IN> DynamicAudioTransmuterBuilder<IN>.trim(startMs: Long, endMs: Long? = null): DynamicAudioTransmuterBuilder<IN> = apply {
  transform { add(AudioTrimTransform(startMs, endMs)) }
}

fun <IN, OUT : AudioFormat> AudioTransmuterBuilder<IN, OUT>.trim(
  startMs: Long,
  endMs: Long? = null,
): AudioTransmuterBuilder<IN, OUT> = apply {
  transform { add(AudioTrimTransform(startMs, endMs)) }
}

/** Apply volume gain in decibels (+dB louder, −dB quieter). */
fun <IN> DynamicAudioTransmuterBuilder<IN>.gain(db: Float): DynamicAudioTransmuterBuilder<IN> = apply {
  transform { add(AudioGainTransform(db)) }
}

fun <IN, OUT : AudioFormat> AudioTransmuterBuilder<IN, OUT>.gain(db: Float): AudioTransmuterBuilder<IN, OUT> = apply {
  transform { add(AudioGainTransform(db)) }
}

/** Convert stereo → mono by averaging channels. */
fun <IN> DynamicAudioTransmuterBuilder<IN>.mono(): DynamicAudioTransmuterBuilder<IN> = apply {
  transform { add(AudioMonoTransform()) }
}

fun <IN, OUT : AudioFormat> AudioTransmuterBuilder<IN, OUT>.mono(): AudioTransmuterBuilder<IN, OUT> = apply {
  transform { add(AudioMonoTransform()) }
}

/** Reverse playback. */
fun <IN> DynamicAudioTransmuterBuilder<IN>.reverse(): DynamicAudioTransmuterBuilder<IN> = apply {
  transform { add(AudioReverseTransform()) }
}

fun <IN, OUT : AudioFormat> AudioTransmuterBuilder<IN, OUT>.reverse(): AudioTransmuterBuilder<IN, OUT> = apply {
  transform { add(AudioReverseTransform()) }
}

/** Change playback speed without altering pitch (SOLA time-stretch). */
fun <IN> DynamicAudioTransmuterBuilder<IN>.speed(speed: Float): DynamicAudioTransmuterBuilder<IN> = apply {
  transform { add(AudioSpeedTransform(speed)) }
}

fun <IN, OUT : AudioFormat> AudioTransmuterBuilder<IN, OUT>.speed(speed: Float): AudioTransmuterBuilder<IN, OUT> = apply {
  transform { add(AudioSpeedTransform(speed)) }
}

/** Trim silence from start and/or end. */
fun <IN> DynamicAudioTransmuterBuilder<IN>.silenceTrim(
  thresholdDb: Float = -40f,
  minSilenceMs: Long = 100,
  trimStart: Boolean = true,
  trimEnd: Boolean = true,
): DynamicAudioTransmuterBuilder<IN> = apply {
  transform { add(AudioSilenceTrimTransform(thresholdDb, minSilenceMs, trimStart, trimEnd)) }
}

fun <IN, OUT : AudioFormat> AudioTransmuterBuilder<IN, OUT>.silenceTrim(
  thresholdDb: Float = -40f,
  minSilenceMs: Long = 100,
  trimStart: Boolean = true,
  trimEnd: Boolean = true,
): AudioTransmuterBuilder<IN, OUT> = apply {
  transform { add(AudioSilenceTrimTransform(thresholdDb, minSilenceMs, trimStart, trimEnd)) }
}

/** Apply dynamic range compression. */
fun <IN> DynamicAudioTransmuterBuilder<IN>.compressor(
  thresholdDb: Float = -20f,
  ratio: Float = 4f,
  attackMs: Float = 10f,
  releaseMs: Float = 100f,
  makeupGainDb: Float = 0f,
): DynamicAudioTransmuterBuilder<IN> = apply {
  transform { add(AudioCompressorTransform(thresholdDb, ratio, attackMs, releaseMs, makeupGainDb)) }
}

fun <IN, OUT : AudioFormat> AudioTransmuterBuilder<IN, OUT>.compressor(
  thresholdDb: Float = -20f,
  ratio: Float = 4f,
  attackMs: Float = 10f,
  releaseMs: Float = 100f,
  makeupGainDb: Float = 0f,
): AudioTransmuterBuilder<IN, OUT> = apply {
  transform { add(AudioCompressorTransform(thresholdDb, ratio, attackMs, releaseMs, makeupGainDb)) }
}

/** Remap audio channels. [mapping] defines output→source channel indices. */
fun <IN> DynamicAudioTransmuterBuilder<IN>.channelMap(mapping: IntArray): DynamicAudioTransmuterBuilder<IN> = apply {
  transform { add(AudioChannelMapTransform(mapping)) }
}

fun <IN, OUT : AudioFormat> AudioTransmuterBuilder<IN, OUT>.channelMap(
  mapping: IntArray,
): AudioTransmuterBuilder<IN, OUT> = apply {
  transform { add(AudioChannelMapTransform(mapping)) }
}
