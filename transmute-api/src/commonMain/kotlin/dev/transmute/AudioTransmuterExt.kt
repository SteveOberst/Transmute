package dev.transmute

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
fun AudioTransmuter.normalize(targetPeak: Float = 0.95f): AudioTransmuter = apply {
  pipeline.add(AudioNormalizeTransform(targetPeak))
}

/** Resample to [targetSampleRate] Hz using linear interpolation. */
fun AudioTransmuter.resample(targetSampleRate: Int): AudioTransmuter = apply {
  pipeline.add(AudioResampleTransform(targetSampleRate))
}

/** Apply fade-in / fade-out envelopes (milliseconds). */
fun AudioTransmuter.fade(fadeInMs: Long = 0, fadeOutMs: Long = 0): AudioTransmuter = apply {
  pipeline.add(AudioFadeTransform(fadeInMs, fadeOutMs))
}

/** Trim to time range (milliseconds). [endMs] = null → end of audio. */
fun AudioTransmuter.trim(startMs: Long, endMs: Long? = null): AudioTransmuter = apply {
  pipeline.add(AudioTrimTransform(startMs, endMs))
}

/** Apply volume gain in decibels (+dB louder, −dB quieter). */
fun AudioTransmuter.gain(db: Float): AudioTransmuter = apply {
  pipeline.add(AudioGainTransform(db))
}

/** Convert stereo → mono by averaging channels. */
fun AudioTransmuter.mono(): AudioTransmuter = apply {
  pipeline.add(AudioMonoTransform())
}

/** Reverse playback. */
fun AudioTransmuter.reverse(): AudioTransmuter = apply {
  pipeline.add(AudioReverseTransform())
}

/** Change playback speed without altering pitch (SOLA time-stretch). */
fun AudioTransmuter.speed(speed: Float): AudioTransmuter = apply {
  pipeline.add(AudioSpeedTransform(speed))
}

/** Trim silence from start and/or end. */
fun AudioTransmuter.silenceTrim(
  thresholdDb: Float = -40f,
  minSilenceMs: Long = 100,
  trimStart: Boolean = true,
  trimEnd: Boolean = true,
): AudioTransmuter = apply {
  pipeline.add(AudioSilenceTrimTransform(thresholdDb, minSilenceMs, trimStart, trimEnd))
}

/** Apply dynamic range compression. */
fun AudioTransmuter.compressor(
  thresholdDb: Float = -20f,
  ratio: Float = 4f,
  attackMs: Float = 10f,
  releaseMs: Float = 100f,
  makeupGainDb: Float = 0f,
): AudioTransmuter = apply {
  pipeline.add(AudioCompressorTransform(thresholdDb, ratio, attackMs, releaseMs, makeupGainDb))
}

/** Remap audio channels. [mapping] defines output→source channel indices. */
fun AudioTransmuter.channelMap(mapping: IntArray): AudioTransmuter = apply {
  pipeline.add(AudioChannelMapTransform(mapping))
}
