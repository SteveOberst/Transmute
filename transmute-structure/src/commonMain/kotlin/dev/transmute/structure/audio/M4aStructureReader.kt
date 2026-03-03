@file:Suppress("unused")

package dev.transmute.structure.audio

import dev.transmute.model.structure.audio.types.M4aRaw
import dev.transmute.structure.common.IsoBmffStructureReader

/**
 * Parses raw M4A file bytes into an [M4aRaw] structure.
 *
 * M4aRaw is an audio-only ISO BMFF container with brands like `M4A `,
 * `M4B `, `mp42`, or `isom` containing only audio tracks.
 *
 * ```
 * | ftyp box | moov box | mdat box | ... |
 * ```
 */
class M4aStructureReader : IsoBmffStructureReader<M4aRaw>(::M4aRaw)
