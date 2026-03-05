package dev.transmute.testing.image

import dev.transmute.image.PixelFormat
import dev.transmute.testing.image.ImageAssertions.assertDimensions
import dev.transmute.testing.image.ImageAssertions.assertExactMatch
import dev.transmute.testing.image.ImageAssertions.assertNotUniform
import dev.transmute.testing.image.ImageAssertions.assertPixelFormat
import dev.transmute.testing.image.ImageAssertions.assertPixelNear
import dev.transmute.testing.image.ImageAssertions.assertSimilar
import dev.transmute.testing.image.ImageAssertions.assertUniform
import dev.transmute.testing.image.ImageAssertions.averageBrightness
import dev.transmute.testing.image.ImageAssertions.histogram
import dev.transmute.testing.image.ImageAssertions.meanAbsoluteError
import dev.transmute.testing.image.ImageAssertions.peakDifference
import dev.transmute.testing.image.ImageAssertions.pixelAt
import dev.transmute.testing.image.ImageAssertions.psnr
import dev.transmute.testing.image.ImageAssertions.rmse
import dev.transmute.testing.image.ImageAssertions.similarityIndex
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SyntheticImageTest {

  @Test
  fun solidColorDimensions() {
    val img = SyntheticImage.solidColor(100, 50, r = 128, g = 64, b = 32)
    assertDimensions(img, 100, 50)
    assertPixelFormat(img, PixelFormat.RGBA_8888)
  }

  @Test
  fun solidColorPixelAccuracy() {
    val img = SyntheticImage.solidColor(10, 10, r = 200, g = 100, b = 50)
    val px = pixelAt(img, 5, 5)
    assertEquals(200, px[0])
    assertEquals(100, px[1])
    assertEquals(50, px[2])
    assertEquals(255, px[3])
  }

  @Test
  fun solidColorIsUniform() {
    val img = SyntheticImage.solidColor(20, 20, r = 42, g = 42, b = 42)
    assertUniform(img)
  }

  @Test
  fun checkerboardIsNotUniform() {
    val img = SyntheticImage.checkerboard(64, 64, blockSize = 8)
    assertNotUniform(img)
  }

  @Test
  fun identicalImagesExactMatch() {
    val a = SyntheticImage.colorBars(100, 50)
    val b = SyntheticImage.colorBars(100, 50)
    assertExactMatch(a, b)
    assertEquals(0, peakDifference(a, b))
  }

  @Test
  fun differentImagesHaveDifference() {
    val a = SyntheticImage.solidColor(10, 10, r = 0, g = 0, b = 0)
    val b = SyntheticImage.solidColor(10, 10, r = 255, g = 255, b = 255)
    assertEquals(255, peakDifference(a, b))
  }

  @Test
  fun maeOfIdenticalIsZero() {
    val img = SyntheticImage.horizontalGradient(100, 50)
    assertEquals(0.0, meanAbsoluteError(img, img))
  }

  @Test
  fun rmseOfIdenticalIsZero() {
    val img = SyntheticImage.verticalGradient(100, 50)
    assertEquals(0.0, rmse(img, img))
  }

  @Test
  fun psnrOfIdenticalIsInfinity() {
    val img = SyntheticImage.checkerboard(32, 32)
    assertEquals(Double.POSITIVE_INFINITY, psnr(img, img))
  }

  @Test
  fun similarityIndexOfIdenticalIsOne() {
    val img = SyntheticImage.radialGradient(64, 64)
    assertEquals(1.0, similarityIndex(img, img))
  }

  @Test
  fun similarWithinTolerance() {
    val a = SyntheticImage.solidColor(10, 10, r = 100, g = 100, b = 100)
    val b = SyntheticImage.solidColor(10, 10, r = 102, g = 100, b = 101)
    assertSimilar(a, b, maxPeakDiff = 3)
  }

  @Test
  fun horizontalGradientVariesHorizontally() {
    val img = SyntheticImage.horizontalGradient(256, 10)
    val left = pixelAt(img, 0, 5)
    val right = pixelAt(img, 255, 5)
    assertEquals(0, left[0])
    assertEquals(255, right[0])
  }

  @Test
  fun verticalGradientVariesVertically() {
    val img = SyntheticImage.verticalGradient(10, 256)
    val top = pixelAt(img, 5, 0)
    val bottom = pixelAt(img, 5, 255)
    assertEquals(0, top[0])
    assertEquals(255, bottom[0])
  }

  @Test
  fun noiseIsDeterministic() {
    val a = SyntheticImage.noise(32, 32, seed = 123L)
    val b = SyntheticImage.noise(32, 32, seed = 123L)
    assertExactMatch(a, b)
  }

  @Test
  fun noiseWithDifferentSeedsDiffers() {
    val a = SyntheticImage.noise(32, 32, seed = 1L)
    val b = SyntheticImage.noise(32, 32, seed = 2L)
    assertTrue(peakDifference(a, b) > 0)
  }

  @Test
  fun colorBarsHasSevenRegions() {
    val img = SyntheticImage.colorBars(700, 10)
    // Sample center of each bar
    val colors = (0 until 7).map { i ->
      val x = (i * 100) + 50
      pixelAt(img, x, 5)
    }
    // All bars should be distinct
    val distinct = colors.map { it.toList() }.toSet()
    assertEquals(7, distinct.size, "Color bars should produce 7 distinct colors")
  }

  @Test
  fun grayscaleRampMonotonicallyIncreases() {
    val img = SyntheticImage.grayscaleRamp(256, 1)
    var prev = 0
    for (x in 0 until 256) {
      val v = pixelAt(img, x, 0)[0]
      assertTrue(v >= prev, "Grayscale ramp should be monotonically increasing at x=$x: $prev -> $v")
      prev = v
    }
  }

  @Test
  fun quadrantsAreFourDistinctColors() {
    val img = SyntheticImage.quadrants(100, 100)
    val tl = pixelAt(img, 10, 10)
    val tr = pixelAt(img, 90, 10)
    val bl = pixelAt(img, 10, 90)
    val br = pixelAt(img, 90, 90)
    val distinctColors = setOf(tl.toList(), tr.toList(), bl.toList(), br.toList())
    assertEquals(4, distinctColors.size, "Quadrants should have 4 distinct colors")
  }

  @Test
  fun pixelNearAssertion() {
    val img = SyntheticImage.solidColor(10, 10, r = 100, g = 150, b = 200)
    assertPixelNear(img, 5, 5, intArrayOf(100, 150, 200, 255), tolerance = 0)
  }

  @Test
  fun averageBrightnessOfBlackIsZero() {
    val img = SyntheticImage.solidColor(10, 10, r = 0, g = 0, b = 0)
    assertEquals(0.0, averageBrightness(img))
  }

  @Test
  fun histogramOfSolidColorHasSinglePeak() {
    val img = SyntheticImage.solidColor(10, 10, r = 128, g = 0, b = 0)
    val hist = histogram(img, channel = 0) // red channel
    assertEquals(100, hist[128]) // all 100 pixels at value 128
    assertEquals(0, hist[0])
    assertEquals(0, hist[255])
  }

  @Test
  fun borderHasDistinctRegions() {
    val img = SyntheticImage.border(20, 20, borderWidth = 2)
    val cornerPx = pixelAt(img, 0, 0)
    val centerPx = pixelAt(img, 10, 10)
    assertTrue(cornerPx.toList() != centerPx.toList(), "Border and fill should differ")
  }

  @Test
  fun gridHasLines() {
    val img = SyntheticImage.grid(64, 64, cellSize = 16, lineWidth = 1)
    val linePx = pixelAt(img, 0, 0)   // on grid line
    val cellPx = pixelAt(img, 8, 8)   // in cell interior
    assertTrue(linePx.toList() != cellPx.toList(), "Grid line and cell should differ")
  }

  @Test
  fun zonePlateVaries() {
    val img = SyntheticImage.zonePlate(64, 64)
    assertNotUniform(img)
  }

  @Test
  fun alphaGradientVariesAlpha() {
    val img = SyntheticImage.alphaGradient(256, 1)
    val left = pixelAt(img, 0, 0)
    val right = pixelAt(img, 255, 0)
    assertEquals(255, left[3], "Left should be opaque")
    assertEquals(0, right[3], "Right should be transparent")
  }
}
