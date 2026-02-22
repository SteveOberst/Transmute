package dev.transmute.image.codecs.jvm

import dev.transmute.core.Bytes
import dev.transmute.image.ImageFormatDetector
import dev.transmute.image.ImageTestHelpers.adjustAlphaForComparison
import dev.transmute.image.ImageTestHelpers.checkerboard
import dev.transmute.image.ImageTestHelpers.horizontalGradient
import dev.transmute.image.ImageTestHelpers.meanAbsoluteError
import dev.transmute.image.ImageTestHelpers.pixelAt
import dev.transmute.image.ImageTestHelpers.solidColor
import dev.transmute.image.ImageTestHelpers.testContext
import dev.transmute.image.ImageFormat
import dev.transmute.image.Orientation
import dev.transmute.image.transform.ImageCropTransform
import dev.transmute.image.transform.ImageRotateTransform
import dev.transmute.image.transform.ImageScaleTransform
import dev.transmute.image.ImageIR
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import dev.transmute.image.CanonicalImageEncodeOptions
import dev.transmute.image.CanonicalImageDecodeOptions
import dev.transmute.image.JpegEncodeOptions

/**
 * Integration tests that exercise the full encode → detect → decode pipeline,
 * and multi-transform pipelines (crop + scale + rotate + encode + decode).
 *
 * These prove that the entire conversion chain works end-to-end with real
 * byte-level codecs - exactly what Transmute will do to iOS/Android photos.
 */
class FormatRoundTripTest {

  private val decoder = JvmImageIoDecoder()
  private val encoder = JvmImageIoEncoder()
  private val ctx = testContext()

  private suspend fun encodePng(ir: ImageIR): Bytes =
    encoder.encode(ir, ImageFormat.Png, CanonicalImageEncodeOptions(), ctx)

  private suspend fun encodeJpeg(ir: ImageIR, quality: Float = 0.90f): Bytes =
    encoder.encode(ir, ImageFormat.Jpeg, JpegEncodeOptions(quality = quality), ctx)

  // --- Format detection after encoding ---

  @Test
  fun encodedJpegDetectedAsJpeg() = runTest {
    val ir = solidColor(80, 80, r = 200, g = 100, b = 50)
    val bytes = encodeJpeg(ir)
    assertEquals(ImageFormat.Jpeg, ImageFormatDetector.detect(bytes))
  }

  @Test
  fun encodedPngDetectedAsPng() = runTest {
    val ir = solidColor(80, 80, r = 200, g = 100, b = 50)
    val bytes = encodePng(ir)
    assertEquals(ImageFormat.Png, ImageFormatDetector.detect(bytes))
  }

  // --- Full loop: encode → detect → decode → verify ---

  @Test
  fun pngFullLoopLossless() = runTest {
    val original = horizontalGradient(200, 20, startR = 10, endR = 245)
    val bytes = encodePng(original)
    val format = ImageFormatDetector.detect(bytes)
    assertEquals(ImageFormat.Png, format)

    val decoded = decoder.decode(bytes, CanonicalImageDecodeOptions(), ctx)
    assertEquals(200, decoded.width)
    assertEquals(20, decoded.height)

    // PNG is lossless - mean error should be 0 (ignoring alpha artefacts)
    val mae = meanAbsoluteError(
      adjustAlphaForComparison(original),
      adjustAlphaForComparison(decoded),
    )
    assertTrue(mae < 1.0, "PNG full loop MAE should be ~0, got $mae")
  }

  @Test
  fun jpegFullLoopWithinTolerance() = runTest {
    val original = solidColor(120, 120, r = 50, g = 150, b = 250)
    val bytes = encodeJpeg(original)
    val format = ImageFormatDetector.detect(bytes)
    assertEquals(ImageFormat.Jpeg, format)

    val decoded = decoder.decode(bytes, CanonicalImageDecodeOptions(), ctx)
    assertEquals(120, decoded.width)
    assertEquals(120, decoded.height)

    // JPEG is lossy - check pixel tolerance
    val center = pixelAt(decoded, 60, 60)
    assertTrue(center[0] in 40..60, "R should be near 50, got ${center[0]}")
    assertTrue(center[1] in 140..160, "G should be near 150, got ${center[1]}")
    assertTrue(center[2] in 240..255, "B should be near 250, got ${center[2]}")
  }

  // --- Multi-transform pipelines ---

  @Test
  fun scaleDownThenEncodeJpegThenDecode() = runTest {
    val original = checkerboard(800, 600, blockSize = 40,
      colorA = intArrayOf(255, 0, 0, 255),
      colorB = intArrayOf(0, 0, 255, 255),
    )
    val scaled = ImageScaleTransform(maxWidth = 200, maxHeight = 150)
      .apply(original, ctx)

    assertEquals(200, scaled.width)
    assertEquals(150, scaled.height)

    val bytes = encodeJpeg(scaled)
    val format = ImageFormatDetector.detect(bytes)
    assertEquals(ImageFormat.Jpeg, format)

    val decoded = decoder.decode(bytes, CanonicalImageDecodeOptions(), ctx)
    assertEquals(200, decoded.width)
    assertEquals(150, decoded.height)
  }

  @Test
  fun cropThenEncodePngThenDecode() = runTest {
    // 200×200 gradient, crop center 100×100, encode to PNG, decode back
    val original = horizontalGradient(200, 200, startR = 0, endR = 200)
    val cropped = ImageCropTransform(x = 50, y = 50, cropWidth = 100, cropHeight = 100)
      .apply(original, ctx)

    assertEquals(100, cropped.width)
    assertEquals(100, cropped.height)

    // First pixel in crop was at x=50 in original → R≈50
    val cropLeft = pixelAt(cropped, 0, 0)
    assertEquals(50, cropLeft[0], "Crop left edge R should be ~50")

    val bytes = encodePng(cropped)
    val decoded = decoder.decode(bytes, CanonicalImageDecodeOptions(), ctx)

    assertEquals(100, decoded.width)
    assertEquals(100, decoded.height)

    // Lossless - verify pixel values survive
    val decodedLeft = pixelAt(decoded, 0, 0)
    assertEquals(50, decodedLeft[0], "Decoded crop left R should be ~50")
  }

  @Test
  fun rotateThenScaleThenEncodeJpeg() = runTest {
    // 400×300 image, rotate 90° → 300×400, scale → 150×200, encode JPEG → decode
    val original = solidColor(400, 300, r = 100, g = 200, b = 50)
      .copy(orientation = Orientation.ROTATE_90)

    val rotated = ImageRotateTransform().apply(original, ctx)
    assertEquals(300, rotated.width)
    assertEquals(400, rotated.height)
    assertEquals(Orientation.NORMAL, rotated.orientation)

    val scaled = ImageScaleTransform(maxWidth = 150, maxHeight = 200)
      .apply(rotated, ctx)
    assertEquals(150, scaled.width)
    assertEquals(200, scaled.height)

    val bytes = encodeJpeg(scaled)
    assertEquals(ImageFormat.Jpeg, ImageFormatDetector.detect(bytes))

    val decoded = decoder.decode(bytes, CanonicalImageDecodeOptions(), ctx)
    assertEquals(150, decoded.width)
    assertEquals(200, decoded.height)

    // Solid colour - JPEG tolerance
    val px = pixelAt(decoded, 75, 100)
    assertTrue(px[0] in 90..110, "R near 100, got ${px[0]}")
    assertTrue(px[1] in 190..210, "G near 200, got ${px[1]}")
    assertTrue(px[2] in 40..60, "B near 50, got ${px[2]}")
  }

  @Test
  fun cropThenRotateThenEncodePng() = runTest {
    val original = horizontalGradient(300, 200, startR = 0, endR = 255)

    // Crop 100×200 from x=100
    val cropped = ImageCropTransform(x = 100, y = 0, cropWidth = 100, cropHeight = 200)
      .apply(original, ctx)
    assertEquals(100, cropped.width)
    assertEquals(200, cropped.height)

    // Rotate 180°
    val rotated = ImageRotateTransform()
      .apply(cropped.copy(orientation = Orientation.ROTATE_180), ctx)
    assertEquals(100, rotated.width)
    assertEquals(200, rotated.height)

    // The gradient was R: 100→199 left to right. After 180° it should be reversed.
    val leftAfterRotate = pixelAt(rotated, 0, 0)
    val rightAfterRotate = pixelAt(rotated, 99, 0)
    assertTrue(leftAfterRotate[0] > rightAfterRotate[0],
      "After 180° rotation, gradient should be reversed")

    // Encode to PNG → decode → verify dimensions
    val bytes = encodePng(rotated)
    assertEquals(ImageFormat.Png, ImageFormatDetector.detect(bytes))

    val decoded = decoder.decode(bytes, CanonicalImageDecodeOptions(), ctx)
    assertEquals(100, decoded.width)
    assertEquals(200, decoded.height)
  }

  // --- JPEG re-encode size reduction ---

  @Test
  fun jpegReEncodeAtLowerQualityIsSmallerFile() = runTest {
    val input = checkerboard(300, 300, blockSize = 15,
      colorA = intArrayOf(30, 60, 120, 255),
      colorB = intArrayOf(200, 180, 140, 255),
    )

    val highBytes = encodeJpeg(input, quality = 0.95f)
    val lowBytes = encodeJpeg(input, quality = 0.40f)

    assertTrue(lowBytes.size < highBytes.size,
      "Low quality JPEG (${lowBytes.size}B) should be smaller than high quality (${highBytes.size}B)")

    // Both should still be detectable as JPEG
    assertEquals(ImageFormat.Jpeg, ImageFormatDetector.detect(highBytes))
    assertEquals(ImageFormat.Jpeg, ImageFormatDetector.detect(lowBytes))
  }

  // --- Edge: 1×1 pixel survives the whole pipeline ---

  @Test
  fun singlePixelSurvivesFullPipeline() = runTest {
    val original = solidColor(1, 1, r = 42, g = 84, b = 168)

    // PNG encode → detect → decode
    val pngBytes = encodePng(original)
    assertEquals(ImageFormat.Png, ImageFormatDetector.detect(pngBytes))
    val decodedPng = decoder.decode(pngBytes, CanonicalImageDecodeOptions(), ctx)
    assertEquals(1, decodedPng.width)
    assertEquals(1, decodedPng.height)
    val pPx = pixelAt(decodedPng, 0, 0)
    assertContentEquals(intArrayOf(42, 84, 168, 255), pPx)
  }
}

