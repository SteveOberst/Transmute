@file:Suppress("unused")

package dev.transmute.model.metadata

/**
 * Kind of metadata source. Open for extension.
 */
interface MetadataKind {
    val name: String

    companion object {
        val Exif: MetadataKind = KnownMetadataKind("Exif")
        val Iptc: MetadataKind = KnownMetadataKind("IPTC")
        val Xmp: MetadataKind = KnownMetadataKind("XMP")
        val Id3v1: MetadataKind = KnownMetadataKind("ID3v1")
        val Id3v2: MetadataKind = KnownMetadataKind("ID3v2")
        val VorbisComment: MetadataKind = KnownMetadataKind("VorbisComment")
        val Mp4Atoms: MetadataKind = KnownMetadataKind("MP4Atoms")
        val MatroskaTag: MetadataKind = KnownMetadataKind("MatroskaTag")
        val FlacPicture: MetadataKind = KnownMetadataKind("FlacPicture")
        val PngText: MetadataKind = KnownMetadataKind("PngText")
        val GifComment: MetadataKind = KnownMetadataKind("GifComment")
        val TiffTag: MetadataKind = KnownMetadataKind("TiffTag")
        val IccProfile: MetadataKind = KnownMetadataKind("IccProfile")
        val JfifThumbnail: MetadataKind = KnownMetadataKind("JfifThumbnail")
    }
}

internal data class KnownMetadataKind(override val name: String) : MetadataKind {
    override fun toString(): String = "MetadataKind.$name"
}

/**
 * Custom metadata kind for schemas not in the built-in set.
 */
data class OtherMetadataKind(override val name: String) : MetadataKind {
    override fun toString(): String = "MetadataKind.Other($name)"
}

/**
 * Where a metadata block was found in the file.
 */
data class MetadataSource(
    val kind: MetadataKind,
    val description: String? = null,
)
