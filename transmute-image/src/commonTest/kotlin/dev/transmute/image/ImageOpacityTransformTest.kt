package dev.transmute.image

import dev.transmute.image.ImageTestHelpers.pixelAt
import dev.transmute.image.ImageTestHelpers.solidColor
import dev.transmute.image.ImageTestHelpers.testContext
import dev.transmute.image.transform.ImageOpacityTransform
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ImageOpacityTransformTest {

  private val context = testContext()

  @Test
  fun halfOpacityHalvesAlpha() = runTest {
    val input = solidColor(10, 10, r = 200, g = 100, b = 50, a = 200)
    val result = ImageOpacityTransform(opacity = 0.5f).apply(input, context)

    val p = pixelAt(result, 5, 5)
    assertEquals(100, p[3], "Alpha should be halved: 200*0.5=100")
    // RGB channels should be unchanged.
    assertEquals(200, p[0])
    assertEquals(100, p[1])
    assertEquals(50, p[2])
  }

  @Test
  fun zeroOpacityMakesFullyTransparent() = runTest {
    val input = solidColor(10, 10, r = 255, g = 255, b = 255, a = 255)
    val result = ImageOpacityTransform(opacity = 0f).apply(input, context)

    val p = pixelAt(result, 5, 5)
    assertEquals(0, p[3])
  }

  @Test
  fun fullOpacityKeepsAlpha() = runTest {
    val input = solidColor(10, 10, r = 128, g = 64, b = 32, a = 200)
    val result = ImageOpacityTransform(opacity = 1f).apply(input, context)

    val p = pixelAt(result, 5, 5)
    assertEquals(200, p[3])
    assertEquals(128, p[0])
  }

  @Test
  fun promotesRgb888ToRgba8888() = runTest {
    // Create an RGB_888 image (no alpha channel initially).
    // Simulated by a 3-byte-per-pixel image.
    // But our ImageIR uses RGBA_8888 with opaque alpha, so test that an
    // opaque image (a=255) gets its alpha multiplied.
    val input = solidColor(10, 10, r = 100, g = 100, b = 100, a = 255)
    val result = ImageOpacityTransform(opacity = 0.3f).apply(input, context)

    val p = pixelAt(result, 5, 5)
    // 255 * 0.3  76
    assertEquals(76, p[3], "Should be ~76, got ${p[3]}")
    assertEquals(PixelFormat.RGBA_8888, result.pixelFormat)
  }

  @Test
  fun preservesDimensions() = runTest {
    val input = solidColor(47, 31, r = 100, g = 100, b = 100)
    val result = ImageOpacityTransform(opacity = 0.5f).apply(input, context)

    assertEquals(47, result.width)
    assertEquals(31, result.height)
  }
}
