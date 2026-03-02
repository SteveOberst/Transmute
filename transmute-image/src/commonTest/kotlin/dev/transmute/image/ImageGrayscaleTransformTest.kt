package dev.transmute.image

import dev.transmute.image.ImageTestHelpers.horizontalGradient
import dev.transmute.image.ImageTestHelpers.pixelAt
import dev.transmute.image.ImageTestHelpers.solidColor
import dev.transmute.image.ImageTestHelpers.testContext
import dev.transmute.image.transform.ImageGrayscaleTransform
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ImageGrayscaleTransformTest {

  private val context = testContext()
  private val transform = ImageGrayscaleTransform()

  @Test
  fun pureWhiteRemainsWhite() = runTest {
    val input = solidColor(10, 10, r = 255, g = 255, b = 255)
    val result = transform.apply(input, context)

    val p = pixelAt(result, 5, 5)
    assertEquals(255, p[0])
    assertEquals(255, p[1])
    assertEquals(255, p[2])
    assertEquals(255, p[3])
  }

  @Test
  fun pureBlackRemainsBlack() = runTest {
    val input = solidColor(10, 10, r = 0, g = 0, b = 0)
    val result = transform.apply(input, context)

    val p = pixelAt(result, 5, 5)
    assertEquals(0, p[0])
    assertEquals(0, p[1])
    assertEquals(0, p[2])
  }

  @Test
  fun pureRedBecomesLumaGrey() = runTest {
    // BT.709: luma = 0.2126*R -> 0.2126*255  54
    val input = solidColor(10, 10, r = 255, g = 0, b = 0)
    val result = transform.apply(input, context)

    val p = pixelAt(result, 5, 5)
    assertTrue(p[0] in 53..55, "R→grey should be ~54, got ${p[0]}")
    assertEquals(p[0], p[1], "All channels should be equal for grey")
    assertEquals(p[1], p[2])
  }

  @Test
  fun pureGreenBecomesLumaGrey() = runTest {
    // BT.709: luma = 0.7152*255  182
    val input = solidColor(10, 10, r = 0, g = 255, b = 0)
    val result = transform.apply(input, context)

    val p = pixelAt(result, 5, 5)
    assertTrue(p[0] in 181..183, "G→grey should be ~182, got ${p[0]}")
    assertEquals(p[0], p[1])
    assertEquals(p[1], p[2])
  }

  @Test
  fun preservesAlpha() = runTest {
    val input = solidColor(10, 10, r = 200, g = 100, b = 50, a = 128)
    val result = transform.apply(input, context)

    val p = pixelAt(result, 5, 5)
    assertEquals(128, p[3], "Alpha should be preserved")
  }

  @Test
  fun preservesDimensions() = runTest {
    val input = solidColor(47, 31, r = 128, g = 64, b = 200)
    val result = transform.apply(input, context)

    assertEquals(47, result.width)
    assertEquals(31, result.height)
    assertEquals(PixelFormat.RGBA_8888, result.pixelFormat)
  }

  @Test
  fun allChannelsEqualAfterGrayscale() = runTest {
    val input = horizontalGradient(50, 10, startR = 255, startG = 0, startB = 0, endR = 0, endG = 0, endB = 255)
    val result = transform.apply(input, context)

    // Check several pixels: R, G, B should be identical (grey)
    for (x in listOf(0, 12, 25, 37, 49)) {
      val p = pixelAt(result, x, 5)
      assertEquals(p[0], p[1], "Pixel ($x,5): R=${p[0]} should equal G=${p[1]}")
      assertEquals(p[1], p[2], "Pixel ($x,5): G=${p[1]} should equal B=${p[2]}")
    }
  }
}
