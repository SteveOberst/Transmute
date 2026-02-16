package dev.transmute.image

import dev.transmute.image.ImageTestHelpers.horizontalGradient
import dev.transmute.image.ImageTestHelpers.pixelAt
import dev.transmute.image.ImageTestHelpers.solidColor
import dev.transmute.image.ImageTestHelpers.testContext
import dev.transmute.image.transform.ImageFlipTransform
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertSame

class ImageFlipTransformTest {

  private val context = testContext()

  @Test
  fun horizontalFlipReversesColumns() = runTest {
    // 4×1 image: pixels [Red, Green, Blue, White]
    val w = 4; val h = 1; val bpp = 4
    val data = byteArrayOf(
      255.toByte(), 0, 0, -1, // Red
      0, 255.toByte(), 0, -1, // Green
      0, 0, 255.toByte(), -1, // Blue
      255.toByte(), 255.toByte(), 255.toByte(), -1, // White
    )
    val input = ImageIR(
      buffer = ByteArrayPixelBuffer(data),
      width = w, height = h, stride = w * bpp,
      pixelFormat = PixelFormat.RGBA_8888,
      alphaSemantics = AlphaSemantics.OPAQUE,
      colorInfo = ColorInfo(),
    )

    val result = ImageFlipTransform(horizontal = true, vertical = false).apply(input, context)

    assertContentEquals(intArrayOf(255, 255, 255, 255), pixelAt(result, 0, 0)) // White
    assertContentEquals(intArrayOf(0, 0, 255, 255), pixelAt(result, 1, 0))     // Blue
    assertContentEquals(intArrayOf(0, 255, 0, 255), pixelAt(result, 2, 0))     // Green
    assertContentEquals(intArrayOf(255, 0, 0, 255), pixelAt(result, 3, 0))     // Red
  }

  @Test
  fun verticalFlipReversesRows() = runTest {
    // 1×3 image: rows [Red, Green, Blue]
    val w = 1; val h = 3; val bpp = 4
    val data = byteArrayOf(
      255.toByte(), 0, 0, -1,
      0, 255.toByte(), 0, -1,
      0, 0, 255.toByte(), -1,
    )
    val input = ImageIR(
      buffer = ByteArrayPixelBuffer(data),
      width = w, height = h, stride = w * bpp,
      pixelFormat = PixelFormat.RGBA_8888,
      alphaSemantics = AlphaSemantics.OPAQUE,
      colorInfo = ColorInfo(),
    )

    val result = ImageFlipTransform(horizontal = false, vertical = true).apply(input, context)

    assertContentEquals(intArrayOf(0, 0, 255, 255), pixelAt(result, 0, 0))   // Blue (was row 2)
    assertContentEquals(intArrayOf(0, 255, 0, 255), pixelAt(result, 0, 1))   // Green (unchanged)
    assertContentEquals(intArrayOf(255, 0, 0, 255), pixelAt(result, 0, 2))   // Red (was row 0)
  }

  @Test
  fun bothFlipsEqualRotate180() = runTest {
    val input = horizontalGradient(20, 10, startR = 0, endR = 255)
    val result = ImageFlipTransform(horizontal = true, vertical = true).apply(input, context)

    // Top-left of result corresponds to bottom-right of input
    val topLeft = pixelAt(result, 0, 0)
    val origBottomRight = pixelAt(input, 19, 9)
    assertContentEquals(origBottomRight, topLeft)
  }

  @Test
  fun noFlipReturnsSameInstance() = runTest {
    val input = solidColor(10, 10, r = 128, g = 64, b = 32)
    val result = ImageFlipTransform(horizontal = false, vertical = false).apply(input, context)

    assertSame(input, result)
  }

  @Test
  fun doubleHorizontalFlipRestoresOriginal() = runTest {
    val input = horizontalGradient(30, 10)
    val flip = ImageFlipTransform(horizontal = true, vertical = false)

    val flipped = flip.apply(input, context)
    val restored = flip.apply(flipped, context)

    val origBuf = (input.buffer as ByteArrayPixelBuffer).data
    val resBuf = (restored.buffer as ByteArrayPixelBuffer).data
    assertContentEquals(origBuf, resBuf)
  }

  @Test
  fun preservesDimensions() = runTest {
    val input = solidColor(47, 31, r = 100, g = 100, b = 100)
    val result = ImageFlipTransform(horizontal = true, vertical = true).apply(input, context)

    assertEquals(47, result.width)
    assertEquals(31, result.height)
  }
}
