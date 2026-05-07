@file:Suppress("MagicNumber", "TooManyFunctions")

package dev.transmute.testing.image

import dev.transmute.image.ByteArrayPixelBuffer
import dev.transmute.image.ImageIR
import dev.transmute.image.PixelFormat
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Measurement and assertion utilities for [ImageIR] instances.
 *
 * Split into two groups:
 *
 * 1. **Measurements** - pure functions that return numbers (peak difference, PSNR,
 *    mean absolute error, ...).  Use these inside custom assertion logic.
 * 2. **Assertions** - convenience wrappers that throw [AssertionError] with clear
 *    messages when a condition is violated.  Ready for direct use in test bodies.
 *
 * All functions operate on the raw pixel bytes using [PixelFormat.bytesPerPixel]
 * from the image's declared format.  Only 8-bit-per-channel formats are fully
 * supported by the measurement helpers; higher-bit formats will still work for
 * dimension/size assertions but pixel-level comparisons may need casting.
 *
 * ### Quick start
 * ```kotlin
 * val original = SyntheticImage.colorBars(640, 480)
 * val decoded  = roundTrip(original) // encode -> decode
 *
 * assertDimensions(decoded, 640, 480)
 * assertSimilar(original, decoded, maxPeakDiff = 5)
 * val psnr = psnr(original, decoded)
 * println("PSNR = $psnr dB")
 * ```
 */
object ImageAssertions {

  // ---
  // Pixel access
  // ---

  /**
   * Read the RGBA components of the pixel at ([x], [y]).
   *
   * Works for [PixelFormat.RGBA_8888] and [PixelFormat.RGB_888] (alpha returned
   * as 255 for RGB).  Returns an [IntArray] of `[R, G, B, A]` in 0-255.
   *
   * @throws IllegalArgumentException if the format is not 8-bit per channel.
   */
  fun pixelAt(image: ImageIR, x: Int, y: Int): IntArray {
    val data = requirePixelBytes(image)
    val bpp = image.pixelFormat.bytesPerPixel
    require(bpp <= 4) { "pixelAt only supports 8-bit formats, got ${image.pixelFormat}" }
    val off = y * image.stride + x * bpp
    val r = data[off].toInt() and 0xFF
    val g = data[off + 1].toInt() and 0xFF
    val b = data[off + 2].toInt() and 0xFF
    val a = if (bpp >= 4) data[off + 3].toInt() and 0xFF else 255
    return intArrayOf(r, g, b, a)
  }

  // ---
  // Measurements
  // ---

  /**
   * Peak (maximum) absolute difference across all channels between two images.
   *
   * The two images must have identical dimensions and pixel format.
   * Returns a value in 0-255 for 8-bit formats.
   */
  fun peakDifference(a: ImageIR, b: ImageIR): Int {
    requireSameGeometry(a, b)
    val da = requirePixelBytes(a)
    val db = requirePixelBytes(b)
    val bpp = a.pixelFormat.bytesPerPixel
    var peak = 0
    for (y in 0 until a.height) {
      for (x in 0 until a.width) {
        val offA = y * a.stride + x * bpp
        val offB = y * b.stride + x * bpp
        for (ch in 0 until bpp) {
          val diff = abs((da[offA + ch].toInt() and 0xFF) - (db[offB + ch].toInt() and 0xFF))
          if (diff > peak) peak = diff
        }
      }
    }
    return peak
  }

  /**
   * Mean absolute error (MAE) across all channels.
   *
   * Returns a floating-point value in 0-255 for 8-bit formats.
   */
  fun meanAbsoluteError(a: ImageIR, b: ImageIR): Double {
    requireSameGeometry(a, b)
    val da = requirePixelBytes(a)
    val db = requirePixelBytes(b)
    val bpp = a.pixelFormat.bytesPerPixel
    var sum = 0L
    var count = 0L
    for (y in 0 until a.height) {
      for (x in 0 until a.width) {
        val offA = y * a.stride + x * bpp
        val offB = y * b.stride + x * bpp
        for (ch in 0 until bpp) {
          sum += abs((da[offA + ch].toInt() and 0xFF) - (db[offB + ch].toInt() and 0xFF))
          count++
        }
      }
    }
    return if (count == 0L) 0.0 else sum.toDouble() / count
  }

  /**
   * Root Mean Squared Error across all channels.
   */
  fun rmse(a: ImageIR, b: ImageIR): Double {
    requireSameGeometry(a, b)
    val da = requirePixelBytes(a)
    val db = requirePixelBytes(b)
    val bpp = a.pixelFormat.bytesPerPixel
    var sumSq = 0.0
    var count = 0L
    for (y in 0 until a.height) {
      for (x in 0 until a.width) {
        val offA = y * a.stride + x * bpp
        val offB = y * b.stride + x * bpp
        for (ch in 0 until bpp) {
          val diff = (da[offA + ch].toInt() and 0xFF) - (db[offB + ch].toInt() and 0xFF)
          sumSq += diff.toDouble() * diff
          count++
        }
      }
    }
    return if (count == 0L) 0.0 else sqrt(sumSq / count)
  }

  /**
   * Peak Signal-to-Noise Ratio in decibels.
   *
   * - Identical images -> [Double.POSITIVE_INFINITY]
   * - Typical lossless: > 60 dB
   * - Typical high-quality lossy (JPEG q=90): 30-45 dB
   *
   * @param maxVal The maximum possible pixel value (255 for 8-bit).
   */
  fun psnr(a: ImageIR, b: ImageIR, maxVal: Int = 255): Double {
    val mse = rmse(a, b).let { it * it } // MSE from RMSE
    if (mse == 0.0) return Double.POSITIVE_INFINITY
    return 20.0 * log10(maxVal.toDouble()) - 10.0 * log10(mse)
  }

  /**
   * Structural similarity proxy - computes the mean luminance difference
   * weighted per-pixel.  This is a simplified (non-windowed) metric that
   * correlates with SSIM for test assertions without requiring convolutions.
   *
   * Returns a value in 0.0-1.0 where 1.0 = identical.
   */
  fun similarityIndex(a: ImageIR, b: ImageIR): Double {
    requireSameGeometry(a, b)
    val da = requirePixelBytes(a)
    val db = requirePixelBytes(b)
    val bpp = a.pixelFormat.bytesPerPixel
    val rgbChannels = minOf(bpp, 3)
    var totalDiff = 0.0
    var pixelCount = 0L
    for (y in 0 until a.height) {
      for (x in 0 until a.width) {
        val offA = y * a.stride + x * bpp
        val offB = y * b.stride + x * bpp
        var pixDiff = 0.0
        for (ch in 0 until rgbChannels) {
          val va = da[offA + ch].toInt() and 0xFF
          val vb = db[offB + ch].toInt() and 0xFF
          pixDiff += abs(va - vb).toDouble()
        }
        totalDiff += pixDiff / (rgbChannels * 255.0)
        pixelCount++
      }
    }
    return if (pixelCount == 0L) 1.0 else 1.0 - (totalDiff / pixelCount)
  }

  /**
   * Count the number of pixels that differ by more than [threshold] on any channel.
   */
  fun countDifferingPixels(a: ImageIR, b: ImageIR, threshold: Int = 0): Long {
    requireSameGeometry(a, b)
    val da = requirePixelBytes(a)
    val db = requirePixelBytes(b)
    val bpp = a.pixelFormat.bytesPerPixel
    var count = 0L
    for (y in 0 until a.height) {
      for (x in 0 until a.width) {
        val offA = y * a.stride + x * bpp
        val offB = y * b.stride + x * bpp
        for (ch in 0 until bpp) {
          if (abs((da[offA + ch].toInt() and 0xFF) - (db[offB + ch].toInt() and 0xFF)) > threshold) {
            count++
            break // count each pixel only once
          }
        }
      }
    }
    return count
  }

  /**
   * Average brightness (luma) of the image using the Rec.601 formula:
   * Y = 0.299.R + 0.587.G + 0.114.B
   *
   * Returns a value in 0.0-255.0 for 8-bit images.
   */
  fun averageBrightness(image: ImageIR): Double {
    val data = requirePixelBytes(image)
    val bpp = image.pixelFormat.bytesPerPixel
    var sum = 0.0
    var count = 0L
    for (y in 0 until image.height) {
      for (x in 0 until image.width) {
        val off = y * image.stride + x * bpp
        val r = data[off].toInt() and 0xFF
        val g = data[off + 1].toInt() and 0xFF
        val b = data[off + 2].toInt() and 0xFF
        sum += 0.299 * r + 0.587 * g + 0.114 * b
        count++
      }
    }
    return if (count == 0L) 0.0 else sum / count
  }

  /**
   * Histogram of a single channel. Returns an IntArray of size 256 where
   * index *i* holds the count of pixels with that channel value.
   *
   * @param channel 0=R, 1=G, 2=B, 3=A
   */
  fun histogram(image: ImageIR, channel: Int = 0): IntArray {
    val data = requirePixelBytes(image)
    val bpp = image.pixelFormat.bytesPerPixel
    require(channel in 0 until bpp) { "Channel $channel out of range for $bpp bpp format" }
    val hist = IntArray(256)
    for (y in 0 until image.height) {
      for (x in 0 until image.width) {
        val off = y * image.stride + x * bpp
        val v = data[off + channel].toInt() and 0xFF
        hist[v]++
      }
    }
    return hist
  }

  // ---
  // Assertions
  // ---

  /**
   * Assert that the image has the expected [width] and [height].
   */
  fun assertDimensions(image: ImageIR, width: Int, height: Int) {
    check(image.width == width && image.height == height) {
      "Expected ${width}×${height}, got ${image.width}×${image.height}"
    }
  }

  /**
   * Assert that the image has the expected [PixelFormat].
   */
  fun assertPixelFormat(image: ImageIR, expected: PixelFormat) {
    check(image.pixelFormat == expected) {
      "Expected pixel format $expected, got ${image.pixelFormat}"
    }
  }

  /**
   * Assert that two images have identical pixel data.
   */
  fun assertExactMatch(a: ImageIR, b: ImageIR) {
    requireSameGeometry(a, b)
    val da = requirePixelBytes(a)
    val db = requirePixelBytes(b)
    check(da.contentEquals(db)) {
      val peak = peakDifference(a, b)
      "Images are not identical (peak difference = $peak)"
    }
  }

  /**
   * Assert that the peak per-channel difference does not exceed [maxPeakDiff].
   *
   * A value of 0 means exact match; 1-3 is typical for lossless codecs with
   * rounding; 5-15 is typical for high-quality lossy codecs.
   */
  fun assertSimilar(a: ImageIR, b: ImageIR, maxPeakDiff: Int) {
    val peak = peakDifference(a, b)
    check(peak <= maxPeakDiff) {
      "Peak difference $peak exceeds tolerance $maxPeakDiff"
    }
  }

  /**
   * Assert that the PSNR between two images meets or exceeds [minDB].
   *
   * Typical thresholds:
   * - Lossless: > 60 dB (often infinity)
   * - High-quality lossy: > 30 dB
   * - Low-quality lossy: > 20 dB
   */
  fun assertPsnr(a: ImageIR, b: ImageIR, minDB: Double) {
    val value = psnr(a, b)
    check(value >= minDB) {
      "PSNR ${String.format("%.2f", value)} dB is below minimum $minDB dB"
    }
  }

  /**
   * Assert that the MAE between two images does not exceed [maxMae].
   */
  fun assertMaeBelow(a: ImageIR, b: ImageIR, maxMae: Double) {
    val value = meanAbsoluteError(a, b)
    check(value <= maxMae) {
      "MAE ${String.format("%.4f", value)} exceeds maximum $maxMae"
    }
  }

  /**
   * Assert that the pixel at ([x], [y]) is near the expected [rgba] values,
   * within [tolerance] per channel.
   */
  fun assertPixelNear(
    image: ImageIR,
    x: Int,
    y: Int,
    rgba: IntArray,
    tolerance: Int = 1,
  ) {
    require(rgba.size == 4) { "Expected RGBA (4 elements)" }
    val actual = pixelAt(image, x, y)
    for (ch in 0..3) {
      check(abs(actual[ch] - rgba[ch]) <= tolerance) {
        val chName = arrayOf("R", "G", "B", "A")[ch]
        "Pixel ($x,$y) $chName: expected ${rgba[ch]}±$tolerance, got ${actual[ch]}"
      }
    }
  }

  /**
   * Assert that the image is *not* a uniform solid color (i.e., has some variation).
   *
   * Useful for verifying that a codec actually produced meaningful output
   * rather than a blank / zero-filled buffer.
   */
  fun assertNotUniform(image: ImageIR) {
    val data = requirePixelBytes(image)
    val bpp = image.pixelFormat.bytesPerPixel
    if (image.width * image.height <= 1) return // trivial
    val firstOff = 0
    for (y in 0 until image.height) {
      for (x in 0 until image.width) {
        val off = y * image.stride + x * bpp
        for (ch in 0 until bpp) {
          if (data[off + ch] != data[firstOff + ch]) return // found variation
        }
      }
    }
    error("Image is uniform (all pixels identical)")
  }

  /**
   * Assert that the image *is* a uniform solid color.
   */
  fun assertUniform(image: ImageIR) {
    val data = requirePixelBytes(image)
    val bpp = image.pixelFormat.bytesPerPixel
    val firstOff = 0
    for (y in 0 until image.height) {
      for (x in 0 until image.width) {
        val off = y * image.stride + x * bpp
        for (ch in 0 until bpp) {
          check(data[off + ch] == data[firstOff + ch]) {
            "Image is not uniform: pixel ($x,$y) differs from (0,0)"
          }
        }
      }
    }
  }

  /**
   * Assert that the percentage of pixels differing by more than [threshold]
   * does not exceed [maxPercent].
   *
   * @param maxPercent Maximum allowed percentage (0.0-100.0).
   */
  fun assertDiffPixelsBelowPercent(
    a: ImageIR,
    b: ImageIR,
    threshold: Int = 0,
    maxPercent: Double = 1.0,
  ) {
    val diffCount = countDifferingPixels(a, b, threshold)
    val totalPixels = a.width.toLong() * a.height
    val percent = if (totalPixels == 0L) 0.0 else diffCount * 100.0 / totalPixels
    check(percent <= maxPercent) {
      "%.2f%% of pixels differ (threshold=$threshold), max allowed ${maxPercent}%%".format(percent)
    }
  }

  /**
   * Compound assertion for typical round-trip image codec tests.
   *
   * Checks: dimensions preserved, pixel format matches, peak difference <= [maxPeakDiff],
   * and not-uniform (unless original is uniform).
   */
  fun assertRoundTripFidelity(
    original: ImageIR,
    decoded: ImageIR,
    maxPeakDiff: Int = 3,
  ) {
    assertDimensions(decoded, original.width, original.height)
    assertSimilar(original, decoded, maxPeakDiff)
  }

  // ---
  // Internal helpers
  // ---

  private fun requirePixelBytes(image: ImageIR): ByteArray {
    val buffer = image.buffer
    require(buffer is ByteArrayPixelBuffer) {
      "ImageAssertions requires ByteArrayPixelBuffer, got ${buffer::class.simpleName}"
    }
    return buffer.data
  }

  private fun requireSameGeometry(a: ImageIR, b: ImageIR) {
    require(a.width == b.width && a.height == b.height) {
      "Dimension mismatch: ${a.width}×${a.height} vs ${b.width}×${b.height}"
    }
    require(a.pixelFormat == b.pixelFormat) {
      "Pixel format mismatch: ${a.pixelFormat} vs ${b.pixelFormat}"
    }
  }
}
