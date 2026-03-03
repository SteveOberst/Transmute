@file:Suppress("unused")

package dev.transmute.structure.video

import dev.transmute.model.structure.video.types.MkvRaw
import dev.transmute.structure.common.EbmlStructureReader

/**
 * Parses raw MkvRaw (Matroska Video) file bytes into an [MkvRaw] structure.
 *
 * MkvRaw uses the EBML container format with DocType `matroska`.
 *
 * ```
 * | EBML Header | Segment |
 * ```
 */
class MkvStructureReader : EbmlStructureReader<MkvRaw>(::MkvRaw)
