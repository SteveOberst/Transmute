@file:Suppress("unused")

package dev.transmute.model.structure.common

import dev.transmute.model.structure.video.types.EbmlHeaderData
import dev.transmute.model.structure.video.types.MatroskaIds

// ================================================================
//  Shared EBML list extensions (used by MKV, WebM, and any
//  future EBML-based format)
// ================================================================

/** The EBML header element. */
val List<EbmlElement>.ebmlHeader: EbmlElement?
  get() = firstOrNull { it.id == MatroskaIds.EBML }

/** The Segment element. */
val List<EbmlElement>.segment: EbmlElement?
  get() = firstOrNull { it.id == MatroskaIds.Segment }

/** Parsed EBML header metadata. */
val List<EbmlElement>.headerData: EbmlHeaderData?
  get() {
    val hdr = ebmlHeader ?: return null
    val docType = hdr.children.firstOrNull { it.id == MatroskaIds.DocType }
      ?.data?.data?.decodeToString() ?: return null
    val ver = hdr.children.firstOrNull { it.id == MatroskaIds.DocTypeVersion }
      ?.data?.data?.let { readEbmlUInt(it) }?.toInt() ?: 0
    val readVer = hdr.children.firstOrNull { it.id == MatroskaIds.DocTypeReadVersion }
      ?.data?.data?.let { readEbmlUInt(it) }?.toInt() ?: 0
    return EbmlHeaderData(docType, ver, readVer)
  }

/** Info element inside the Segment. */
val List<EbmlElement>.infoElement: EbmlElement?
  get() = segment?.children?.firstOrNull { it.id == MatroskaIds.Info }

/** Tracks element inside the Segment. */
val List<EbmlElement>.tracksElement: EbmlElement?
  get() = segment?.children?.firstOrNull { it.id == MatroskaIds.Tracks }

/** Read a big-endian unsigned integer from a variable-length EBML payload. */
internal fun readEbmlUInt(bytes: ByteArray): Long {
  var v = 0L
  for (b in bytes) v = (v shl 8) or (b.toLong() and 0xFF)
  return v
}
