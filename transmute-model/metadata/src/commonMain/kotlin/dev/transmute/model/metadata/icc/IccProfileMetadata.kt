@file:Suppress("unused")

package dev.transmute.model.metadata.icc

import dev.transmute.model.core.MediaMetadata
import kotlinx.serialization.Serializable

/**
 * ICC colour profile metadata modelling the on-disk hierarchy:
 *
 * - 128-byte profile header (class, colour space, PCS, version, ...)
 * - Tag table listing (signature, offset, size) for each data element
 *
 * Tag payloads are *not* decoded - only the structural tag table is
 * included. This gives a complete picture of the profile's organisation
 * without embedding large LUT or curve data.
 */
@Serializable
data class IccProfileMetadata(
    val header: IccHeader,
    val tags: List<IccTag>,
) : MediaMetadata

// -- Header -------------------------------------------------------------------

@Serializable
data class IccHeader(
    /** Total profile size in bytes. */
    val profileSize: Long,
    /** Preferred CMM type (4-char signature, e.g. `"APPL"`). */
    val preferredCmm: String? = null,
    /** Profile version string (e.g. `"4.3.0"`). */
    val profileVersion: String,
    /** Device class (e.g. `"mntr"` for monitor, `"prtr"` for printer). */
    val profileClass: String,
    /** Data colour space (e.g. `"RGB "`, `"CMYK"`, `"GRAY"`). */
    val colorSpace: String,
    /** Profile Connection Space (e.g. `"XYZ "`, `"Lab "`). */
    val pcs: String,
    /** Creation date/time as ISO 8601 string. */
    val creationDate: String? = null,
    /** Primary platform signature (e.g. `"APPL"`, `"MSFT"`). */
    val primaryPlatform: String? = null,
    /** Rendering intent (perceptual / relative / saturation / absolute). */
    val renderingIntent: String,
)

// -- Tag table ----------------------------------------------------------------

@Serializable
data class IccTag(
    /** 4-char tag signature (e.g. `"desc"`, `"rXYZ"`, `"gTRC"`). */
    val signature: String,
    /** Byte offset from the start of the profile. */
    val offset: Long,
    /** Size of the tag data in bytes. */
    val size: Long,
)
