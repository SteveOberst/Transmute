@file:Suppress("MagicNumber")

package dev.transmute.testing

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Type-safe RGBA color for synthetic media generation.
 *
 * All channel values are in the range 0-255. Provides constants for common
 * colors, HSV conversion, interpolation, and blending helpers.
 *
 * ```kotlin
 * val red  = Color.RED
 * val half = Color.BLUE.withAlpha(128)
 * val mix  = Color.lerp(Color.RED, Color.BLUE, 0.5f)   // purple
 * val warm = Color.fromHsv(30f, 0.8f, 1f)              // orange
 * ```
 */
data class Color(val r: Int, val g: Int, val b: Int, val a: Int = 255) {

  init {
    require(r in 0..255) { "Red must be 0–255, got $r" }
    require(g in 0..255) { "Green must be 0–255, got $g" }
    require(b in 0..255) { "Blue must be 0–255, got $b" }
    require(a in 0..255) { "Alpha must be 0–255, got $a" }
  }

  /** Return a copy with a different alpha. */
  fun withAlpha(alpha: Int): Color = copy(a = alpha)

  /** Convert to `[R, G, B, A]` array (0-255 each). */
  fun toRgbaArray(): IntArray = intArrayOf(r, g, b, a)

  /** Rec.601 luminance: Y = 0.299.R + 0.587.G + 0.114.B. */
  val luminance: Double get() = 0.299 * r + 0.587 * g + 0.114 * b

  /** Whether this color is fully opaque. */
  val isOpaque: Boolean get() = a == 255

  /** Invert RGB channels (alpha unchanged). */
  fun inverted(): Color = Color(255 - r, 255 - g, 255 - b, a)

  /** Multiply RGB by a scalar (clamped to 0-255). */
  operator fun times(factor: Float): Color = Color(
    r = (r * factor).roundToInt().coerceIn(0, 255),
    g = (g * factor).roundToInt().coerceIn(0, 255),
    b = (b * factor).roundToInt().coerceIn(0, 255),
    a = a,
  )

  companion object {
    val BLACK = Color(0, 0, 0)
    val WHITE = Color(255, 255, 255)
    val RED = Color(255, 0, 0)
    val GREEN = Color(0, 255, 0)
    val BLUE = Color(0, 0, 255)
    val YELLOW = Color(255, 255, 0)
    val CYAN = Color(0, 255, 255)
    val MAGENTA = Color(255, 0, 255)
    val ORANGE = Color(255, 165, 0)
    val GRAY = Color(128, 128, 128)
    val DARK_GRAY = Color(64, 64, 64)
    val LIGHT_GRAY = Color(192, 192, 192)
    val TRANSPARENT = Color(0, 0, 0, 0)

    /** Grayscale where R = G = B = [value]. */
    fun gray(value: Int, alpha: Int = 255): Color = Color(value, value, value, alpha)

    /**
     * Linear interpolation between [a] and [b].
     *
     * [t] is clamped to 0-1; 0.0 -> [a], 1.0 -> [b].
     */
    fun lerp(a: Color, b: Color, t: Float): Color {
      val ct = t.coerceIn(0f, 1f)
      return Color(
        r = lerpCh(a.r, b.r, ct),
        g = lerpCh(a.g, b.g, ct),
        b = lerpCh(a.b, b.b, ct),
        a = lerpCh(a.a, b.a, ct),
      )
    }

    /**
     * Create a color from HSV values.
     *
     * @param h Hue in degrees (0-360).
     * @param s Saturation (0-1).
     * @param v Value / brightness (0-1).
     */
    fun fromHsv(h: Float, s: Float, v: Float, alpha: Int = 255): Color {
      val c = v * s
      val x = c * (1f - abs((h / 60f) % 2f - 1f))
      val m = v - c
      val (r1, g1, b1) = when {
        h < 60f -> Triple(c, x, 0f)
        h < 120f -> Triple(x, c, 0f)
        h < 180f -> Triple(0f, c, x)
        h < 240f -> Triple(0f, x, c)
        h < 300f -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
      }
      return Color(
        r = ((r1 + m) * 255).roundToInt().coerceIn(0, 255),
        g = ((g1 + m) * 255).roundToInt().coerceIn(0, 255),
        b = ((b1 + m) * 255).roundToInt().coerceIn(0, 255),
        a = alpha,
      )
    }

    /**
     * Alpha-composite [src] over [dst] using "source-over" blending.
     */
    fun alphaComposite(src: Color, dst: Color): Color {
      val sa = src.a / 255f
      val da = dst.a / 255f
      val outA = sa + da * (1f - sa)
      if (outA == 0f) return TRANSPARENT
      return Color(
        r = blendCh(src.r, dst.r, sa, da, outA),
        g = blendCh(src.g, dst.g, sa, da, outA),
        b = blendCh(src.b, dst.b, sa, da, outA),
        a = (outA * 255).roundToInt().coerceIn(0, 255),
      )
    }

    private fun lerpCh(a: Int, b: Int, t: Float): Int =
      (a + (b - a) * t).roundToInt().coerceIn(0, 255)

    private fun blendCh(s: Int, d: Int, sa: Float, da: Float, outA: Float): Int =
      ((s * sa + d * da * (1f - sa)) / outA).roundToInt().coerceIn(0, 255)
  }
}
