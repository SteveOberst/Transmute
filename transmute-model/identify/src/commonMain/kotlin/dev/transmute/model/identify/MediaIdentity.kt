@file:Suppress("unused")

package dev.transmute.model.identify

import dev.transmute.model.core.ByteLength
import dev.transmute.model.core.ContainerFamily
import dev.transmute.model.core.MediaFormat
import kotlinx.serialization.Serializable

/**
 * Confidence level for format detection.
 */
@Serializable
enum class Confidence {
    /** Matched only by file extension or heuristic. */
    Low,

    /** Matched by partial magic bytes or structural hints. */
    Medium,

    /** Matched by full magic bytes and validated structure. */
    High,
}

/**
 * Byte order used in the media file.
 */
@Serializable
enum class Endianness {
    Big,
    Little,
}

/**
 * MIME type string.
 */
@Serializable
@JvmInline
value class MimeType(val value: String) {
    init {
        require(value.contains('/')) { "MimeType must contain '/', was '$value'" }
    }

    override fun toString(): String = value
}

/**
 * Complete identity of a detected media file.
 */
data class MediaIdentity(
    val format: MediaFormat<*, *>,
    val confidence: Confidence,
    val containerFamily: ContainerFamily? = format.containerFamily,
    val mimeType: MimeType? = MimeType(format.mimeType),
    val brands: List<Brand> = emptyList(),
    val signatures: List<MagicSignature> = emptyList(),
    val endianness: Endianness? = null,
    val fileSize: ByteLength? = null,
)
