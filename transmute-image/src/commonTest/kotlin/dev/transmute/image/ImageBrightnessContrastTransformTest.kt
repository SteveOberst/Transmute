package dev.transmute.image

import dev.transmute.image.ImageTestHelpers.pixelAt
import dev.transmute.image.ImageTestHelpers.solidColor
import dev.transmute.image.ImageTestHelpers.testContext
import dev.transmute.image.transform.ImageBrightnessContrastTransform
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ImageBrightnessContrastTransformTest {

  private val context = testContext()

  @Test
  fun positiveBrightnessIncreasesValues() = runTest {
    val input = solidColor(10, 10, r = 100, g = 100, b = 100)
    val result = ImageBrightnessContrastTransform(brightness = 50f).apply(input, context)

    val p = pixelAt(result, 5, 5)
    assertEquals(150, p[0])
    assertEquals(150, p[1])
    assertEquals(150, p[2])
  }

  @Test
  fun negativeBrightnessDecreasesValues() = runTest {
    val input = solidColor(10, 10, r = 100, g = 100, b = 100)
    val result = ImageBrightnessContrastTransform(brightness = -50f).apply(input, context)

    val p = pixelAt(result, 5, 5)
    assertEquals(50, p[0])
  }

  @Test
  fun brightnessClipsAt255() = runTest {
    val input = solidColor(10, 10, r = 200, g = 200, b = 200)
    val result = ImageBrightnessContrastTransform(brightness = 100f).apply(input, context)

    val p = pixelAt(result, 5, 5)
    assertEquals(255, p[0])
  }

  @Test
  fun brightnessClipsAt0() = runTest {
    val input = solidColor(10, 10, r = 30, g = 30, b = 30)
    val result = ImageBrightnessContrastTransform(brightness = -100f).apply(input, context)

    val p = pixelAt(result, 5, 5)
    assertEquals(0, p[0])
  }

  @Test
  fun contrastGreaterThan1IncreasesContrast() = runTest {
    // Pixel at mid-grey (128) should stay roughly the same.
    // Pixel > 128 should get brighter; pixel < 128 should get darker.
    val input = solidColor(10, 10, r = 200, g = 56, b = 128)
    val result = ImageBrightnessContrastTransform(contrast = 2.0f).apply(input, context)

    val p = pixelAt(result, 5, 5)
    // R: (200-128)*2 + 128 = 272 → clamped 255
    assertEquals(255, p[0])
    // G: (56-128)*2 + 128 = -16 → clamped 0
    assertEquals(0, p[1])
    // B: (128-128)*2 + 128 = 128
    assertEquals(128, p[2])
  }

  @Test
  fun contrastLessThan1ReducesContrast() = runTest {
    val input = solidColor(10, 10, r = 200, g = 56, b = 128)
    val result = ImageBrightnessContrastTransform(contrast = 0.5f).apply(input, context)

    val p = pixelAt(result, 5, 5)
    // R: (200-128)*0.5 + 128 = 164
    assertEquals(164, p[0])
    // G: (56-128)*0.5 + 128 = 92
    assertEquals(92, p[1])
    // B: should stay 128
    assertEquals(128, p[2])
  }

  @Test
  fun noChangeWithDefaults() = runTest {
    val input = solidColor(10, 10, r = 42, g = 84, b = 168)
    val result = ImageBrightnessContrastTransform(brightness = 0f, contrast = 1f).apply(input, context)

    val p = pixelAt(result, 5, 5)
    assertEquals(42, p[0])
    assertEquals(84, p[1])
    assertEquals(168, p[2])
  }

  @Test
  fun preservesAlpha() = runTest {
    val input = solidColor(10, 10, r = 100, g = 100, b = 100, a = 128)
    val result = ImageBrightnessContrastTransform(brightness = 50f, contrast = 1.5f).apply(input, context)

    val p = pixelAt(result, 5, 5)
    assertEquals(128, p[3])
  }
}
