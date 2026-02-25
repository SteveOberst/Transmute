@file:Suppress("unused")

package dev.transmute.model.metadata

import dev.transmute.model.identify.FourCC

// --- MetadataKey interface ---

/**
 * Key identifying a metadata field. Open for extension.
 */
interface MetadataKey {
    val displayName: String
}

// --- EXIF ---

enum class ExifIfd { Ifd0, Ifd1, ExifIfd, GpsIfd, InteropIfd }

data class ExifTag(
    val tagId: Int,
    val ifd: ExifIfd = ExifIfd.Ifd0,
    override val displayName: String = "Exif:0x${tagId.toString(16).uppercase()}",
) : MetadataKey

// --- ID3 ---

@JvmInline
value class Id3FrameId(val value: String) {
    init {
        require(value.length in 3..4) { "Id3FrameId must be 3 or 4 characters, was '${value}'" }
    }

    override fun toString(): String = value
}

data class Id3Frame(
    val frameId: Id3FrameId,
    override val displayName: String = frameId.value,
) : MetadataKey

// --- Vorbis Comment ---

@JvmInline
value class VorbisFieldName(val value: String) {
    init {
        require(value.isNotBlank()) { "VorbisFieldName must not be blank" }
    }

    override fun toString(): String = value
}

data class VorbisCommentKey(
    val fieldName: VorbisFieldName,
    override val displayName: String = fieldName.value,
) : MetadataKey

// --- MP4 Atoms ---

data class Mp4AtomKey(
    val atomType: FourCC,
    override val displayName: String = atomType.value,
) : MetadataKey

// --- XMP ---

@JvmInline
value class XmpPathString(val value: String) {
    override fun toString(): String = value
}

data class XmpPathKey(
    val path: XmpPathString,
    override val displayName: String = path.value,
) : MetadataKey

// --- Matroska Tag ---

data class MatroskaTagKey(
    val tagName: String,
    override val displayName: String = tagName,
) : MetadataKey

// --- PNG Text ---

data class PngTextKey(
    val keyword: String,
    override val displayName: String = keyword,
) : MetadataKey

// --- Fallback ---

data class OtherKey(
    val rawKey: String,
    override val displayName: String = rawKey,
) : MetadataKey
