@file:Suppress("unused")

package dev.transmute.structure.audio

import dev.transmute.model.core.Bytes
import dev.transmute.model.structure.StructureReader
import dev.transmute.model.structure.audio.types.AacRaw

/**
 * Parses raw AAC ADTS file bytes into an [AacRaw] structure.
 *
 * AAC ADTS is a stream of self-delimiting frames starting with a
 * 12-bit sync word (`0xFFF`).  The entire file is stored as an
 * opaque blob since individual frame parsing is expensive.
 */
class AacStructureReader : StructureReader<AacRaw> {

  override fun read(source: Bytes): AacRaw = AacRaw(data = source)
}
