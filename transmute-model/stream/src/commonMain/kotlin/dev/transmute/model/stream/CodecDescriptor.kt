@file:Suppress("unused")

package dev.transmute.model.stream

import dev.transmute.model.identify.FourCC

/**
 * Describes the codec used by a media stream.
 */
data class CodecDescriptor(
    val name: String,
    val fourCC: FourCC? = null,
    val profile: String? = null,
    val level: String? = null,
)
