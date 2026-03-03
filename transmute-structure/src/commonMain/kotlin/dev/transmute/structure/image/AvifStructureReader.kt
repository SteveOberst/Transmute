@file:Suppress("unused")

package dev.transmute.structure.image

import dev.transmute.model.structure.image.types.AvifRaw
import dev.transmute.structure.common.IsoBmffStructureReader

/**
 * Parses raw AVIF file bytes into an [AvifRaw] structure.
 *
 * AvifRaw uses ISO BMFF with major brands `avif` or `avis`.
 *
 * ```
 * | ftyp box | meta box | mdat box | ... |
 * ```
 */
class AvifStructureReader : IsoBmffStructureReader<AvifRaw>(::AvifRaw)
