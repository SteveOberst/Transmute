package dev.transmute.image

/**
 * Lightweight metadata snapshot for an image item.
 *
 * Used with [dev.transmute.ImageTransmuter.wouldTransmute] to determine whether a configured
 * transmuter would produce any change on an image without decoding it.
 * All properties are nullable - `null` means the value is unknown, and the
 * transmuter will conservatively assume the transform *might* apply.
 *
 * ```kotlin
 * val transmuter = Transmute.image {
 *     scale(1080, 1080)
 *     encode { options(JpegEncodeOptions(quality = 0.85f)) }
 * }
 *
 * val hint = ImageHint(width = item.width, height = item.height, format = item.format)
 * if (transmuter.wouldTransmute(hint)) {
 *     val compressed = transmuter.transmute(item.bytes.asBytes())
 * }
 * ```
 */
data class ImageHint(
    /** Width of the image in pixels, or `null` if unknown. */
    val width: Int? = null,
    /** Height of the image in pixels, or `null` if unknown. */
    val height: Int? = null,
    /** Detected or declared format, or `null` if unknown. */
    val format: ImageFormat? = null,
    /** Encoded file size in bytes, or `null` if unknown. */
    val sizeBytes: Long? = null,
)
