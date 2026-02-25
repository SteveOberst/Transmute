@file:Suppress("unused")

package dev.transmute.model.metadata

import dev.transmute.model.core.Iso8601String
import dev.transmute.model.core.LanguageTag
import dev.transmute.model.core.Rational
import dev.transmute.model.core.Utf8String

// --- MetadataValue interface ---

/**
 * Value of a metadata field. Open for extension.
 */
interface MetadataValue

/** UTF-8 text value. */
@JvmInline
value class TextValue(val text: Utf8String) : MetadataValue {
    override fun toString(): String = text.value

    companion object {
        operator fun invoke(value: String): TextValue = TextValue(Utf8String(value))
    }
}

/** Integer value. */
@JvmInline
value class IntValue(val value: Long) : MetadataValue {
    override fun toString(): String = value.toString()
}

/** Floating-point value. */
@JvmInline
value class FloatValue(val value: Double) : MetadataValue {
    override fun toString(): String = value.toString()
}

/** Rational number value. */
@JvmInline
value class RationalValue(val value: Rational) : MetadataValue {
    override fun toString(): String = value.toString()
}

/** Boolean value. */
@JvmInline
value class BoolValue(val value: Boolean) : MetadataValue {
    override fun toString(): String = value.toString()
}

/** Raw binary data. */
@JvmInline
value class BinaryValue(val data: ByteArray) : MetadataValue {
    override fun toString(): String = "BinaryValue(${data.size} bytes)"
}

/** ISO 8601 date/time value. */
@JvmInline
value class DateTimeValue(val value: Iso8601String) : MetadataValue {
    override fun toString(): String = value.value

    companion object {
        operator fun invoke(isoString: String): DateTimeValue = DateTimeValue(Iso8601String(isoString))
    }
}

/** Language-tagged text value. */
data class LocalizedTextValue(
    val text: Utf8String,
    val language: LanguageTag,
) : MetadataValue {
    override fun toString(): String = "${text.value} [${language.value}]"

    companion object {
        operator fun invoke(text: String, language: String): LocalizedTextValue =
            LocalizedTextValue(Utf8String(text), LanguageTag(language))
    }
}

/** GPS coordinates. */
data class GpsCoordinateValue(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double? = null,
) : MetadataValue {
    override fun toString(): String = buildString {
        append("GPS($latitude, $longitude")
        if (altitude != null) append(", alt=$altitude")
        append(")")
    }
}

/** List of values. */
@JvmInline
value class ListValue(val values: List<MetadataValue>) : MetadataValue {
    override fun toString(): String = values.toString()
}

/** Structured nested key-value data. */
@JvmInline
value class StructValue(val fields: Map<String, MetadataValue>) : MetadataValue {
    override fun toString(): String = fields.toString()
}

/** Image data embedded as metadata (e.g. album art, thumbnail). */
data class ImageValue(
    val mimeType: String,
    val data: ByteArray,
    val description: String? = null,
) : MetadataValue {
    override fun toString(): String = "ImageValue($mimeType, ${data.size} bytes)"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ImageValue) return false
        return mimeType == other.mimeType && data.contentEquals(other.data) && description == other.description
    }

    override fun hashCode(): Int {
        var result = mimeType.hashCode()
        result = 31 * result + data.contentHashCode()
        result = 31 * result + (description?.hashCode() ?: 0)
        return result
    }
}
