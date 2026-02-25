@file:Suppress("unused")

package dev.transmute.model.stream

/**
 * Type of media stream. Open for extension — downstream code can
 * define additional stream types.
 */
interface StreamType {
    val name: String

    companion object {
        val Video: StreamType = KnownStreamType("Video")
        val Audio: StreamType = KnownStreamType("Audio")
        val Subtitle: StreamType = KnownStreamType("Subtitle")
        val Image: StreamType = KnownStreamType("Image")
        val Data: StreamType = KnownStreamType("Data")
        val Attachment: StreamType = KnownStreamType("Attachment")
    }
}

internal data class KnownStreamType(override val name: String) : StreamType {
    override fun toString(): String = "StreamType.$name"
}

/**
 * Custom stream type for types not covered by the built-in set.
 */
data class OtherStreamType(override val name: String) : StreamType {
    override fun toString(): String = "StreamType.Other($name)"
}
