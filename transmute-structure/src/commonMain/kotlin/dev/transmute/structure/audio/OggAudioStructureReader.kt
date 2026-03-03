@file:Suppress("unused")

package dev.transmute.structure.audio

import dev.transmute.model.structure.audio.types.OggAudioRaw
import dev.transmute.structure.common.OggStructureReader

/**
 * Parses raw Ogg Vorbis file bytes into an [OggAudioRaw] structure.
 *
 * Ogg Vorbis uses the Ogg container with a Vorbis codec.
 * The BOS page's first packet starts with `\x01vorbis`.
 */
class OggAudioStructureReader : OggStructureReader<OggAudioRaw>(::OggAudioRaw)
