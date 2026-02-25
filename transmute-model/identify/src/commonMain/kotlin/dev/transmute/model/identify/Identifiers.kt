@file:Suppress("unused")

package dev.transmute.model.identify

/**
 * ISO base media file format four-character code.
 * Each character must be in the 0x00..0xFF range to accommodate
 * legacy atoms like `©nam`.
 */
import dev.transmute.model.core.InvalidIdentifierException
import kotlinx.serialization.Serializable

@Serializable
@JvmInline
value class FourCC(val value: String) {
    init {
        if (value.length != 4) {
            throw InvalidIdentifierException(
                "FourCC must be exactly 4 characters, was '${value}'"
            )
        }
        if (!value.all { it.code in 0x00..0xFF }) {
            throw InvalidIdentifierException(
                "FourCC characters must be in 0x00..0xFF range, got '${value}'"
            )
        }
    }

    override fun toString(): String = value
}

/**
 * ISO base media file format brand (e.g. "isom", "mp41").
 */
@Serializable
@JvmInline
value class Brand(val fourCC: FourCC) {
    override fun toString(): String = fourCC.toString()

    companion object {
        /** Create a [Brand] from a raw 4-character string. */
        operator fun invoke(value: String): Brand = Brand(FourCC(value))
    }
}

/**
 * RIFF chunk identifier (4 ASCII characters).
 */
@Serializable
@JvmInline
value class RiffChunkId(val value: String) {
    init {
        if (value.length != 4) {
            throw InvalidIdentifierException(
                "RiffChunkId must be exactly 4 characters, was '${value}'"
            )
        }
    }

    override fun toString(): String = value
}

/**
 * EBML element identifier.
 */
@Serializable
@JvmInline
value class EbmlId(val value: Long) {
    init {
        if (value <= 0) {
            throw InvalidIdentifierException("EbmlId must be positive, was $value")
        }
    }

    override fun toString(): String = "0x${value.toString(16).uppercase()}"
}

/**
 * File magic bytes signature used for format detection.
 */
@Serializable
@JvmInline
value class MagicSignature(val bytes: ByteArray) {
    init {
        if (bytes.isEmpty()) {
            throw InvalidIdentifierException("MagicSignature must not be empty")
        }
    }

    override fun toString(): String = bytes.joinToString("") { "%02X".format(it) }
}
