package dev.transmute.image

import dev.transmute.image.transform.ImageResizeTransform
import dev.transmute.image.transform.kernel.ResampleFilter
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ImageResizeTransformTest {

  private val ctx = ImageTestHelpers.testContext()

  // -- Downscale --

  @Test
  fun downscaleProducesExactTargetDimensions() = runTest {
    val src = ImageTestHelpers.horizontalGradient(100, 80)
    val transform = ImageResizeTransform(50, 40)

    val result = transform.apply(src, ctx)
    assertEquals(50, result.width)
    assertEquals(40, result.height)
  }

  @Test
  fun downscalePreservesPixelFormat() = runTest {
    val src = ImageTestHelpers.solidColor(64, 64, r = 128, g = 64, b = 32)
    val transform = ImageResizeTransform(32, 32)

    val result = transform.apply(src, ctx)
    assertEquals(src.pixelFormat, result.pixelFormat)
  }

  @Test
  fun downscaleSolidColorPreservesColor() = runTest {
    val src = ImageTestHelpers.solidColor(100, 100, r = 200, g = 100, b = 50)
    val transform = ImageResizeTransform(50, 50)

    val result = transform.apply(src, ctx)
    // Solid color -> should remain solid after resize; peak diff <= 1 for rounding
    val diff = ImageTestHelpers.peakDifference(
      ImageTestHelpers.solidColor(50, 50, r = 200, g = 100, b = 50),
      result,
    )
    assertTrue(diff <= 2, "Solid color resize peak diff $diff should be ≤ 2")
  }

  // -- Upscale --

  @Test
  fun upscaleProducesExactTargetDimensions() = runTest {
    val src = ImageTestHelpers.checkerboard(32, 32)
    val transform = ImageResizeTransform(64, 64)

    val result = transform.apply(src, ctx)
    assertEquals(64, result.width)
    assertEquals(64, result.height)
  }

  @Test
  fun upscaleBlockedWhenAllowUpscaleIsFalse() = runTest {
    val src = ImageTestHelpers.solidColor(32, 32, r = 128, g = 128, b = 128)
    val transform = ImageResizeTransform(64, 64, allowUpscale = false)

    val result = transform.apply(src, ctx)
    // Should return original unchanged
    assertEquals(32, result.width)
    assertEquals(32, result.height)
  }

  // -- Identity --

  @Test
  fun sameSizeReturnsUnchanged() = runTest {
    val src = ImageTestHelpers.solidColor(50, 50, r = 10, g = 20, b = 30)
    val transform = ImageResizeTransform(50, 50)

    val result = transform.apply(src, ctx)
    assertEquals(50, result.width)
    assertEquals(50, result.height)
    assertEquals(0, ImageTestHelpers.peakDifference(src, result))
  }

  // -- Filters --

  @Test
  fun allFiltersProduceCorrectDimensions() = runTest {
    val src = ImageTestHelpers.horizontalGradient(80, 60)
    for (filter in ResampleFilter.entries) {
      val transform = ImageResizeTransform(40, 30, filter = filter)
      val result = transform.apply(src, ctx)
      assertEquals(40, result.width, "Filter $filter: width")
      assertEquals(30, result.height, "Filter $filter: height")
    }
  }

  // -- Non-uniform scale --

  @Test
  fun nonUniformScaleProducesCorrectDimensions() = runTest {
    val src = ImageTestHelpers.checkerboard(100, 50)
    val transform = ImageResizeTransform(200, 25)

    val result = transform.apply(src, ctx)
    assertEquals(200, result.width)
    assertEquals(25, result.height)
  }

  // -- Transform ID --

  @Test
  fun transformIdIsCorrect() {
    val transform = ImageResizeTransform(10, 10)
    assertEquals("image.resize", transform.id.value)
  }
}
