package dev.transmute.model.core

/**
 * Base exception for all Transmute model validation errors.
 *
 * Subclasses provide context-specific error messages so callers can
 * catch precisely the category of failure they care about.
 */
open class TransmuteModelException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

/**
 * Thrown when a value violates the range or sign invariant of a numeric
 * model primitive (e.g. negative [DurationMicros], zero [Hertz]).
 */
class InvalidNumericValueException(
    message: String,
    cause: Throwable? = null,
) : TransmuteModelException(message, cause)

/**
 * Thrown when a string value violates the character-range constraint of
 * a string model primitive (e.g. non-ASCII in [AsciiString]).
 */
class InvalidStringEncodingException(
    message: String,
    cause: Throwable? = null,
) : TransmuteModelException(message, cause)

/**
 * Thrown when a byte offset, length, or range is logically invalid
 * (e.g. negative offset, zero-length range, end before start).
 */
class InvalidByteRangeException(
    message: String,
    cause: Throwable? = null,
) : TransmuteModelException(message, cause)

/**
 * Thrown when a container-level identifier value is out of its allowed
 * range (e.g. [FourCC] byte outside 0x00–0xFF).
 */
class InvalidIdentifierException(
    message: String,
    cause: Throwable? = null,
) : TransmuteModelException(message, cause)
