@file:Suppress("unused")

package dev.transmute.model.core

import kotlinx.serialization.Serializable

// --- String wrapper types ---

/** UTF-8 encoded string value. */
@Serializable
@JvmInline
value class Utf8String(val value: String) {
    override fun toString(): String = value
}

/** ASCII-only string value (all chars in 0x00..0x7F). */
@Serializable
@JvmInline
value class AsciiString(val value: String) {
    init {
        if (!value.all { it.code in 0x00..0x7F }) {
            throw InvalidStringEncodingException(
                "AsciiString contains non-ASCII characters: '${value}'"
            )
        }
    }

    override fun toString(): String = value
}

/** ISO 8859-1 (Latin-1) string value (all chars in 0x00..0xFF). */
@Serializable
@JvmInline
value class Latin1String(val value: String) {
    init {
        if (!value.all { it.code in 0x00..0xFF }) {
            throw InvalidStringEncodingException(
                "Latin1String contains characters outside Latin-1 range (0x00..0xFF): '${value}'"
            )
        }
    }

    override fun toString(): String = value
}

/** URI string. No structural validation is performed — this is a semantic wrapper only. */
@Serializable
@JvmInline
value class UriString(val value: String) {
    override fun toString(): String = value
}

/** ISO 8601 formatted date/time string. */
@Serializable
@JvmInline
value class Iso8601String(val value: String) {
    override fun toString(): String = value
}

/** IETF BCP 47 language tag (e.g. "en", "de-AT"). No structural validation is performed. */
@Serializable
@JvmInline
value class LanguageTag(val value: String) {
    override fun toString(): String = value
}

// --- Numeric primitives ---

/** Duration in microseconds. */
@Serializable
@JvmInline
value class DurationMicros(val value: Long) {
    init {
        if (value < 0) {
            throw InvalidNumericValueException(
                "DurationMicros must be non-negative, was $value"
            )
        }
    }

    override fun toString(): String = "DurationMicros($value)"
}

/** Frequency in Hertz (e.g. sample rate). */
@Serializable
@JvmInline
value class Hertz(val value: Int) {
    init {
        if (value <= 0) {
            throw InvalidNumericValueException("Hertz must be positive, was $value")
        }
    }

    override fun toString(): String = "${value}Hz"
}

/** Number of audio channels. */
@Serializable
@JvmInline
value class Channels(val value: Int) {
    init {
        if (value <= 0) {
            throw InvalidNumericValueException("Channels must be positive, was $value")
        }
    }

    override fun toString(): String = "Channels($value)"
}

/** Bits per sample (audio bit depth). */
@Serializable
@JvmInline
value class BitsPerSample(val value: Int) {
    init {
        if (value <= 0) {
            throw InvalidNumericValueException("BitsPerSample must be positive, was $value")
        }
    }

    override fun toString(): String = "${value}bit"
}

/** Dimension in pixels (width or height). */
@Serializable
@JvmInline
value class Pixels(val value: Int) {
    init {
        if (value <= 0) {
            throw InvalidNumericValueException("Pixels must be positive, was $value")
        }
    }

    override fun toString(): String = "${value}px"
}

/** Rational number represented as numerator/denominator. */
@Serializable
data class Rational(
    val numerator: Int,
    val denominator: Int,
) {
    init {
        if (denominator == 0) {
            throw InvalidNumericValueException("Rational denominator must not be zero")
        }
    }

    fun toDouble(): Double = numerator.toDouble() / denominator.toDouble()

    override fun toString(): String = "$numerator/$denominator"
}

/** Bitrate in bits per second. */
@Serializable
@JvmInline
value class Bitrate(val bitsPerSecond: Long) {
    init {
        if (bitsPerSecond < 0) {
            throw InvalidNumericValueException(
                "Bitrate must be non-negative, was $bitsPerSecond"
            )
        }
    }

    override fun toString(): String = "${bitsPerSecond}bps"
}
