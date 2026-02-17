package dev.transmute.audio.transform

import dev.transmute.audio.AudioIR
import dev.transmute.audio.AudioSamples
import dev.transmute.core.ConversionContext
import dev.transmute.core.pipeline.Transform
import dev.transmute.core.pipeline.TransformId

/**
 * Remaps audio channels - swap L/R, duplicate mono to stereo, or
 * select specific channels from a multi-channel source.
 *
 * The [mapping] array defines which source channel feeds each output
 * channel. `mapping[outputCh] = sourceCh`. For example:
 *
 * - Swap stereo: `intArrayOf(1, 0)` - right → left, left → right
 * - Mono → stereo: `intArrayOf(0, 0)` - duplicate channel 0
 * - Pick centre from 5.1: `intArrayOf(2)` - extract channel 2 only
 *
 * Source indices that exceed the actual channel count wrap around
 * (modulo) to prevent crashes from misconfigured mappings.
 *
 * @param mapping Output-to-source channel index mapping.
 */
class AudioChannelMapTransform(
  private val mapping: IntArray,
) : Transform<AudioIR> {

  override val id = TransformId("audio.channel-map")

  override suspend fun apply(ir: AudioIR, context: ConversionContext): AudioIR {
    val srcChannels = ir.channelCount
    val dstChannels = mapping.size

    if (dstChannels == srcChannels && mapping.indices.all { mapping[it] == it }) {
      context.logger.debug("AudioChannelMapTransform: identity mapping - skipping")
      return ir
    }

    context.logger.info(
      "AudioChannelMapTransform: ${srcChannels}ch → ${dstChannels}ch, mapping=${mapping.toList()}"
    )

    val samples = ir.samples.data
    val frameCount = samples.size / srcChannels
    val output = FloatArray(frameCount * dstChannels)

    for (frame in 0 until frameCount) {
      for (dstCh in 0 until dstChannels) {
        // Modulo prevents out-of-bounds if mapping refers to a missing channel.
        val srcCh = mapping[dstCh] % srcChannels
        output[frame * dstChannels + dstCh] = samples[frame * srcChannels + srcCh]
      }
    }

    return ir.copy(
      samples = AudioSamples(output, ir.sampleRate, dstChannels),
      channelCount = dstChannels,
    )
  }
}
