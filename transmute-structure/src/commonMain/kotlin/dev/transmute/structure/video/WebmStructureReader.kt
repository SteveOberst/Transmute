@file:Suppress("unused")

package dev.transmute.structure.video

import dev.transmute.model.structure.video.types.WebmRaw
import dev.transmute.structure.common.EbmlStructureReader

/**
 * Parses raw WebM file bytes into a [WebmRaw] structure.
 *
 * WebmRaw uses the EBML container format with DocType `webm`.
 *
 * ```
 * | EBML Header | Segment |
 * ```
 */
class WebmStructureReader : EbmlStructureReader<WebmRaw>(::WebmRaw)
