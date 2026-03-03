@file:Suppress("unused")

package dev.transmute.structure.video

import dev.transmute.model.structure.video.types.MovRaw
import dev.transmute.structure.common.IsoBmffStructureReader

/**
 * Parses raw MovRaw (QuickTime) file bytes into a [MovRaw] structure.
 *
 * MovRaw uses the ISO BMFF container with brand `qt  ` or classic
 * QuickTime files that may start with a `moov` or `wide` box
 * (no `ftyp`).
 *
 * ```
 * | ftyp box | moov box | mdat box | ... |
 * ```
 */
class MovStructureReader : IsoBmffStructureReader<MovRaw>(::MovRaw)
