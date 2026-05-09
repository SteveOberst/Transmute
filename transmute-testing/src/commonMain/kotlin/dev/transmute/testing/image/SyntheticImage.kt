@file:Suppress("MagicNumber")

package dev.transmute.testing.image

import dev.transmute.image.AlphaSemantics
import dev.transmute.image.ByteArrayPixelBuffer
import dev.transmute.image.ColorInfo
import dev.transmute.image.ImageIR
import dev.transmute.image.PixelFormat
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Convenience shortcuts for generating synthetic [ImageIR] instances.
 *
 * Each function creates a fully-formed [ImageIR] with a single call.
 * For more flexible, composable image generation - layer composition,
 * custom per-pixel generators, blend modes, and gradient configuration -
 * use the **image DSL**:
 *
 * ```kotlin
 * import dev.transmute.testing.dsl.*
 *
 * val image = syntheticImage {
 *     size(640, 480)
 *     layer { solid(Color.BLACK) }
 *     layer(opacity = 0.5f) { checkerboard { blockSize = 16 } }
 *     layer { border { width = 4; color = Color.RED } }
 * }
 * ```
 *
 * ### Quick start (static helpers)
 * ```kotlin
 * val red  = SyntheticImage.solidColor(100, 100, r = 255, g = 0, b = 0)
 * val grad = SyntheticImage.horizontalGradient(200, 100)
 * val bars = SyntheticImage.colorBars(640, 480)
 * ```
 *
 * ### Design notes
 * - All images use [PixelFormat.RGBA_8888] for maximum compatibility.
 * - Alpha channel is set to 255 (opaque) unless explicitly specified otherwise.
 * - Pixel values are clamped to 0-255.
 *
 * @see dev.transmute.testing.dsl.syntheticImage
 */
object SyntheticImage {

  // ---
  // Solid fills
  // ---

  /**
   * Solid-color fill.
   *
   * Useful for verifying that encoders/decoders preserve uniform regions,
   * and for checking pixel-level color accuracy through lossy codecs.
   */
  fun solidColor(
    width: Int,
    height: Int,
    r: Int,
    g: Int,
    b: Int,
    a: Int = 255,
  ): ImageIR {
    val bpp = 4
    val stride = width * bpp
    val data = ByteArray(height * stride)
    for (y in 0 until height) {
      for (x in 0 until width) {
        val off = y * stride + x * bpp
        data[off] = r.toByte()
        data[off + 1] = g.toByte()
        data[off + 2] = b.toByte()
        data[off + 3] = a.toByte()
      }
    }
    return imageIR(data, width, height, stride, a)
  }

  // ---
  // Gradients
  // ---

  /**
   * Horizontal gradient from left color to right color.
   *
   * Useful for detecting banding artefacts from quantization and verifying
   * that scaling preserves smooth transitions.
   */
  fun horizontalGradient(
    width: Int,
    height: Int,
    startR: Int = 0,
    startG: Int = 0,
    startB: Int = 0,
    endR: Int = 255,
    endG: Int = 255,
    endB: Int = 255,
  ): ImageIR {
    val bpp = 4
    val stride = width * bpp
    val data = ByteArray(height * stride)
    for (y in 0 until height) {
      for (x in 0 until width) {
        val t = if (width > 1) x.toFloat() / (width - 1) else 0f
        val off = y * stride + x * bpp
        data[off] = lerp(startR, endR, t).toByte()
        data[off + 1] = lerp(startG, endG, t).toByte()
        data[off + 2] = lerp(startB, endB, t).toByte()
        data[off + 3] = 0xFF.toByte()
      }
    }
    return imageIR(data, width, height, stride)
  }

  /**
   * Vertical gradient from top color to bottom color.
   */
  fun verticalGradient(
    width: Int,
    height: Int,
    startR: Int = 0,
    startG: Int = 0,
    startB: Int = 0,
    endR: Int = 255,
    endG: Int = 255,
    endB: Int = 255,
  ): ImageIR {
    val bpp = 4
    val stride = width * bpp
    val data = ByteArray(height * stride)
    for (y in 0 until height) {
      val t = if (height > 1) y.toFloat() / (height - 1) else 0f
      for (x in 0 until width) {
        val off = y * stride + x * bpp
        data[off] = lerp(startR, endR, t).toByte()
        data[off + 1] = lerp(startG, endG, t).toByte()
        data[off + 2] = lerp(startB, endB, t).toByte()
        data[off + 3] = 0xFF.toByte()
      }
    }
    return imageIR(data, width, height, stride)
  }

  /**
   * Diagonal gradient from top-left color to bottom-right color.
   *
   * Exercises both axes simultaneously - useful for rotation and
   * transpose testing.
   */
  fun diagonalGradient(
    width: Int,
    height: Int,
    startR: Int = 0,
    startG: Int = 0,
    startB: Int = 0,
    endR: Int = 255,
    endG: Int = 255,
    endB: Int = 255,
  ): ImageIR {
    val bpp = 4
    val stride = width * bpp
    val data = ByteArray(height * stride)
    val maxDist = (width - 1 + height - 1).coerceAtLeast(1).toFloat()
    for (y in 0 until height) {
      for (x in 0 until width) {
        val t = (x + y) / maxDist
        val off = y * stride + x * bpp
        data[off] = lerp(startR, endR, t).toByte()
        data[off + 1] = lerp(startG, endG, t).toByte()
        data[off + 2] = lerp(startB, endB, t).toByte()
        data[off + 3] = 0xFF.toByte()
      }
    }
    return imageIR(data, width, height, stride)
  }

  /**
   * Radial gradient from center color to edge color.
   *
   * Exercises radial symmetry preservation in transforms and detects
   * directional compression artefacts.
   */
  fun radialGradient(
    width: Int,
    height: Int,
    centerR: Int = 255,
    centerG: Int = 255,
    centerB: Int = 255,
    edgeR: Int = 0,
    edgeG: Int = 0,
    edgeB: Int = 0,
  ): ImageIR {
    val bpp = 4
    val stride = width * bpp
    val data = ByteArray(height * stride)
    val cx = (width - 1) / 2f
    val cy = (height - 1) / 2f
    val maxRadius = sqrt(cx * cx + cy * cy).coerceAtLeast(1f)
    for (y in 0 until height) {
      for (x in 0 until width) {
        val dx = x - cx
        val dy = y - cy
        val t = (sqrt(dx * dx + dy * dy) / maxRadius).coerceIn(0f, 1f)
        val off = y * stride + x * bpp
        data[off] = lerp(centerR, edgeR, t).toByte()
        data[off + 1] = lerp(centerG, edgeG, t).toByte()
        data[off + 2] = lerp(centerB, edgeB, t).toByte()
        data[off + 3] = 0xFF.toByte()
      }
    }
    return imageIR(data, width, height, stride)
  }

  // ---
  // Patterns
  // ---

  /**
   * Checkerboard pattern of alternating colors.
   *
   * Useful for detecting boundary smearing, high-frequency detail loss,
   * and scaling artefacts.
   *
   * @param blockSize Size of each checker square in pixels.
   */
  fun checkerboard(
    width: Int,
    height: Int,
    blockSize: Int = 8,
    colorA: IntArray = intArrayOf(255, 255, 255, 255),
    colorB: IntArray = intArrayOf(0, 0, 0, 255),
  ): ImageIR {
    require(colorA.size == 4 && colorB.size == 4) { "Colors must be RGBA (4 elements)" }
    val bpp = 4
    val stride = width * bpp
    val data = ByteArray(height * stride)
    for (y in 0 until height) {
      for (x in 0 until width) {
        val isA = ((x / blockSize) + (y / blockSize)) % 2 == 0
        val c = if (isA) colorA else colorB
        val off = y * stride + x * bpp
        data[off] = c[0].toByte()
        data[off + 1] = c[1].toByte()
        data[off + 2] = c[2].toByte()
        data[off + 3] = c[3].toByte()
      }
    }
    return imageIR(data, width, height, stride)
  }

  /**
   * Horizontal stripe pattern.
   *
   * Alternating horizontal bars of two colors, [stripeHeight] pixels each.
   */
  fun horizontalStripes(
    width: Int,
    height: Int,
    stripeHeight: Int = 8,
    colorA: IntArray = intArrayOf(255, 255, 255, 255),
    colorB: IntArray = intArrayOf(0, 0, 0, 255),
  ): ImageIR {
    require(colorA.size == 4 && colorB.size == 4) { "Colors must be RGBA (4 elements)" }
    val bpp = 4
    val stride = width * bpp
    val data = ByteArray(height * stride)
    for (y in 0 until height) {
      val c = if ((y / stripeHeight) % 2 == 0) colorA else colorB
      for (x in 0 until width) {
        val off = y * stride + x * bpp
        data[off] = c[0].toByte()
        data[off + 1] = c[1].toByte()
        data[off + 2] = c[2].toByte()
        data[off + 3] = c[3].toByte()
      }
    }
    return imageIR(data, width, height, stride)
  }

  /**
   * Vertical stripe pattern.
   *
   * Alternating vertical bars of two colors, [stripeWidth] pixels each.
   */
  fun verticalStripes(
    width: Int,
    height: Int,
    stripeWidth: Int = 8,
    colorA: IntArray = intArrayOf(255, 255, 255, 255),
    colorB: IntArray = intArrayOf(0, 0, 0, 255),
  ): ImageIR {
    require(colorA.size == 4 && colorB.size == 4) { "Colors must be RGBA (4 elements)" }
    val bpp = 4
    val stride = width * bpp
    val data = ByteArray(height * stride)
    for (y in 0 until height) {
      for (x in 0 until width) {
        val c = if ((x / stripeWidth) % 2 == 0) colorA else colorB
        val off = y * stride + x * bpp
        data[off] = c[0].toByte()
        data[off + 1] = c[1].toByte()
        data[off + 2] = c[2].toByte()
        data[off + 3] = c[3].toByte()
      }
    }
    return imageIR(data, width, height, stride)
  }

  /**
   * Grid pattern (crosshatch) - useful for alignment and scaling tests.
   *
   * Draws [lineColor] lines on a [bgColor] background at regular intervals.
   *
   * @param cellSize Spacing between grid lines in pixels.
   * @param lineWidth Width of each grid line in pixels.
   */
  fun grid(
    width: Int,
    height: Int,
    cellSize: Int = 32,
    lineWidth: Int = 1,
    bgColor: IntArray = intArrayOf(255, 255, 255, 255),
    lineColor: IntArray = intArrayOf(0, 0, 0, 255),
  ): ImageIR {
    require(bgColor.size == 4 && lineColor.size == 4) { "Colors must be RGBA (4 elements)" }
    val bpp = 4
    val stride = width * bpp
    val data = ByteArray(height * stride)
    for (y in 0 until height) {
      for (x in 0 until width) {
        val onGridH = (y % cellSize) < lineWidth
        val onGridV = (x % cellSize) < lineWidth
        val c = if (onGridH || onGridV) lineColor else bgColor
        val off = y * stride + x * bpp
        data[off] = c[0].toByte()
        data[off + 1] = c[1].toByte()
        data[off + 2] = c[2].toByte()
        data[off + 3] = c[3].toByte()
      }
    }
    return imageIR(data, width, height, stride)
  }

  /**
   * Border frame - solid outer border with a different inner fill.
   *
   * Useful for verifying that crop coordinates are pixel-accurate.
   *
   * @param borderWidth Width of the border in pixels.
   */
  fun border(
    width: Int,
    height: Int,
    borderWidth: Int = 4,
    borderColor: IntArray = intArrayOf(255, 0, 0, 255),
    fillColor: IntArray = intArrayOf(255, 255, 255, 255),
  ): ImageIR {
    require(borderColor.size == 4 && fillColor.size == 4) { "Colors must be RGBA (4 elements)" }
    val bpp = 4
    val stride = width * bpp
    val data = ByteArray(height * stride)
    for (y in 0 until height) {
      for (x in 0 until width) {
        val isBorder = x < borderWidth || x >= width - borderWidth ||
          y < borderWidth || y >= height - borderWidth
        val c = if (isBorder) borderColor else fillColor
        val off = y * stride + x * bpp
        data[off] = c[0].toByte()
        data[off + 1] = c[1].toByte()
        data[off + 2] = c[2].toByte()
        data[off + 3] = c[3].toByte()
      }
    }
    return imageIR(data, width, height, stride)
  }

  // ---
  // Test cards & reference patterns
  // ---

  /**
   * SMPTE-style color bars (simplified 7-bar pattern).
   *
   * Seven equal-width vertical bars: white, yellow, cyan, green, magenta, red, blue.
   * Useful for verifying that codecs handle diverse colors across the spectrum
   * and for visual inspection of codec output.
   */
  fun colorBars(width: Int, height: Int): ImageIR {
    val bars = listOf(
      intArrayOf(255, 255, 255, 255), // White
      intArrayOf(255, 255, 0, 255),   // Yellow
      intArrayOf(0, 255, 255, 255),   // Cyan
      intArrayOf(0, 255, 0, 255),     // Green
      intArrayOf(255, 0, 255, 255),   // Magenta
      intArrayOf(255, 0, 0, 255),     // Red
      intArrayOf(0, 0, 255, 255),     // Blue
    )
    val bpp = 4
    val stride = width * bpp
    val data = ByteArray(height * stride)
    for (y in 0 until height) {
      for (x in 0 until width) {
        val barIndex = (x * bars.size / width).coerceIn(0, bars.size - 1)
        val c = bars[barIndex]
        val off = y * stride + x * bpp
        data[off] = c[0].toByte()
        data[off + 1] = c[1].toByte()
        data[off + 2] = c[2].toByte()
        data[off + 3] = c[3].toByte()
      }
    }
    return imageIR(data, width, height, stride)
  }

  /**
   * Grayscale ramp from black (left) to white (right).
   *
   * 256 distinct levels across the width. Useful for testing tonal
   * reproduction and gamma handling.
   */
  fun grayscaleRamp(width: Int, height: Int): ImageIR {
    val bpp = 4
    val stride = width * bpp
    val data = ByteArray(height * stride)
    for (y in 0 until height) {
      for (x in 0 until width) {
        val v = if (width > 1) (x * 255 / (width - 1)).coerceIn(0, 255) else 128
        val off = y * stride + x * bpp
        data[off] = v.toByte()
        data[off + 1] = v.toByte()
        data[off + 2] = v.toByte()
        data[off + 3] = 0xFF.toByte()
      }
    }
    return imageIR(data, width, height, stride)
  }

  /**
   * Color wheel - hue varies by angle from center, saturation by radius.
   *
   * Useful for verifying color space handling and hue preservation.
   */
  fun colorWheel(width: Int, height: Int): ImageIR {
    val bpp = 4
    val stride = width * bpp
    val data = ByteArray(height * stride)
    val cx = (width - 1) / 2f
    val cy = (height - 1) / 2f
    val maxR = minOf(cx, cy).coerceAtLeast(1f)
    for (y in 0 until height) {
      for (x in 0 until width) {
        val dx = x - cx
        val dy = y - cy
        val r = sqrt(dx * dx + dy * dy) / maxR
        val off = y * stride + x * bpp
        if (r > 1f) {
          // Outside the wheel - transparent black
          data[off] = 0; data[off + 1] = 0; data[off + 2] = 0; data[off + 3] = 0
        } else {
          val hue = (kotlin.math.atan2(dy, dx) / PI.toFloat() + 1f) / 2f // 0..1
          val sat = r
          val rgb = hsvToRgb(hue * 360f, sat, 1f)
          data[off] = rgb[0].toByte()
          data[off + 1] = rgb[1].toByte()
          data[off + 2] = rgb[2].toByte()
          data[off + 3] = 0xFF.toByte()
        }
      }
    }
    return imageIR(data, width, height, stride, alpha = 0)
  }

  // ---
  // Noise & stress patterns
  // ---

  /**
   * Pseudo-random noise pattern.
   *
   * Useful for testing worst-case compression (incompressible data)
   * and verifying that codecs handle high-entropy input.
   *
   * @param seed Deterministic RNG seed for reproducible runs.
   */
  fun noise(width: Int, height: Int, seed: Long = 42L): ImageIR {
    val rng = Random(seed)
    val bpp = 4
    val stride = width * bpp
    val data = ByteArray(height * stride)
    for (i in data.indices step bpp) {
      data[i] = rng.nextInt(256).toByte()
      data[i + 1] = rng.nextInt(256).toByte()
      data[i + 2] = rng.nextInt(256).toByte()
      data[i + 3] = 0xFF.toByte()
    }
    return imageIR(data, width, height, stride)
  }

  /**
   * Noise with alpha variation - random RGBA values including alpha.
   *
   * Useful for testing alpha channel preservation through codecs.
   *
   * @param seed Deterministic RNG seed for reproducible runs.
   */
  fun noiseWithAlpha(width: Int, height: Int, seed: Long = 42L): ImageIR {
    val rng = Random(seed)
    val bpp = 4
    val stride = width * bpp
    val data = ByteArray(height * stride)
    for (i in data.indices step bpp) {
      data[i] = rng.nextInt(256).toByte()
      data[i + 1] = rng.nextInt(256).toByte()
      data[i + 2] = rng.nextInt(256).toByte()
      data[i + 3] = rng.nextInt(256).toByte()
    }
    return imageIR(data, width, height, stride, alpha = 0)
  }

  /**
   * Concentric rings pattern (zone plate).
   *
   * Classic test pattern for evaluating spatial frequency response - aliasing
   * and moire artefacts become visible at high frequencies. The pattern is a
   * sinusoidal function of squared distance from center.
   */
  fun zonePlate(width: Int, height: Int): ImageIR {
    val bpp = 4
    val stride = width * bpp
    val data = ByteArray(height * stride)
    val cx = width / 2f
    val cy = height / 2f
    val scale = 0.5f / maxOf(cx, cy)
    for (y in 0 until height) {
      for (x in 0 until width) {
        val dx = (x - cx) * scale
        val dy = (y - cy) * scale
        val r2 = dx * dx + dy * dy
        val v = ((sin(r2 * 200f * PI.toFloat()) + 1f) * 127.5f).roundToInt().coerceIn(0, 255)
        val off = y * stride + x * bpp
        data[off] = v.toByte()
        data[off + 1] = v.toByte()
        data[off + 2] = v.toByte()
        data[off + 3] = 0xFF.toByte()
      }
    }
    return imageIR(data, width, height, stride)
  }

  // ---
  // Alpha test patterns
  // ---

  /**
   * Horizontal alpha gradient - opaque on the left, transparent on the right,
   * over a solid [r], [g], [b] fill.
   *
   * Useful for testing alpha channel encode/decode fidelity.
   */
  fun alphaGradient(
    width: Int,
    height: Int,
    r: Int = 255,
    g: Int = 0,
    b: Int = 0,
  ): ImageIR {
    val bpp = 4
    val stride = width * bpp
    val data = ByteArray(height * stride)
    for (y in 0 until height) {
      for (x in 0 until width) {
        val a = if (width > 1) 255 - (x * 255 / (width - 1)) else 255
        val off = y * stride + x * bpp
        data[off] = r.toByte()
        data[off + 1] = g.toByte()
        data[off + 2] = b.toByte()
        data[off + 3] = a.toByte()
      }
    }
    return imageIR(data, width, height, stride, alpha = 0)
  }

  // ---
  // Geometry test patterns
  // ---

  /**
   * Single-pixel dot at the given coordinates on a solid background.
   *
   * Useful for verifying exact coordinate handling in crop/scale operations.
   *
   * @param dotX X coordinate of the dot.
   * @param dotY Y coordinate of the dot.
   */
  fun singleDot(
    width: Int,
    height: Int,
    dotX: Int,
    dotY: Int,
    dotColor: IntArray = intArrayOf(255, 0, 0, 255),
    bgColor: IntArray = intArrayOf(0, 0, 0, 255),
  ): ImageIR {
    require(dotColor.size == 4 && bgColor.size == 4) { "Colors must be RGBA (4 elements)" }
    val bpp = 4
    val stride = width * bpp
    val data = ByteArray(height * stride)
    for (y in 0 until height) {
      for (x in 0 until width) {
        val c = if (x == dotX && y == dotY) dotColor else bgColor
        val off = y * stride + x * bpp
        data[off] = c[0].toByte()
        data[off + 1] = c[1].toByte()
        data[off + 2] = c[2].toByte()
        data[off + 3] = c[3].toByte()
      }
    }
    return imageIR(data, width, height, stride)
  }

  /**
   * Quadrant image - each quarter of the image is a different color.
   *
   * Useful for rotation and flip tests: each quadrant is distinguishable.
   *
   * Layout (default): TL=Red, TR=Green, BL=Blue, BR=Yellow.
   */
  fun quadrants(
    width: Int,
    height: Int,
    topLeft: IntArray = intArrayOf(255, 0, 0, 255),
    topRight: IntArray = intArrayOf(0, 255, 0, 255),
    bottomLeft: IntArray = intArrayOf(0, 0, 255, 255),
    bottomRight: IntArray = intArrayOf(255, 255, 0, 255),
  ): ImageIR {
    val bpp = 4
    val stride = width * bpp
    val data = ByteArray(height * stride)
    val mx = width / 2
    val my = height / 2
    for (y in 0 until height) {
      for (x in 0 until width) {
        val c = when {
          x < mx && y < my -> topLeft
          x >= mx && y < my -> topRight
          x < mx && y >= my -> bottomLeft
          else -> bottomRight
        }
        val off = y * stride + x * bpp
        data[off] = c[0].toByte()
        data[off + 1] = c[1].toByte()
        data[off + 2] = c[2].toByte()
        data[off + 3] = c[3].toByte()
      }
    }
    return imageIR(data, width, height, stride)
  }

  // ---
  // Internal helpers
  // ---

  private fun lerp(start: Int, end: Int, t: Float): Int =
    (start + (end - start) * t).roundToInt().coerceIn(0, 255)

  /**
   * HSV to RGB conversion. H in 0-360, S and V in 0-1.
   * Returns IntArray of [R, G, B] in 0-255.
   */
  private fun hsvToRgb(h: Float, s: Float, v: Float): IntArray {
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
    return intArrayOf(
      ((r1 + m) * 255).roundToInt().coerceIn(0, 255),
      ((g1 + m) * 255).roundToInt().coerceIn(0, 255),
      ((b1 + m) * 255).roundToInt().coerceIn(0, 255),
    )
  }

  private fun imageIR(
    data: ByteArray,
    width: Int,
    height: Int,
    stride: Int,
    alpha: Int = 255,
  ): ImageIR = ImageIR(
    buffer = ByteArrayPixelBuffer(data),
    width = width,
    height = height,
    stride = stride,
    pixelFormat = PixelFormat.RGBA_8888,
    alphaSemantics = if (alpha < 255) AlphaSemantics.STRAIGHT else AlphaSemantics.OPAQUE,
    colorInfo = ColorInfo(),
  )
}
