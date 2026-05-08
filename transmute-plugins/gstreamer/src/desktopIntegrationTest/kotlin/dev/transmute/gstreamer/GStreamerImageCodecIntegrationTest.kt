package dev.transmute.gstreamer

import dev.transmute.gstreamer.GStreamerTestHelpers.testContext
import dev.transmute.image.CanonicalImageDecodeOptions
import dev.transmute.image.HeifEncodeOptions
import dev.transmute.image.ImageFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * End-to-end integration tests for GStreamer image codecs.
 *
 * Tests exercise HEIF/HEIC/AVIF decode (GStreamer -> PNG -> ImageIO) and
 * encode (ImageIO -> PNG -> GStreamer -> target) pipelines.
 *
 * Encode tests require specific GStreamer elements:
 * - `x265enc` for HEIF/HEIC
 * - `av1enc` for AVIF
 */
class GStreamerImageCodecIntegrationTest : GStreamerTestBase() {

  private val ctx = testContext()
  private val decoder = GstImageDecoder()
  private val encoder = GstImageEncoder()

  // -- Decoder metadata ---

  @Test
  fun decoder_supportedFormats_containsHeifHeicAvif() {
    val formats = decoder.supportedFormats
    assertTrue(ImageFormat.Heif in formats, "Must support HEIF")
    assertTrue(ImageFormat.Heic in formats, "Must support HEIC")
    assertTrue(ImageFormat.Avif in formats, "Must support AVIF")
  }

  // -- Encoder metadata ---

  @Test
  fun encoder_supportedFormats_containsHeifHeicAvif() {
    val formats = encoder.supportedFormats
    assertTrue(ImageFormat.Heif in formats, "Must support HEIF")
    assertTrue(ImageFormat.Heic in formats, "Must support HEIC")
    assertTrue(ImageFormat.Avif in formats, "Must support AVIF")
  }

  // -- HEIF encode -> decode roundtrip ---

  @Test
  fun heif_encodeAndDecode_roundTrip() = runTest {
    assumeHeifImageEncodeSupported()
    val ir = GStreamerTestHelpers.solidColor(64, 64, r = 128, g = 64, b = 32)
    val encoded = encoder.encode(ir, ImageFormat.Heif, HeifEncodeOptions(), ctx)
    assertTrue(encoded.isNotEmpty(), "Encoded HEIF output must not be empty")

    val decoded = decoder.decode(encoded, CanonicalImageDecodeOptions(), ctx)
    assertNotNull(decoded, "Decoded ImageIR must not be null")
    assertEquals(64, decoded.width, "Width must survive roundtrip")
    assertEquals(64, decoded.height, "Height must survive roundtrip")
  }

  // -- HEIC encode -> decode roundtrip ---

  @Test
  fun heic_encodeAndDecode_roundTrip() = runTest {
    assumeHeifImageEncodeSupported()
    val ir = GStreamerTestHelpers.solidColor(64, 64, r = 200, g = 100, b = 50)
    val encoded = encoder.encode(ir, ImageFormat.Heic, HeifEncodeOptions(format = ImageFormat.Heic), ctx)
    assertTrue(encoded.isNotEmpty(), "Encoded HEIC output must not be empty")

    val decoded = decoder.decode(encoded, CanonicalImageDecodeOptions(), ctx)
    assertNotNull(decoded, "Decoded ImageIR must not be null")
    assertEquals(64, decoded.width, "Width must survive roundtrip")
    assertEquals(64, decoded.height, "Height must survive roundtrip")
  }

  // -- AVIF encode -> decode roundtrip ---

  @Test
  fun avif_encodeAndDecode_roundTrip() = runTest {
    assumeAvifImageEncodeSupported()
    val ir = GStreamerTestHelpers.solidColor(64, 64, r = 50, g = 100, b = 200)
    val encoded = encoder.encode(ir, ImageFormat.Avif, HeifEncodeOptions(format = ImageFormat.Avif), ctx)
    assertTrue(encoded.isNotEmpty(), "Encoded AVIF output must not be empty")

    val decoded = decoder.decode(encoded, CanonicalImageDecodeOptions(), ctx)
    assertNotNull(decoded, "Decoded ImageIR must not be null")
    assertEquals(64, decoded.width, "Width must survive roundtrip")
    assertEquals(64, decoded.height, "Height must survive roundtrip")
  }

  // -- Encode produces valid ISO BMFF header ---

  @Test
  fun heif_encode_producesIsoBmffOutput() = runTest {
    assumeHeifImageEncodeSupported()
    val ir = GStreamerTestHelpers.solidColor(32, 32, r = 0, g = 0, b = 0)
    val encoded = encoder.encode(ir, ImageFormat.Heif, HeifEncodeOptions(), ctx)
    assertTrue(encoded.size > 12, "Output must have at least 12 bytes")

    // ISO BMFF should have "ftyp" at offset 4
    val ftyp = encoded.data.sliceArray(4 until 8).decodeToString()
    assertEquals("ftyp", ftyp, "HEIF output must be an ISO BMFF container with ftyp box")
  }
}
