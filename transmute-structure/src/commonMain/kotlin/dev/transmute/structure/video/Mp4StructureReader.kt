@file:Suppress("unused")

package dev.transmute.structure.video

import dev.transmute.model.structure.video.types.Mp4Raw
import dev.transmute.structure.common.IsoBmffStructureReader

/**
 * Parses raw MP4 file bytes into an [Mp4Raw] structure.
 *
 * Mp4Raw uses the ISO BMFF container with brands like `isom`, `mp41`,
 * `mp42`, `iso2`, `iso5`, `iso6`, `dash`, `msdh`, `msix`.
 *
 * ```
 * | ftyp box | moov box | mdat box | ... |
 * ```
 */
class Mp4StructureReader : IsoBmffStructureReader<Mp4Raw>(::Mp4Raw)
