package dev.transmute.common

/**
 * Global runtime configuration for the Transmute library.
 *
 * Every property has a sensible default so the library works out of the box
 * without any configuration.  Override properties **before** performing any
 * conversions – typically during application initialisation.
 *
 * ---
 *
 * **Migration notice:** This global singleton is being phased out in favour of
 * [TransmuteContext], which provides the same configuration as an immutable,
 * explicit parameter.  Prefer:
 *
 * ```kotlin
 * val ctx = TransmuteContext {
 *     logger = TransmuteLogging.printLogger(LogLevel.DEBUG)
 * }
 * Transmute.image { context(ctx) }.transmute(source)
 * ```
 *
 * @see TransmuteContext
 */
@Deprecated(
    message = "TransmuteConfig is being phased out. Use TransmuteContext or the instance-based Transmute { } builder with plugins instead.",
    level = DeprecationLevel.WARNING,
)
object TransmuteConfig
