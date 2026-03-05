@file:Suppress("MagicNumber", "TooManyFunctions")

package dev.transmute.testing.dsl

import dev.transmute.image.AlphaSemantics
import dev.transmute.image.ByteArrayPixelBuffer
import dev.transmute.image.ColorInfo
import dev.transmute.image.ImageIR
import dev.transmute.image.PixelFormat
import dev.transmute.testing.Color
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

// ═══════════════════════════════════════════════════════════════════════════════
//  Entry point
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Build a synthetic [ImageIR] using the image DSL.
 *
 * ```kotlin
 * // Solid red, 640×480
 * val img = syntheticImage {
 *     size(640, 480)
 *     solid(Color.RED)
 * }
 *
 * // Gradient with specified direction
 * val grad = syntheticImage {
 *     size(800, 600)
 *     gradient {
 *         direction = GradientDirection.RADIAL
 *         from = Color.WHITE
 *         to = Color.BLACK
 *     }
 * }
 *
 * // Per-pixel custom fill
 * val custom = syntheticImage {
 *     size(256, 256)
 *     pixels { x, y ->
 *         Color.fromHsv(x * 360f / width, y.toFloat() / height, 1f)
 *     }
 * }
 *
 * // Layered composition
 * val layered = syntheticImage {
 *     size(640, 480)
 *     layer { solid(Color.BLACK) }
 *     layer(opacity = 0.5f) {
 *         checkerboard { blockSize = 16 }
 *     }
 *     layer {
 *         border { width = 4; color = Color.RED }
 *     }
 * }
 * ```
 */
fun syntheticImage(block: ImageScope.() -> Unit): ImageIR =
  ImageScope().apply(block).build()

// ═══════════════════════════════════════════════════════════════════════════════
//  Root scope
// ═══════════════════════════════════════════════════════════════════════════════

@SyntheticMediaDsl
class ImageScope {
  /** Image width in pixels. */
  var width: Int = 100

  /** Image height in pixels. */
  var height: Int = 100

  /** Pixel format for the output buffer (default RGBA_8888). */
  var pixelFormat: PixelFormat = PixelFormat.RGBA_8888

  // ---- internal bookkeeping ----
  internal var renderer: PixelRenderer? = null
  internal val layers = mutableListOf<LayerEntry>()

  /** Set width and height in one call. */
  fun size(w: Int, h: Int) {
    width = w
    height = h
  }

  // ─────────────────────────── Solid fill ────────────────────────────

  /** Fill the entire image with a single [color]. */
  fun solid(color: Color) {
    renderer = SolidRenderer(color)
  }

  // ─────────────────────────── Gradients ─────────────────────────────

  /** Configure a gradient via sub-scope. */
  fun gradient(block: GradientScope.() -> Unit) {
    val g = GradientScope().apply(block)
    renderer = GradientRenderer(g.direction, g.from, g.to)
  }

  /** Shorthand: horizontal gradient from [from] to [to]. */
  fun horizontalGradient(from: Color = Color.BLACK, to: Color = Color.WHITE) {
    renderer = GradientRenderer(GradientDirection.HORIZONTAL, from, to)
  }

  /** Shorthand: vertical gradient from [from] to [to]. */
  fun verticalGradient(from: Color = Color.BLACK, to: Color = Color.WHITE) {
    renderer = GradientRenderer(GradientDirection.VERTICAL, from, to)
  }

  /** Shorthand: radial gradient from centre colour to edge colour. */
  fun radialGradient(center: Color = Color.WHITE, edge: Color = Color.BLACK) {
    renderer = GradientRenderer(GradientDirection.RADIAL, center, edge)
  }

  // ─────────────────────────── Patterns ──────────────────────────────

  /** Checkerboard pattern via sub-scope. */
  fun checkerboard(block: CheckerboardScope.() -> Unit = {}) {
    val c = CheckerboardScope().apply(block)
    renderer = CheckerboardRenderer(c.blockSize, c.colorA, c.colorB)
  }

  /** Horizontal or vertical stripe pattern. */
  fun stripes(block: StripeScope.() -> Unit) {
    val s = StripeScope().apply(block)
    renderer = StripeRenderer(s.horizontal, s.stripeSize, s.colorA, s.colorB)
  }

  /** Grid / crosshatch pattern. */
  fun grid(block: GridScope.() -> Unit) {
    val g = GridScope().apply(block)
    renderer = GridRenderer(g.cellSize, g.lineWidth, g.lineColor, g.bgColor)
  }

  /** SMPTE-style 7-bar colour bars. */
  fun colorBars() {
    renderer = ColorBarsRenderer
  }

  /** Grayscale ramp (black → white, left → right). */
  fun grayscaleRamp() {
    renderer = GrayscaleRampRenderer
  }

  /** Concentric-ring zone plate (spatial frequency test pattern). */
  fun zonePlate() {
    renderer = ZonePlateRenderer
  }

  /** Border frame with distinct inner fill. */
  fun border(block: BorderScope.() -> Unit) {
    val b = BorderScope().apply(block)
    renderer = BorderRenderer(b.width, b.color, b.fill)
  }

  // ─────────────────────────── Noise ─────────────────────────────────

  /** Pseudo-random noise (high-entropy, incompressible). */
  fun noise(seed: Long = 42L) {
    renderer = NoiseRenderer(seed)
  }

  // ─────────────────────────── Custom ────────────────────────────────

  /**
   * Arbitrary per-pixel generator.
   *
   * The lambda receives `(x, y)` pixel coordinates and must return a [Color].
   * Access [width] and [height] for normalised calculations.
   */
  fun pixels(block: ImageScope.(x: Int, y: Int) -> Color) {
    renderer = CustomRenderer(block)
  }

  // ─────────────────────────── Layers ────────────────────────────────

  /**
   * Add a compositing layer.
   *
   * Layers are alpha-composited in order (bottom → top). The first layer is the
   * base; subsequent layers are blended on top. Each layer is described with
   * its own [ImageScope].
   *
   * @param opacity Overall opacity multiplier for this layer (0.0–1.0).
   * @param blendMode Blend function (default [BlendMode.NORMAL]).
   */
  fun layer(
    opacity: Float = 1f,
    blendMode: BlendMode = BlendMode.NORMAL,
    block: ImageScope.() -> Unit,
  ) {
    val inner = ImageScope().apply {
      width = this@ImageScope.width
      height = this@ImageScope.height
      pixelFormat = this@ImageScope.pixelFormat
      block()
    }
    layers += LayerEntry(inner, opacity, blendMode)
  }

  // ─────────────────────────── Build ─────────────────────────────────

  internal fun build(): ImageIR {
    val bpp = pixelFormat.bytesPerPixel
    val stride = width * bpp
    val data: ByteArray

    if (layers.isNotEmpty()) {
      // Multi-layer composition
      data = ByteArray(height * stride)
      for (entry in layers) {
        val layerData = entry.scope.renderToBytes()
        compositeLayer(data, layerData, width, height, bpp, stride, entry.opacity, entry.blendMode)
      }
    } else {
      data = renderToBytes()
    }

    val hasAlpha = data.indices.step(bpp).any { i ->
      bpp >= 4 && (data[i + 3].toInt() and 0xFF) < 255
    }

    return ImageIR(
      buffer = ByteArrayPixelBuffer(data),
      width = width,
      height = height,
      stride = stride,
      pixelFormat = pixelFormat,
      alphaSemantics = if (hasAlpha) AlphaSemantics.STRAIGHT else AlphaSemantics.OPAQUE,
      colorInfo = ColorInfo(),
    )
  }

  internal fun renderToBytes(): ByteArray {
    val bpp = pixelFormat.bytesPerPixel
    val stride = width * bpp
    val data = ByteArray(height * stride)
    val r = renderer ?: SolidRenderer(Color.BLACK)
    for (y in 0 until height) {
      for (x in 0 until width) {
        val c = r.colorAt(this, x, y)
        val off = y * stride + x * bpp
        data[off] = c.r.toByte()
        if (bpp > 1) data[off + 1] = c.g.toByte()
        if (bpp > 2) data[off + 2] = c.b.toByte()
        if (bpp > 3) data[off + 3] = c.a.toByte()
      }
    }
    return data
  }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Sub-scopes
// ═══════════════════════════════════════════════════════════════════════════════

/** Configuration for a gradient. */
@SyntheticMediaDsl
class GradientScope {
  var direction: GradientDirection = GradientDirection.HORIZONTAL
  var from: Color = Color.BLACK
  var to: Color = Color.WHITE
}

/** Configuration for a checkerboard. */
@SyntheticMediaDsl
class CheckerboardScope {
  var blockSize: Int = 8
  var colorA: Color = Color.WHITE
  var colorB: Color = Color.BLACK
}

/** Configuration for stripes. */
@SyntheticMediaDsl
class StripeScope {
  /** If true → horizontal stripes; if false → vertical. */
  var horizontal: Boolean = true
  var stripeSize: Int = 8
  var colorA: Color = Color.WHITE
  var colorB: Color = Color.BLACK
}

/** Configuration for a grid overlay. */
@SyntheticMediaDsl
class GridScope {
  var cellSize: Int = 32
  var lineWidth: Int = 1
  var lineColor: Color = Color.BLACK
  var bgColor: Color = Color.WHITE
}

/** Configuration for a border frame. */
@SyntheticMediaDsl
class BorderScope {
  var width: Int = 4
  var color: Color = Color.RED
  var fill: Color = Color.WHITE
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Pixel renderers (internal)
// ═══════════════════════════════════════════════════════════════════════════════

internal fun interface PixelRenderer {
  fun colorAt(scope: ImageScope, x: Int, y: Int): Color
}

internal class SolidRenderer(private val color: Color) : PixelRenderer {
  override fun colorAt(scope: ImageScope, x: Int, y: Int) = color
}

internal class GradientRenderer(
  private val dir: GradientDirection,
  private val from: Color,
  private val to: Color,
) : PixelRenderer {
  override fun colorAt(scope: ImageScope, x: Int, y: Int): Color {
    val t = when (dir) {
      GradientDirection.HORIZONTAL ->
        if (scope.width > 1) x.toFloat() / (scope.width - 1) else 0.5f
      GradientDirection.VERTICAL ->
        if (scope.height > 1) y.toFloat() / (scope.height - 1) else 0.5f
      GradientDirection.DIAGONAL -> {
        val max = ((scope.width - 1) + (scope.height - 1)).coerceAtLeast(1).toFloat()
        (x + y) / max
      }
      GradientDirection.RADIAL -> {
        val cx = (scope.width - 1) / 2f
        val cy = (scope.height - 1) / 2f
        val dx = x - cx
        val dy = y - cy
        val maxR = sqrt(cx * cx + cy * cy).coerceAtLeast(1f)
        (sqrt(dx * dx + dy * dy) / maxR).coerceIn(0f, 1f)
      }
    }
    return Color.lerp(from, to, t)
  }
}

internal class CheckerboardRenderer(
  private val blockSize: Int,
  private val colorA: Color,
  private val colorB: Color,
) : PixelRenderer {
  override fun colorAt(scope: ImageScope, x: Int, y: Int): Color =
    if (((x / blockSize) + (y / blockSize)) % 2 == 0) colorA else colorB
}

internal class StripeRenderer(
  private val horizontal: Boolean,
  private val stripeSize: Int,
  private val colorA: Color,
  private val colorB: Color,
) : PixelRenderer {
  override fun colorAt(scope: ImageScope, x: Int, y: Int): Color {
    val idx = if (horizontal) y else x
    return if ((idx / stripeSize) % 2 == 0) colorA else colorB
  }
}

internal class GridRenderer(
  private val cellSize: Int,
  private val lineWidth: Int,
  private val lineColor: Color,
  private val bgColor: Color,
) : PixelRenderer {
  override fun colorAt(scope: ImageScope, x: Int, y: Int): Color =
    if ((x % cellSize) < lineWidth || (y % cellSize) < lineWidth) lineColor else bgColor
}

internal data object ColorBarsRenderer : PixelRenderer {
  private val bars = listOf(
    Color.WHITE, Color.YELLOW, Color.CYAN, Color.GREEN,
    Color.MAGENTA, Color.RED, Color.BLUE,
  )

  override fun colorAt(scope: ImageScope, x: Int, y: Int): Color {
    val idx = (x * bars.size / scope.width.coerceAtLeast(1)).coerceIn(0, bars.size - 1)
    return bars[idx]
  }
}

internal data object GrayscaleRampRenderer : PixelRenderer {
  override fun colorAt(scope: ImageScope, x: Int, y: Int): Color {
    val v = if (scope.width > 1) (x * 255 / (scope.width - 1)).coerceIn(0, 255) else 128
    return Color.gray(v)
  }
}

internal data object ZonePlateRenderer : PixelRenderer {
  override fun colorAt(scope: ImageScope, x: Int, y: Int): Color {
    val cx = scope.width / 2f
    val cy = scope.height / 2f
    val scale = 0.5f / maxOf(cx, cy)
    val dx = (x - cx) * scale
    val dy = (y - cy) * scale
    val r2 = dx * dx + dy * dy
    val v = ((sin(r2 * 200.0 * PI) + 1.0) * 127.5).roundToInt().coerceIn(0, 255)
    return Color.gray(v)
  }
}

internal class BorderRenderer(
  private val borderWidth: Int,
  private val borderColor: Color,
  private val fillColor: Color,
) : PixelRenderer {
  override fun colorAt(scope: ImageScope, x: Int, y: Int): Color {
    val isBorder = x < borderWidth || x >= scope.width - borderWidth ||
      y < borderWidth || y >= scope.height - borderWidth
    return if (isBorder) borderColor else fillColor
  }
}

internal class NoiseRenderer(private val seed: Long) : PixelRenderer {
  override fun colorAt(scope: ImageScope, x: Int, y: Int): Color {
    // Deterministic per-pixel noise via seeded hash
    val rng = Random(seed + y.toLong() * scope.width + x)
    return Color(rng.nextInt(256), rng.nextInt(256), rng.nextInt(256))
  }
}

internal class CustomRenderer(private val fn: ImageScope.(Int, Int) -> Color) : PixelRenderer {
  override fun colorAt(scope: ImageScope, x: Int, y: Int): Color = scope.fn(x, y)
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Layer compositing (internal)
// ═══════════════════════════════════════════════════════════════════════════════

internal data class LayerEntry(
  val scope: ImageScope,
  val opacity: Float,
  val blendMode: BlendMode,
)

/**
 * Composite [src] onto [dst] in-place using the given [blendMode] and [opacity].
 */
internal fun compositeLayer(
  dst: ByteArray,
  src: ByteArray,
  width: Int,
  height: Int,
  bpp: Int,
  stride: Int,
  opacity: Float,
  blendMode: BlendMode,
) {
  for (y in 0 until height) {
    for (x in 0 until width) {
      val off = y * stride + x * bpp
      val sr = src[off].toInt() and 0xFF
      val sg = src[off + 1].toInt() and 0xFF
      val sb = src[off + 2].toInt() and 0xFF
      val sa = if (bpp >= 4) ((src[off + 3].toInt() and 0xFF) * opacity).roundToInt().coerceIn(0, 255) else 255

      val dr = dst[off].toInt() and 0xFF
      val dg = dst[off + 1].toInt() and 0xFF
      val db = dst[off + 2].toInt() and 0xFF
      val da = if (bpp >= 4) dst[off + 3].toInt() and 0xFF else 255

      val (blendR, blendG, blendB) = when (blendMode) {
        BlendMode.NORMAL -> Triple(sr, sg, sb)
        BlendMode.MULTIPLY -> Triple(sr * dr / 255, sg * dg / 255, sb * db / 255)
        BlendMode.SCREEN -> Triple(255 - (255 - sr) * (255 - dr) / 255, 255 - (255 - sg) * (255 - dg) / 255, 255 - (255 - sb) * (255 - db) / 255)
        BlendMode.ADD -> Triple((sr + dr).coerceAtMost(255), (sg + dg).coerceAtMost(255), (sb + db).coerceAtMost(255))
      }

      // Alpha composite (source-over)
      val srcA = sa / 255f
      val dstA = da / 255f
      val outA = srcA + dstA * (1f - srcA)
      if (outA > 0f) {
        dst[off] = ((blendR * srcA + dr * dstA * (1f - srcA)) / outA).roundToInt().coerceIn(0, 255).toByte()
        dst[off + 1] = ((blendG * srcA + dg * dstA * (1f - srcA)) / outA).roundToInt().coerceIn(0, 255).toByte()
        dst[off + 2] = ((blendB * srcA + db * dstA * (1f - srcA)) / outA).roundToInt().coerceIn(0, 255).toByte()
        if (bpp >= 4) dst[off + 3] = (outA * 255).roundToInt().coerceIn(0, 255).toByte()
      }
    }
  }
}
