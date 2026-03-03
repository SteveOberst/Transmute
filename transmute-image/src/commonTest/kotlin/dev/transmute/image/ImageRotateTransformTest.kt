package dev.transmute.image

import dev.transmute.image.ImageTestHelpers.pixelAt
import dev.transmute.image.ImageTestHelpers.solidColor
import dev.transmute.image.ImageTestHelpers.testContext
import dev.transmute.image.transform.ImageRotateTransform
import dev.transmute.model.core.asBytes
import dev.transmute.codec.pipeline.TransformId
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

/**
 * Tests for [ImageRotateTransform].
 *
 * Uses a small asymmetric colour-coded image so we can verify
 * exact pixel positions after 90 deg/180 deg/270 deg rotations.
 *
 * The reference image is 4x3 (W=4, H=3):
 * ```
 *   (0,0)Red     (1,0)Green   (2,0)Blue    (3,0)White
 *   (0,1)Yellow  (1,1)Cyan    (2,1)Magenta (3,1)Black
 *   (0,2)Orange  (1,2)Pink    (2,2)Grey    (3,2)Teal
 * ```
 * Each pixel has a unique colour, making mis-placement immediately obvious.
 */
class ImageRotateTransformTest {

  // --- Helpers ---

  private val RED     = intArrayOf(255,   0,   0, 255)
  private val GREEN   = intArrayOf(  0, 255,   0, 255)
  private val BLUE    = intArrayOf(  0,   0, 255, 255)
  private val WHITE   = intArrayOf(255, 255, 255, 255)
  private val YELLOW  = intArrayOf(255, 255,   0, 255)
  private val CYAN    = intArrayOf(  0, 255, 255, 255)
  private val MAGENTA = intArrayOf(255,   0, 255, 255)
  private val BLACK   = intArrayOf(  0,   0,   0, 255)
  private val ORANGE  = intArrayOf(255, 165,   0, 255)
  private val PINK    = intArrayOf(255, 192, 203, 255)
  private val GREY    = intArrayOf(128, 128, 128, 255)
  private val TEAL    = intArrayOf(  0, 128, 128, 255)

  /** Build the 4x3 reference image with the given orientation. */
  private fun referenceImage(orientation: Orientation): ImageIR {
    val w = 4; val h = 3; val bpp = 4
    val pixels = listOf(
      RED, GREEN, BLUE, WHITE,       // row 0
      YELLOW, CYAN, MAGENTA, BLACK,  // row 1
      ORANGE, PINK, GREY, TEAL,      // row 2
    )
    val data = ByteArray(w * h * bpp)
    for (i in pixels.indices) {
      val off = i * bpp
      data[off + 0] = pixels[i][0].toByte()
      data[off + 1] = pixels[i][1].toByte()
      data[off + 2] = pixels[i][2].toByte()
      data[off + 3] = pixels[i][3].toByte()
    }
    return ImageIR(
      buffer = ByteArrayPixelBuffer(data),
      width = w,
      height = h,
      stride = w * bpp,
      pixelFormat = PixelFormat.RGBA_8888,
      alphaSemantics = AlphaSemantics.STRAIGHT,
      colorInfo = ColorInfo(),
      orientation = orientation,
    )
  }

  // --- NORMAL passthrough ---

  @Test
  fun rotateAlwaysAppliesExplicitDegrees() = runTest {
    // ImageRotateTransform always rotates by the given degrees - EXIF orientation is ignored
    val input = referenceImage(Orientation.NORMAL) // 4x3
    val result = ImageRotateTransform(90).apply(input, testContext())
    assertEquals(3, result.width, "90 deg CW: width becomes srcH")
    assertEquals(4, result.height, "90 deg CW: height becomes srcW")
    assertEquals(Orientation.NORMAL, result.orientation)
  }

  // --- 90 deg CW ---

  @Test
  fun rotate90DimensionsSwapped() = runTest {
    val input = referenceImage(Orientation.NORMAL) // 4x3
    val result = ImageRotateTransform(90).apply(input, testContext())
    assertEquals(3, result.width, "W should become srcH")  // srcH=3
    assertEquals(4, result.height, "H should become srcW")  // srcW=4
    assertEquals(Orientation.NORMAL, result.orientation)
  }

  @Test
  fun rotate90PixelMapping() = runTest {
    // 90 deg CW: (x,y) -> dst(srcH-1-y, x), new dims srcHxsrcW = 3x4
    // Source (0,0)=RED -> dst(2,0)
    // Source (3,0)=WHITE -> dst(2,3)
    // Source (0,2)=ORANGE -> dst(0,0)
    // Source (3,2)=TEAL -> dst(0,3)
    val input = referenceImage(Orientation.NORMAL)
    val result = ImageRotateTransform(90).apply(input, testContext())

    // Top-left of rotated image: was bottom-left of original = ORANGE (0,2) -> (0,0)
    // Wait, (x,y)=(0,2) in source -> dst_x = srcH-1-y = 3-1-2 = 0, dst_y = x = 0 -> (0,0)
    assertContentEquals(ORANGE, pixelAt(result, 0, 0), "dst(0,0) should be ORANGE from src(0,2)")

    // src(1,2)=PINK -> dst(0,1)
    assertContentEquals(PINK, pixelAt(result, 0, 1), "dst(0,1) should be PINK from src(1,2)")

    // src(0,0)=RED -> dst(2,0)
    assertContentEquals(RED, pixelAt(result, 2, 0), "dst(2,0) should be RED from src(0,0)")

    // src(3,0)=WHITE -> dst(2,3)
    assertContentEquals(WHITE, pixelAt(result, 2, 3), "dst(2,3) should be WHITE from src(3,0)")

    // src(3,2)=TEAL -> dst_x = 3-1-2=0, dst_y = 3 -> (0,3)
    assertContentEquals(TEAL, pixelAt(result, 0, 3), "dst(0,3) should be TEAL from src(3,2)")
  }

  // --- 180 deg ---

  @Test
  fun rotate180DimensionsPreserved() = runTest {
    val input = referenceImage(Orientation.NORMAL) // 4x3
    val result = ImageRotateTransform(180).apply(input, testContext())
    assertEquals(4, result.width)
    assertEquals(3, result.height)
    assertEquals(Orientation.NORMAL, result.orientation)
  }

  @Test
  fun rotate180PixelMapping() = runTest {
    // 180 deg: (x,y) -> (srcW-1-x, srcH-1-y), same dims
    // src(0,0)=RED -> dst(3,2)
    // src(3,2)=TEAL -> dst(0,0)
    // src(1,1)=CYAN -> dst(2,1)
    val input = referenceImage(Orientation.NORMAL)
    val result = ImageRotateTransform(180).apply(input, testContext())

    assertContentEquals(TEAL, pixelAt(result, 0, 0), "dst(0,0) should be TEAL from src(3,2)")
    assertContentEquals(RED, pixelAt(result, 3, 2), "dst(3,2) should be RED from src(0,0)")
    assertContentEquals(CYAN, pixelAt(result, 2, 1), "dst(2,1) should be CYAN from src(1,1)")

    // src(3,0)=WHITE -> dst(0,2)
    assertContentEquals(WHITE, pixelAt(result, 0, 2), "dst(0,2) should be WHITE from src(3,0)")

    // src(0,2)=ORANGE -> dst(3,0)
    assertContentEquals(ORANGE, pixelAt(result, 3, 0), "dst(3,0) should be ORANGE from src(0,2)")
  }

  // --- 270 deg CW (= 90 deg CCW) ---

  @Test
  fun rotate270DimensionsSwapped() = runTest {
    val input = referenceImage(Orientation.NORMAL) // 4x3
    val result = ImageRotateTransform(270).apply(input, testContext())
    assertEquals(3, result.width, "W should become srcH")
    assertEquals(4, result.height, "H should become srcW")
    assertEquals(Orientation.NORMAL, result.orientation)
  }

  @Test
  fun rotate270PixelMapping() = runTest {
    // 270 deg CW: (x,y) -> dst(y, srcW-1-x), new dims srcHxsrcW = 3x4
    // src(0,0)=RED -> dst(0,3)
    // src(3,0)=WHITE -> dst(0,0)
    // src(0,2)=ORANGE -> dst(2,3)
    // src(3,2)=TEAL -> dst(2,0)
    val input = referenceImage(Orientation.NORMAL)
    val result = ImageRotateTransform(270).apply(input, testContext())

    assertContentEquals(WHITE, pixelAt(result, 0, 0), "dst(0,0) should be WHITE from src(3,0)")
    assertContentEquals(RED, pixelAt(result, 0, 3), "dst(0,3) should be RED from src(0,0)")
    assertContentEquals(TEAL, pixelAt(result, 2, 0), "dst(2,0) should be TEAL from src(3,2)")
    assertContentEquals(ORANGE, pixelAt(result, 2, 3), "dst(2,3) should be ORANGE from src(0,2)")

    // src(1,1)=CYAN -> dst(1, 4-1-1) = dst(1,2)
    assertContentEquals(CYAN, pixelAt(result, 1, 2), "dst(1,2) should be CYAN from src(1,1)")
  }

  // --- Rotation preserves metadata ---

  @Test
  fun rotationPreservesMetadata() = runTest {
    val exif = byteArrayOf(0x45, 0x78, 0x69, 0x66) // "Exif"
    val input = referenceImage(Orientation.NORMAL).copy(
      metadata = ImageMetadata(exifBlob = exif.asBytes(), xmpBlob = null),
    )
    val result = ImageRotateTransform(90).apply(input, testContext())
    assertContentEquals(exif, result.metadata.exifBlob?.data, "EXIF should survive rotation")
  }

  // --- Buffer size correctness ---

  @Test
  fun rotateBufferSizeCorrect() = runTest {
    val input = referenceImage(Orientation.NORMAL)
    val result = ImageRotateTransform(90).apply(input, testContext())

    val buf = result.buffer as ByteArrayPixelBuffer
    assertEquals(result.width * result.height * 4, buf.data.size)
    assertEquals(result.width * 4, result.stride)
  }

  // --- Solid colour rotation invariant ---

  @Test
  fun solidColourUnchangedByAnyRotation() = runTest {
    val solid = solidColor(60, 40, r = 42, g = 84, b = 168)
    for (deg in listOf(90, 180, 270)) {
      val result = ImageRotateTransform(deg).apply(solid, testContext())

      // All pixels should be the same colour regardless of rotation angle
      assertContentEquals(intArrayOf(42, 84, 168, 255), pixelAt(result, 0, 0),
        "Top-left should remain same colour after ${deg} deg")
      val maxX = result.width - 1
      val maxY = result.height - 1
      assertContentEquals(intArrayOf(42, 84, 168, 255), pixelAt(result, maxX, maxY),
        "Bottom-right should remain same colour after ${deg} deg")
    }
  }

  @Test
  fun transformId() {
    assertEquals(TransformId("image-rotate"), ImageRotateTransform().id)
  }
}
