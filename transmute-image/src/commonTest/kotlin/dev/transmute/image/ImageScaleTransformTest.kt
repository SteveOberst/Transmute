package dev.transmute.image

import dev.transmute.image.ImageTestHelpers.checkerboard
import dev.transmute.image.ImageTestHelpers.horizontalGradient
import dev.transmute.image.ImageTestHelpers.peakDifference
import dev.transmute.image.ImageTestHelpers.pixelAt
import dev.transmute.image.ImageTestHelpers.solidColor
import dev.transmute.image.ImageTestHelpers.testContext
import dev.transmute.image.transform.ImageScaleTransform
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ImageScaleTransformTest {

  // --- fitDimensions ---

  @Test
  fun fitDimensionsScalesDownToMaxWidth() {
    val (w, h) = ImageScaleTransform.fitDimensions(4000, 3000, 1920, 1080)
    // 4000×3000 → scale by min(1920/4000, 1080/3000) = min(0.48, 0.36) = 0.36
    assertEquals(1440, w)
    assertEquals(1080, h)
  }

  @Test
  fun fitDimensionsScalesDownToMaxHeight() {
    val (w, h) = ImageScaleTransform.fitDimensions(1920, 3000, 1920, 1080)
    // scale by min(1920/1920, 1080/3000) = min(1.0, 0.36) = 0.36
    assertEquals(691, w)
    assertEquals(1080, h)
  }

  @Test
  fun fitDimensionsPreservesAspectRatio() {
    val (w, h) = ImageScaleTransform.fitDimensions(1600, 900, 800, 600)
    // scale = min(800/1600, 600/900) = min(0.5, 0.667) = 0.5
    assertEquals(800, w)
    assertEquals(450, h)
  }

  @Test
  fun fitDimensionsAtLeast1x1() {
    val (w, h) = ImageScaleTransform.fitDimensions(10000, 1, 1, 1)
    assertTrue(w >= 1)
    assertTrue(h >= 1)
  }

  // --- No upscale ---

  @Test
  fun noUpscaleWhenAlreadySmaller() = runTest {
    val input = solidColor(100, 100, r = 128, g = 64, b = 32)
    val transform = ImageScaleTransform(maxWidth = 200, maxHeight = 200)
    val result = transform.apply(input, testContext())
    assertSame(input, result, "Should return the same instance when no scaling needed")
  }

  @Test
  fun noUpscaleWhenExactlyAtBounds() = runTest {
    val input = solidColor(200, 200, r = 128, g = 64, b = 32)
    val transform = ImageScaleTransform(maxWidth = 200, maxHeight = 200)
    val result = transform.apply(input, testContext())
    assertSame(input, result)
  }

  // --- Solid color downscale ---

  @Test
  fun solidColorDownscalePreservesColor() = runTest {
    val input = solidColor(400, 300, r = 200, g = 100, b = 50)
    val transform = ImageScaleTransform(maxWidth = 200, maxHeight = 150)
    val result = transform.apply(input, testContext())

    assertEquals(200, result.width)
    assertEquals(150, result.height)
    assertEquals(PixelFormat.RGBA_8888, result.pixelFormat)

    // Every pixel should be exactly (200, 100, 50, 255) since the source is uniform.
    val pixel = pixelAt(result, 50, 50)
    assertContentEquals(intArrayOf(200, 100, 50, 255), pixel,
      "Solid color should be preserved exactly after downscale")

    // Check corners too
    assertContentEquals(intArrayOf(200, 100, 50, 255), pixelAt(result, 0, 0))
    assertContentEquals(intArrayOf(200, 100, 50, 255), pixelAt(result, 199, 149))
  }

  // --- Gradient downscale ---

  @Test
  fun gradientDownscaleProducesSmootherGradient() = runTest {
    // 800×600 → maxWidth=200, maxHeight=150 → scale=0.25 → 200×150
    val input = horizontalGradient(800, 600, startR = 0, endR = 255)
    val transform = ImageScaleTransform(maxWidth = 200, maxHeight = 150)
    val result = transform.apply(input, testContext())

    assertEquals(200, result.width)
    assertEquals(150, result.height)

    // Left edge should still be dark
    val left = pixelAt(result, 0, 75)
    assertTrue(left[0] < 10, "Left edge R should be near 0, got ${left[0]}")

    // Right edge should still be bright
    val right = pixelAt(result, 199, 75)
    assertTrue(right[0] > 245, "Right edge R should be near 255, got ${right[0]}")

    // Middle should be roughly 128
    val mid = pixelAt(result, 100, 75)
    assertTrue(mid[0] in 120..136, "Middle R should be near 128, got ${mid[0]}")
  }

  // --- Checkerboard downscale ---

  @Test
  fun checkerboardDownscaleProducesValidOutput() = runTest {
    val input = checkerboard(400, 400, blockSize = 8)
    val transform = ImageScaleTransform(maxWidth = 100, maxHeight = 100)
    val result = transform.apply(input, testContext())

    assertEquals(100, result.width)
    assertEquals(100, result.height)

    // The output should be a valid pixel buffer of the right size.
    val buffer = result.buffer as ByteArrayPixelBuffer
    assertEquals(100 * 100 * 4, buffer.data.size)
  }

  // --- Non-square aspect ratio ---

  @Test
  fun landscapeImageScaledCorrectly() = runTest {
    val input = solidColor(1920, 1080, r = 50, g = 150, b = 250)
    val transform = ImageScaleTransform(maxWidth = 960, maxHeight = 540)
    val result = transform.apply(input, testContext())

    assertEquals(960, result.width)
    assertEquals(540, result.height)
  }

  @Test
  fun portraitImageScaledCorrectly() = runTest {
    val input = solidColor(1080, 1920, r = 50, g = 150, b = 250)
    val transform = ImageScaleTransform(maxWidth = 540, maxHeight = 960)
    val result = transform.apply(input, testContext())

    assertEquals(540, result.width)
    assertEquals(960, result.height)
  }

  @Test
  fun wideLandscapeConstrainedByWidth() = runTest {
    // 3000×500 → maxWidth constrains: scale = 1000/3000 = 0.333
    val input = solidColor(3000, 500, r = 100, g = 200, b = 50)
    val transform = ImageScaleTransform(maxWidth = 1000, maxHeight = 1000)
    val result = transform.apply(input, testContext())

    assertEquals(1000, result.width)
    assertEquals(167, result.height)
  }

  // --- Stride correctness ---

  @Test
  fun outputStrideMatchesWidthTimesBpp() = runTest {
    val input = solidColor(800, 600, r = 128, g = 128, b = 128)
    val transform = ImageScaleTransform(maxWidth = 320, maxHeight = 240)
    val result = transform.apply(input, testContext())

    assertEquals(320 * 4, result.stride, "Stride should be width × bytesPerPixel")
  }

  // --- 2× downscale pixel accuracy ---

  @Test
  fun halfSizeDownscalePixelAccuracy() = runTest {
    // Create a 4×4 image with known pixels:
    //   Row 0: (255,0,0) (255,0,0) (0,255,0) (0,255,0)
    //   Row 1: (255,0,0) (255,0,0) (0,255,0) (0,255,0)
    //   Row 2: (0,0,255) (0,0,255) (255,255,0) (255,255,0)
    //   Row 3: (0,0,255) (0,0,255) (255,255,0) (255,255,0)
    val bpp = 4
    val stride = 4 * bpp
    val data = ByteArray(4 * stride)

    fun setPixel(x: Int, y: Int, r: Int, g: Int, b: Int) {
      val off = y * stride + x * bpp
      data[off] = r.toByte()
      data[off + 1] = g.toByte()
      data[off + 2] = b.toByte()
      data[off + 3] = 0xFF.toByte()
    }
    setPixel(0, 0, 255, 0, 0); setPixel(1, 0, 255, 0, 0); setPixel(2, 0, 0, 255, 0); setPixel(3, 0, 0, 255, 0)
    setPixel(0, 1, 255, 0, 0); setPixel(1, 1, 255, 0, 0); setPixel(2, 1, 0, 255, 0); setPixel(3, 1, 0, 255, 0)
    setPixel(0, 2, 0, 0, 255); setPixel(1, 2, 0, 0, 255); setPixel(2, 2, 255, 255, 0); setPixel(3, 2, 255, 255, 0)
    setPixel(0, 3, 0, 0, 255); setPixel(1, 3, 0, 0, 255); setPixel(2, 3, 255, 255, 0); setPixel(3, 3, 255, 255, 0)

    val input = ImageIR(
      buffer = ByteArrayPixelBuffer(data),
      width = 4, height = 4, stride = stride,
      pixelFormat = PixelFormat.RGBA_8888,
      alphaSemantics = AlphaSemantics.OPAQUE,
      colorInfo = ColorInfo(),
    )

    // Scale to 2×2 - each output pixel covers a 2×2 block of identical source pixels.
    // With bilinear interpolation sampling at (0,0), (3,0), (0,3), (3,3) in source:
    val transform = ImageScaleTransform(maxWidth = 2, maxHeight = 2)
    val result = transform.apply(input, testContext())

    assertEquals(2, result.width)
    assertEquals(2, result.height)

    // For a 4→2 downscale with bilinear, source coords map:
    // dst(0,0) → src(0,0) = red (255,0,0)
    // dst(1,0) → src(3,0) = green (0,255,0)
    // dst(0,1) → src(0,3) = blue (0,0,255)
    // dst(1,1) → src(3,3) = yellow (255,255,0)
    val topLeft = pixelAt(result, 0, 0)
    val topRight = pixelAt(result, 1, 0)
    val botLeft = pixelAt(result, 0, 1)
    val botRight = pixelAt(result, 1, 1)

    assertContentEquals(intArrayOf(255, 0, 0, 255), topLeft, "Top-left should be red")
    assertContentEquals(intArrayOf(0, 255, 0, 255), topRight, "Top-right should be green")
    assertContentEquals(intArrayOf(0, 0, 255, 255), botLeft, "Bottom-left should be blue")
    assertContentEquals(intArrayOf(255, 255, 0, 255), botRight, "Bottom-right should be yellow")
  }
}
