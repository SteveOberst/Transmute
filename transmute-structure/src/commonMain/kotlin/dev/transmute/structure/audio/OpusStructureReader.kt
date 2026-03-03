@file:Suppress("unused")

package dev.transmute.structure.audio

import dev.transmute.model.structure.audio.types.OpusRaw
import dev.transmute.structure.common.OggStructureReader

/**
 * Parses raw Opus file bytes into an [OpusRaw] structure.
 *
 * Opus uses the Ogg container.  The BOS page's first packet
 * starts with `OpusHead`.
 */
class OpusStructureReader : OggStructureReader<OpusRaw>(::OpusRaw)
