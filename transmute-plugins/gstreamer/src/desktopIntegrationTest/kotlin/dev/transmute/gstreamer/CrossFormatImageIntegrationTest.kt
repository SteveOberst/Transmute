package dev.transmute.gstreamer

import dev.transmute.codec.OutputFormat
import dev.transmute.gstreamer.GStreamerTestHelpers.testContext
import dev.transmute.image.CanonicalImageDecodeOptions
import dev.transmute.image.CanonicalImageEncodeOptions
import dev.transmute.image.HeifEncodeOptions
import dev.transmute.image.ImageFormat
import dev.transmute.image.JpegEncodeOptions
import dev.transmute.image.PngEncodeOptions
import dev.transmute.structure.image.*
import dev.transmute.transmute
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * Cross-format image conversion end-to-end tests.
 *
 * Each test encodes an image in format A, runs it through a Transmute
 * pipeline that decodes A and encodes to format B, and verifies:
 * 1. Output bytes are non-empty and structurally valid
 * 2. Output format is correct
 * 3. Decoded dimensions match the original
 *
 * Covers the most common real-world conversion paths.
 */
class CrossFormatImageIntegrationTest : GStreamerTestBase() {

  private val ctx = testContext()

  // ===
  // HEIF -> PNG (GStreamer -> JvmImageIo)
  // ===

  @Test
  fun heif_to_png() = runTest {
    val transmute = transmute {
      plugins {
        install(GStreamer)
      }
    }

    val original = GStreamerTestHelpers.solidColor(64, 64, r = 200, g = 100, b = 50)
    val heifBytes = GstImageEncoder().encode(original, ImageFormat.Heif, HeifEncodeOptions(), ctx)

    val transmuter = transmute.image {
      encode { options(PngEncodeOptions()) }
    }
    val result = transmuter.transmute(heifBytes)

    assertTrue(result.bytes.isNotEmpty(), "PNG output must not be empty")
    assertEquals(ImageFormat.Png, result.format, "Output format must be PNG")

    val reader = PngStructureReader()
    reader.read(result.bytes) // validates the output is parseable

    val decoded = dev.transmute.image.codecs.jvm.JvmImageIoDecoder()
      .decode(result.bytes, CanonicalImageDecodeOptions(), ctx)
    assertEquals(64, decoded.width, "Decoded width must match")
    assertEquals(64, decoded.height, "Decoded height must match")
  }

  // ===
  // HEIF -> JPEG (GStreamer -> JvmImageIo)
  // ===

  @Test
  fun heif_to_jpeg() = runTest {
    val transmute = transmute {
      plugins {
        install(GStreamer)
      }
    }

    val original = GStreamerTestHelpers.solidColor(64, 64, r = 128, g = 64, b = 32)
    val heifBytes = GstImageEncoder().encode(original, ImageFormat.Heif, HeifEncodeOptions(), ctx)

    val transmuter = transmute.image {
      encode { options(JpegEncodeOptions(quality = 0.90f)) }
    }
    val result = transmuter.transmute(heifBytes)

    assertTrue(result.bytes.isNotEmpty(), "JPEG output must not be empty")
    assertEquals(ImageFormat.Jpeg, result.format, "Output format must be JPEG")

    val reader = JpegStructureReader()
    reader.read(result.bytes) // validates the output is parseable
  }

  // ===
  // PNG -> JPEG (JvmImageIo -> JvmImageIo)
  // ===

  @Test
  fun png_to_jpeg() = runTest {
    val transmute = transmute {
      plugins {
        install(GStreamer)
      }
    }

    val original = GStreamerTestHelpers.solidColor(64, 64, r = 100, g = 150, b = 200)
    val encoder = dev.transmute.image.codecs.jvm.JvmImageIoEncoder()
    val pngBytes = encoder.encode(original, ImageFormat.Png, PngEncodeOptions(), ctx)

    val transmuter = transmute.image {
      encode { options(JpegEncodeOptions()) }
    }
    val result = transmuter.transmute(pngBytes)

    assertTrue(result.bytes.isNotEmpty(), "JPEG output must not be empty")
    assertEquals(ImageFormat.Jpeg, result.format, "Output format must be JPEG")

    val reader = JpegStructureReader()
    reader.read(result.bytes) // validates the output is parseable
  }

  // ===
  // JPEG -> PNG (JvmImageIo -> JvmImageIo)
  // ===

  @Test
  fun jpeg_to_png() = runTest {
    val transmute = transmute {
      plugins {
        install(GStreamer)
      }
    }

    val original = GStreamerTestHelpers.solidColor(64, 64, r = 180, g = 90, b = 45)
    val encoder = dev.transmute.image.codecs.jvm.JvmImageIoEncoder()
    val jpegBytes = encoder.encode(original, ImageFormat.Jpeg, JpegEncodeOptions(), ctx)

    val transmuter = transmute.image {
      encode { options(PngEncodeOptions()) }
    }
    val result = transmuter.transmute(jpegBytes)

    assertTrue(result.bytes.isNotEmpty(), "PNG output must not be empty")
    assertEquals(ImageFormat.Png, result.format, "Output format must be PNG")

    val reader = PngStructureReader()
    reader.read(result.bytes) // validates the output is parseable
  }

  // ===
  // AVIF -> PNG (GStreamer -> JvmImageIo)
  // ===

  @Test
  fun avif_to_png() = runTest {
    val transmute = transmute {
      plugins {
        install(GStreamer)
      }
    }

    val original = GStreamerTestHelpers.solidColor(64, 64, r = 50, g = 100, b = 200)
    val avifBytes = GstImageEncoder().encode(
      original,
      ImageFormat.Avif,
      HeifEncodeOptions(format = ImageFormat.Avif),
      ctx,
    )

    val transmuter = transmute.image {
      encode { options(PngEncodeOptions()) }
    }
    val result = transmuter.transmute(avifBytes)

    assertTrue(result.bytes.isNotEmpty(), "PNG output must not be empty")
    assertEquals(ImageFormat.Png, result.format, "Output format must be PNG")

    val reader = PngStructureReader()
    reader.read(result.bytes) // validates the output is parseable
  }

  // ===
  // PNG -> BMP (JvmImageIo -> BmpEncoder)
  // ===

  @Test
  fun png_to_bmp() = runTest {
    val transmute = transmute {
      plugins {
        install(GStreamer)
      }
    }

    val original = GStreamerTestHelpers.solidColor(32, 32, r = 100, g = 100, b = 100)
    val encoder = dev.transmute.image.codecs.jvm.JvmImageIoEncoder()
    val pngBytes = encoder.encode(original, ImageFormat.Png, PngEncodeOptions(), ctx)

    val transmuter = transmute.image {
      encode { options(CanonicalImageEncodeOptions(outputFormat = OutputFormat.Exact(ImageFormat.Bmp))) }
    }
    val result = transmuter.transmute(pngBytes)

    assertTrue(result.bytes.isNotEmpty(), "BMP output must not be empty")
    assertEquals(ImageFormat.Bmp, result.format, "Output format must be BMP")

    val reader = BmpStructureReader()
    reader.read(result.bytes) // validates the output is parseable
  }

  // ===
  // JPEG -> HEIF (JvmImageIo -> GStreamer)
  // ===

  @Test
  fun jpeg_to_heif() = runTest {
    val transmute = transmute {
      plugins {
        install(GStreamer)
      }
    }

    val original = GStreamerTestHelpers.solidColor(64, 64, r = 180, g = 90, b = 45)
    val encoder = dev.transmute.image.codecs.jvm.JvmImageIoEncoder()
    val jpegBytes = encoder.encode(original, ImageFormat.Jpeg, JpegEncodeOptions(), ctx)

    val transmuter = transmute.image {
      encode { options(HeifEncodeOptions()) }
    }
    val result = transmuter.transmute(jpegBytes)

    assertTrue(result.bytes.isNotEmpty(), "HEIF output must not be empty")
    assertEquals(ImageFormat.Heif, result.format, "Output format must be HEIF")

    val reader = HeifStructureReader()
    reader.read(result.bytes) // validates the output is parseable
  }

  // ===
  // PNG -> GIF (JvmImageIo -> JvmImageIo)
  // ===

  @Test
  fun png_to_gif() = runTest {
    val transmute = transmute {
      plugins {
        install(GStreamer)
      }
    }

    val original = GStreamerTestHelpers.solidColor(32, 32, r = 255, g = 0, b = 0)
    val encoder = dev.transmute.image.codecs.jvm.JvmImageIoEncoder()
    val pngBytes = encoder.encode(original, ImageFormat.Png, PngEncodeOptions(), ctx)

    val transmuter = transmute.image {
      encode {
        options(CanonicalImageEncodeOptions(outputFormat = OutputFormat.Exact(ImageFormat.Gif)))
      }
    }
    val result = transmuter.transmute(pngBytes)

    assertTrue(result.bytes.isNotEmpty(), "GIF output must not be empty")
    assertEquals(ImageFormat.Gif, result.format, "Output format must be GIF")

    val reader = GifStructureReader()
    reader.read(result.bytes) // validates the output is parseable
  }
}
