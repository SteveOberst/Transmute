@file:Suppress("unused")

package dev.transmute.structure.audio

import dev.transmute.model.core.Bytes
import dev.transmute.model.structure.StructureReader
import dev.transmute.model.structure.audio.types.WavRaw
import dev.transmute.structure.common.parseRiffContainer

/**
 * Parses raw WAV file bytes into a [WavRaw] structure.
 *
 * WAV files use the RIFF container format:
 * ```
 * | "RIFF" (4 B) | fileSize (4 B LE) | "WAVE" (4 B) | sub-chunks... |
 * ```
 */
class WavStructureReader : StructureReader<WavRaw> {

  override fun read(source: Bytes): WavRaw {
    val riff = parseRiffContainer(source.data, expectedFormType = "WAVE", formatName = "WAV")
    return WavRaw(riff = riff)
  }
}
