@file:Suppress("unused")

package dev.transmute.model.core

/**
 * High-level container family grouping related container formats.
 * This interface is open for extension — downstream code can define
 * additional container families.
 */
interface ContainerFamily {
    val name: String

    companion object {
        val IsoBmff: ContainerFamily = KnownContainerFamily("IsoBmff")
        val Riff: ContainerFamily = KnownContainerFamily("Riff")
        val Ebml: ContainerFamily = KnownContainerFamily("Ebml")
        val Ogg: ContainerFamily = KnownContainerFamily("Ogg")
        val Mpeg: ContainerFamily = KnownContainerFamily("Mpeg")
        val Jpeg: ContainerFamily = KnownContainerFamily("Jpeg")
        val Png: ContainerFamily = KnownContainerFamily("Png")
        val Gif: ContainerFamily = KnownContainerFamily("Gif")
        val Bmp: ContainerFamily = KnownContainerFamily("Bmp")
        val Tiff: ContainerFamily = KnownContainerFamily("Tiff")
        val WebP: ContainerFamily = KnownContainerFamily("WebP")
        val Heif: ContainerFamily = KnownContainerFamily("Heif")
        val Avif: ContainerFamily = KnownContainerFamily("Avif")
        val Flac: ContainerFamily = KnownContainerFamily("Flac")
    }
}

internal data class KnownContainerFamily(override val name: String) : ContainerFamily {
    override fun toString(): String = "ContainerFamily.$name"
}

/**
 * Custom container family for formats not covered by the built-in set.
 */
data class OtherContainerFamily(override val name: String) : ContainerFamily {
    override fun toString(): String = "ContainerFamily.Other($name)"
}
