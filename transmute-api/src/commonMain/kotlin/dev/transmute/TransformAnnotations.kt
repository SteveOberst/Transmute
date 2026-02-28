package dev.transmute

/**
 * Marks a transform factory function for runtime discovery by the playground server.
 *
 * Placed on functions in [ImageTransforms], [AudioTransforms], and
 * [VideoTransforms] objects. The server uses Kotlin reflection to scan
 * these at startup and build the `/api/transforms` catalog dynamically —
 * no hardcoded lists anywhere.
 *
 * ```kotlin
 * @TransformDescriptor("scale", "Proportionally scale to fit within bounds")
 * fun scale(
 *     @Param("Maximum width in pixels", required = true) maxWidth: Int,
 *     @Param("Maximum height in pixels", required = true) maxHeight: Int,
 * ) = ImageScaleTransform(maxWidth, maxHeight)
 * ```
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class TransformDescriptor(
    /** Stable ID used in API requests (e.g. "scale", "resize"). */
    val id: String,
    /** Human-readable description shown in the UI. */
    val description: String,
)

/**
 * Documents a single parameter on an annotated transform factory function.
 *
 * Each annotated parameter maps to a [ParameterSchema][dev.transmute.playground.shared.ParameterSchema]
 * returned by `/api/transforms`. Parameters without this annotation are
 * omitted from the API, which is useful for optional/advanced overloads.
 *
 * @param description Human-readable explanation shown in the UI.
 * @param required Whether this parameter must be supplied (no default).
 * @param default Default value as a string (empty = no default displayed).
 * @param min Minimum allowed value as a string (for numeric types).
 * @param max Maximum allowed value as a string (for numeric types).
 * @param enumValues Comma-separated list of allowed values (auto-detected for enum params).
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class Param(
    val description: String = "",
    val required: Boolean = false,
    val default: String = "",
    val min: String = "",
    val max: String = "",
    val enumValues: String = "",
)
