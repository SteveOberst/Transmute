package dev.transmute.image

import dev.transmute.image.ImageTestHelpers.checkerboard
import dev.transmute.image.ImageTestHelpers.horizontalGradient
import dev.transmute.image.ImageTestHelpers.pixelAt
import dev.transmute.image.ImageTestHelpers.solidColor
import dev.transmute.image.ImageTestHelpers.testContext
import dev.transmute.image.transform.ImageCropTransform
import dev.transmute.codec.pipeline.TransformId
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertSame

class ImageCropTransformTest {

  // --- Basic crop ---

  @Test
  fun cropCenterRegion() = runTest {
    // 100x100 solid blue, crop center 50x50
    val input = solidColor(100, 100, r = 0, g = 0, b = 255)
    val crop = ImageCropTransform(x = 25, y = 25, cropWidth = 50, cropHeight = 50)
    val result = crop.apply(input, testContext())

    assertEquals(50, result.width)
    assertEquals(50, result.height)
    assertEquals(50 * 4, result.stride)

    // All pixels should still be blue
    assertContentEquals(intArrayOf(0, 0, 255, 255), pixelAt(result, 0, 0))
    assertContentEquals(intArrayOf(0, 0, 255, 255), pixelAt(result, 49, 49))
  }

  @Test
  fun cropPreservesGradientValues() = runTest {
    // Horizontal gradient 256x10: R goes 0->255 left->right
    val input = horizontalGradient(256, 10, startR = 0, endR = 255, startG = 0, endG = 0, startB = 0, endB = 0)
    // Crop columns 100..199 -> the R value at x=0 in the crop should equal ~100
    val crop = ImageCropTransform(x = 100, y = 0, cropWidth = 100, cropHeight = 10)
    val result = crop.apply(input, testContext())

    assertEquals(100, result.width)
    assertEquals(10, result.height)

    // First pixel in crop corresponds to x=100 in original
    val left = pixelAt(result, 0, 5)
    assertEquals(100, left[0], "Crop left edge R should be ~100 (source x=100)")

    // Last pixel in crop corresponds to x=199 in original
    val right = pixelAt(result, 99, 5)
    assertEquals(199, right[0], "Crop right edge R should be ~199 (source x=199)")
  }

  @Test
  fun cropTopLeftCorner() = runTest {
    val input = checkerboard(100, 100, blockSize = 10,
      colorA = intArrayOf(255, 0, 0, 255),
      colorB = intArrayOf(0, 255, 0, 255),
    )
    val crop = ImageCropTransform(x = 0, y = 0, cropWidth = 10, cropHeight = 10)
    val result = crop.apply(input, testContext())

    assertEquals(10, result.width)
    assertEquals(10, result.height)

    // Top-left 10x10 block should be all red (colorA)
    assertContentEquals(intArrayOf(255, 0, 0, 255), pixelAt(result, 0, 0))
    assertContentEquals(intArrayOf(255, 0, 0, 255), pixelAt(result, 9, 9))
  }

  @Test
  fun cropBottomRightCorner() = runTest {
    val input = solidColor(200, 150, r = 42, g = 84, b = 168)
    val crop = ImageCropTransform(x = 150, y = 100, cropWidth = 50, cropHeight = 50)
    val result = crop.apply(input, testContext())

    assertEquals(50, result.width)
    assertEquals(50, result.height)
    assertContentEquals(intArrayOf(42, 84, 168, 255), pixelAt(result, 25, 25))
  }

  // --- Full image crop (no-op) ---

  @Test
  fun cropFullImageReturnsOriginal() = runTest {
    val input = solidColor(100, 100, r = 50, g = 100, b = 200)
    val crop = ImageCropTransform(x = 0, y = 0, cropWidth = 100, cropHeight = 100)
    val result = crop.apply(input, testContext())
    assertSame(input, result, "Cropping to full size should return same instance")
  }

  // --- Clamping ---

  @Test
  fun cropBeyondBoundsGetsClamped() = runTest {
    val input = solidColor(50, 50, r = 200, g = 100, b = 50)
    // Request extends beyond edge: x=40, cropWidth=100 -> clamped to cropWidth=10
    val crop = ImageCropTransform(x = 40, y = 40, cropWidth = 100, cropHeight = 100)
    val result = crop.apply(input, testContext())

    assertEquals(10, result.width, "Should clamp width to image boundary")
    assertEquals(10, result.height, "Should clamp height to image boundary")
    assertContentEquals(intArrayOf(200, 100, 50, 255), pixelAt(result, 0, 0))
  }

  @Test
  fun cropNegativeCoordinatesClamped() = runTest {
    val input = solidColor(50, 50, r = 100, g = 100, b = 100)
    // Negative x gets clamped to 0
    val crop = ImageCropTransform(x = -10, y = -10, cropWidth = 20, cropHeight = 20)
    val result = crop.apply(input, testContext())

    assertEquals(20, result.width)
    assertEquals(20, result.height)
  }

  @Test
  fun cropZeroSizeReturnsOriginal() = runTest {
    val input = solidColor(50, 50, r = 100, g = 100, b = 100)
    val crop = ImageCropTransform(x = 50, y = 50, cropWidth = 10, cropHeight = 10)
    // x=50 is clamped, then cropWidth = min(10, 50-50) = 0 -> returns original
    val result = crop.apply(input, testContext())
    assertSame(input, result, "Zero-size crop should return original")
  }

  // --- Buffer integrity ---

  @Test
  fun cropBufferSizeMatchesDimensions() = runTest {
    val input = solidColor(300, 200, r = 128, g = 128, b = 128)
    val crop = ImageCropTransform(x = 50, y = 50, cropWidth = 100, cropHeight = 80)
    val result = crop.apply(input, testContext())

    val buffer = result.buffer as ByteArrayPixelBuffer
    val expectedSize = result.width * result.height * result.pixelFormat.bytesPerPixel
    assertEquals(expectedSize, buffer.data.size, "Buffer size must match wxhxbpp")
  }

  @Test
  fun transformId() {
    assertEquals(TransformId("image-crop"), ImageCropTransform(0, 0, 1, 1).id)
  }
}
