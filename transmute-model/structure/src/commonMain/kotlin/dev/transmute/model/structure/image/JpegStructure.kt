@file:Suppress("unused")

package dev.transmute.model.structure.image

import dev.transmute.model.core.MediaStructure
import dev.transmute.model.structure.image.types.JpegMarkerType
import dev.transmute.model.structure.image.types.JpegRaw
import kotlinx.serialization.Serializable

@Serializable
data class JpegSegmentSummary(
  /** Marker-type byte (the byte after 0xFF). */
  val marker: UByte,
  /** Human-readable marker name when known (e.g. "SOI", "APP1", "SOS"). */
  val markerName: String? = null,
  /** Segment payload size in bytes (excluding marker and length field). */
  val dataSizeBytes: Int,
  /** Entropy-coded scan data size in bytes (non-zero only for SOS). */
  val entropySizeBytes: Int = 0,
)

/**
 * Summary of a JPEG scan (SOS + entropy data).
 */
@Serializable
data class JpegScanSummary(
  /** The SOS segment summary. */
  val sos: JpegSegmentSummary,
  /** Byte size of entropy-coded data for this scan. */
  val entropyDataSize: Int,
  /** Number of inter-scan table/misc segments following this scan. */
  val interScanSegmentCount: Int,
  /** Inter-scan segment summaries. */
  val interScanSegments: List<JpegSegmentSummary> = emptyList(),
)

/**
 * Summary of a JPEG frame (SOF + table segments + scans).
 */
@Serializable
data class JpegFrameSummary(
  /** SOF marker byte (0xC0 = baseline, 0xC2 = progressive, etc.). */
  val sofMarker: UByte,
  /** Human-readable SOF marker name. */
  val sofMarkerName: String? = null,
  /** Table/misc segments between SOF and first SOS. */
  val tableSegments: List<JpegSegmentSummary> = emptyList(),
  /** Scans within this frame. */
  val scans: List<JpegScanSummary> = emptyList(),
)

/**
 * Structured representation of a JPEG file following the ITU-T T.81
 * specification hierarchy.
 *
 * ```
 * SOI
 *   +- [APP0/APP1 ..., COM, DQT ...]     <- headerSegments
 *   +- Frame (SOFn)
 *        +- [DQT, DHT, DRI ...]          <- frame.tableSegments
 *        +- Scan 1 (SOS + entropy)
 *        +- [inter-scan tables]
 *        +- Scan 2 ...
 * EOI                                    <- trailerSegments
 * ```
 */
@Serializable
data class JpegStructure(
  /** Header segments (SOI, APP markers, COM, etc.) before the frame. */
  val headerSegments: List<JpegSegmentSummary>,
  /** Frame summary (SOF + scans), or null for metadata-only files. */
  val frame: JpegFrameSummary? = null,
  /** Trailer segments (typically just EOI). */
  val trailerSegments: List<JpegSegmentSummary> = emptyList(),
) : MediaStructure {
  /**
   * Flat list of all segment summaries in file order, for backward
   * compatibility with code that expects a single list.
   */
  val segments: List<JpegSegmentSummary>
    get() = buildList {
      addAll(headerSegments)
      frame?.let { f ->
        add(
          JpegSegmentSummary(
            marker = f.sofMarker,
            markerName = f.sofMarkerName,
            dataSizeBytes = 0, // SOF data size not tracked in summary
          ),
        )
        addAll(f.tableSegments)
        for (scan in f.scans) {
          add(scan.sos)
          addAll(scan.interScanSegments)
        }
      }
      addAll(trailerSegments)
    }
}

private fun segmentSummary(marker: UByte, dataSizeBytes: Int): JpegSegmentSummary = JpegSegmentSummary(
  marker = marker,
  markerName = JpegMarkerType.fromCode(marker)?.name,
  dataSizeBytes = dataSizeBytes,
)

/**
 * Parse this [JpegRaw] into a [JpegStructure].
 *
 * Entropy-coded scan data is summarized as byte sizes rather than
 * included directly, keeping the structure lightweight.
 */
fun JpegRaw.toStructure(): JpegStructure {
  val headerSummaries = headerSegments.map { segmentSummary(it.marker, it.data.size) }
  val trailerSummaries = trailerSegments.map { segmentSummary(it.marker, it.data.size) }

  val frameSummary = frame?.let { f ->
    JpegFrameSummary(
      sofMarker = f.sofMarker,
      sofMarkerName = JpegMarkerType.fromCode(f.sofMarker)?.name,
      tableSegments = f.tableSegments.map { segmentSummary(it.marker, it.data.size) },
      scans = f.scans.map { scan ->
        JpegScanSummary(
          sos = segmentSummary(scan.sosSegment.marker, scan.sosSegment.data.size),
          entropyDataSize = scan.entropy.size,
          interScanSegmentCount = scan.interScanSegments.size,
          interScanSegments = scan.interScanSegments.map { segmentSummary(it.marker, it.data.size) },
        )
      },
    )
  }

  return JpegStructure(
    headerSegments = headerSummaries,
    frame = frameSummary,
    trailerSegments = trailerSummaries,
  )
}
