package dev.transmute.testing.dsl

// ---------------------------------------------------------------------------
// DSL marker
// ---------------------------------------------------------------------------

/**
 * DSL marker for the synthetic-media generation API.
 *
 * Prevents accidental access to an outer builder scope from an inner lambda.
 * Every scope class in the DSL is annotated with this marker so the Kotlin
 * compiler can enforce scope boundaries at compile time.
 */
@DslMarker
@Target(AnnotationTarget.CLASS)
annotation class SyntheticMediaDsl

// ---------------------------------------------------------------------------
// Unit-conversion extensions
// ---------------------------------------------------------------------------

/** Convert an [Int] millisecond value to [Long]: `500.ms` → 500L. */
inline val Int.ms: Long get() = toLong()

/** Convert an [Int] representing seconds to milliseconds: `2.seconds` → 2000L. */
inline val Int.seconds: Long get() = toLong() * 1000L

/** Convert a [Long] millisecond value (identity): `500L.ms` → 500L. */
inline val Long.ms: Long get() = this

/** Convert a [Double] representing seconds to milliseconds: `1.5.seconds` → 1500L. */
inline val Double.seconds: Long get() = (this * 1000).toLong()

/** Convert an [Int] to [Double] Hz: `440.hz` → 440.0. */
inline val Int.hz: Double get() = toDouble()

/** Identity Hz extension for [Double]: `440.5.hz` → 440.5. */
inline val Double.hz: Double get() = this

// ---------------------------------------------------------------------------
// Shared enums
// ---------------------------------------------------------------------------

/** Noise-generation algorithm. */
enum class NoiseType { WHITE, PINK }

/** Direction for a gradient fill. */
enum class GradientDirection { HORIZONTAL, VERTICAL, DIAGONAL, RADIAL }

/** Frequency-sweep type for chirp signals. */
enum class SweepType { LINEAR, LOGARITHMIC }

/** Blend mode used when compositing image layers. */
enum class BlendMode {
  /** Standard alpha-over compositing. */
  NORMAL,
  /** Multiply source × destination RGB. */
  MULTIPLY,
  /** Inverse-multiply (screen). */
  SCREEN,
  /** Additive blend (clamped to 255). */
  ADD,
}
