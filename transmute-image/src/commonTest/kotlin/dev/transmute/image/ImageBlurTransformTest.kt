package dev.transmute.image

import dev.transmute.image.ImageTestHelpers.pixelAt
import dev.transmute.image.ImageTestHelpers.solidColor
import dev.transmute.image.ImageTestHelpers.testContext
import dev.transmute.image.transform.ImageBlurTransform
import kotlinx.coroutines.test.runTest
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ImageBlurTransformTest {

  private val context = testContext()

  @Test
  fun solidColorUnchangedByBlur() = runTest {
    val input = solidColor(20, 20, r = 128, g = 64, b = 200)
    val result = ImageBlurTransform(radius = 3).apply(input, context)

    // A solid-color image should remain identical regardless of blur radius.
    val p = pixelAt(result, 10, 10)
    assertEquals(128, p[0])
    assertEquals(64, p[1])
    assertEquals(200, p[2])
  }

  @Test
  fun blurReducesSharpEdge() = runTest {
    // Left half white, right half black - sharp edge at center.
    val w = 20; val h = 10; val bpp = 4
    val data = ByteArray(w * h * bpp)
    for (y in 0 until h) {
      for (x in 0 until w) {
        val off = (y * w + x) * bpp
        val v = if (x < w / 2) 255 else 0
        data[off] = v.toByte()
        data[off + 1] = v.toByte()
        data[off + 2] = v.toByte()
        data[off + 3] = 0xFF.toByte()
      }
    }
    val input = ImageIR(
      buffer = ByteArrayPixelBuffer(data),
      width = w, height = h, stride = w * bpp,
      pixelFormat = PixelFormat.RGBA_8888,
      alphaSemantics = AlphaSemantics.OPAQUE,
      colorInfo = ColorInfo(),
    )

    val result = ImageBlurTransform(radius = 2).apply(input, context)

    // Center pixel (at the edge) should be somewhere between 0 and 255.
    val center = pixelAt(result, w / 2, h / 2)
    assertTrue(
      center[0] > 20 && center[0] < 235,
      "Edge pixel should be blurred, got ${center[0]}"
    )

    // Pixels far from the edge should remain close to original.
    val farLeft = pixelAt(result, 2, h / 2)
    assertTrue(farLeft[0] > 240, "Far left should still be ~white, got ${farLeft[0]}")

    val farRight = pixelAt(result, w - 3, h / 2)
    assertTrue(farRight[0] < 15, "Far right should still be ~black, got ${farRight[0]}")
  }

  @Test
  fun preservesDimensions() = runTest {
    val input = solidColor(33, 17, r = 100, g = 100, b = 100)
    val result = ImageBlurTransform(radius = 5).apply(input, context)

    assertEquals(33, result.width)
    assertEquals(17, result.height)
  }

  @Test
  fun preservesAlpha() = runTest {
    val input = solidColor(10, 10, r = 100, g = 100, b = 100, a = 128)
    val result = ImageBlurTransform(radius = 2).apply(input, context)

    val p = pixelAt(result, 5, 5)
    // Alpha passes through the blur too, but for a solid alpha the value stays the same.
    assertEquals(128, p[3])
  }

  @Test
  fun largerRadiusBlursMore() = runTest {
    // Create a single white pixel in a black image.
    val w = 21; val h = 21; val bpp = 4
    val data = ByteArray(w * h * bpp)
    val cx = w / 2; val cy = h / 2
    val off = (cy * w + cx) * bpp
    data[off] = 255.toByte(); data[off + 1] = 255.toByte(); data[off + 2] = 255.toByte(); data[off + 3] = 255.toByte()
    // Set alpha for all other pixels so blur doesn't produce 0-alpha
    for (y in 0 until h) {
      for (x in 0 until w) {
        val i = (y * w + x) * bpp
        data[i + 3] = 255.toByte()
      }
    }

    val input = ImageIR(
      buffer = ByteArrayPixelBuffer(data),
      width = w, height = h, stride = w * bpp,
      pixelFormat = PixelFormat.RGBA_8888,
      alphaSemantics = AlphaSemantics.OPAQUE,
      colorInfo = ColorInfo(),
    )

    val smallBlur = ImageBlurTransform(radius = 1).apply(input, context)
    val largeBlur = ImageBlurTransform(radius = 5).apply(input, context)

    val smallCenter = pixelAt(smallBlur, cx, cy)[0]
    val largeCenter = pixelAt(largeBlur, cx, cy)[0]

    // Larger radius spreads the energy more -> lower center value.
    assertTrue(
      largeCenter < smallCenter,
      "Larger radius should spread energy more: small=$smallCenter, large=$largeCenter"
    )
  }
}
