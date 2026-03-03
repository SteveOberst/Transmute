@file:Suppress("unused")

package dev.transmute.structure.image

import dev.transmute.model.structure.image.types.HeifRaw
import dev.transmute.structure.common.IsoBmffStructureReader

/**
 * Parses raw HeifRaw/HEIC file bytes into a [HeifRaw] structure.
 *
 * HeifRaw uses ISO BMFF with major brands `heic`, `heix`, `mif1`, or `heis`.
 *
 * ```
 * | ftyp box | meta box | mdat box | ... |
 * ```
 */
class HeifStructureReader : IsoBmffStructureReader<HeifRaw>(::HeifRaw)
