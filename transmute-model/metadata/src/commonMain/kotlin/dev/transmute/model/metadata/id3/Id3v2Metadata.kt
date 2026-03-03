@file:Suppress("unused")

package dev.transmute.model.metadata.id3

import dev.transmute.model.core.MediaMetadata
import kotlinx.serialization.Serializable

// -- Top-level model ----------------------------------------------------------

/**
 * ID3v2 tag metadata modelling the on-disk hierarchy:
 *
 * - Tag header (version, flags, total size)
 * - Ordered list of frames, each with a 4-char (v2.3/v2.4) or 3-char (v2.2) id
 *
 * Raw binary frame payloads (e.g. embedded pictures) are summarised by size;
 * text- and URL-based frames are decoded into typed values.
 */
@Serializable
data class Id3v2Metadata(
    val version: Id3v2Version,
    val flags: Id3v2HeaderFlags,
    val tagSizeBytes: Long,
    val frames: List<Id3v2Frame>,
) : MediaMetadata

// -- Header types -------------------------------------------------------------

@Serializable
data class Id3v2Version(
    /** Major version (2, 3, or 4). */
    val major: Int,
    /** Revision within the major version. */
    val revision: Int,
)

@Serializable
data class Id3v2HeaderFlags(
    val unsynchronisation: Boolean = false,
    val extendedHeader: Boolean = false,
    val experimental: Boolean = false,
    /** v2.4 only. */
    val footer: Boolean = false,
)

// -- Frames -------------------------------------------------------------------

@Serializable
data class Id3v2Frame(
    /** Frame identifier, e.g. `"TIT2"`, `"APIC"`, `"TXXX"`. */
    val id: String,
    /** Size of the frame data (excluding header). */
    val dataSizeBytes: Long,
    /** Decoded content. */
    val content: Id3v2FrameContent,
)

/**
 * Typed frame content matching the ID3v2 frame families.
 */
@Serializable
sealed class Id3v2FrameContent {
    /** Text-information frame (T*** except TXXX). */
    @Serializable
    data class Text(
        val encoding: String,
        val text: String,
    ) : Id3v2FrameContent()

    /** User-defined text (TXXX). */
    @Serializable
    data class UserText(
        val encoding: String,
        val description: String,
        val text: String,
    ) : Id3v2FrameContent()

    /** URL link frame (W*** except WXXX). */
    @Serializable
    data class Url(val url: String) : Id3v2FrameContent()

    /** User-defined URL (WXXX). */
    @Serializable
    data class UserUrl(
        val encoding: String,
        val description: String,
        val url: String,
    ) : Id3v2FrameContent()

    /** Comment (COMM) or Unsynchronised lyrics (USLT). */
    @Serializable
    data class Comment(
        val encoding: String,
        val language: String,
        val description: String,
        val text: String,
    ) : Id3v2FrameContent()

    /** Attached picture (APIC) - image data summarised by size. */
    @Serializable
    data class Picture(
        val mimeType: String,
        val pictureType: Int,
        val description: String,
        val dataSizeBytes: Long,
    ) : Id3v2FrameContent()

    /** Any frame whose payload is not decoded - size summary only. */
    @Serializable
    data class Binary(val sizeBytes: Long) : Id3v2FrameContent()
}
