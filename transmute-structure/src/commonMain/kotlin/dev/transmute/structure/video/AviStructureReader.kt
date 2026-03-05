@file:Suppress("unused")

package dev.transmute.structure.video

import dev.transmute.model.core.Bytes
import dev.transmute.model.structure.StructureReader
import dev.transmute.model.structure.video.types.AviRaw
import dev.transmute.structure.common.parseRiffContainer

/**
 * Parses raw AVI file bytes into an [AviRaw] structure.
 *
 * AVI uses a RIFF container with form type `AVI `.
 *
 * ```
 * | "RIFF" (4 B) | fileSize (4 B LE) | "AVI " (4 B) | sub-chunks... |
 * ```
 */
class AviStructureReader : StructureReader<AviRaw> {

  override fun read(source: Bytes): AviRaw {
    val riff = parseRiffContainer(source.data, expectedFormType = "AVI ", formatName = "AVI")
    return AviRaw(riff = riff)
  }
}
