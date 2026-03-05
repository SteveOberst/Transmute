package dev.transmute.gstreamer

import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.transmute.gstreamer.GStreamerAndroidTestHelpers.codecOp
import dev.transmute.gstreamer.GStreamerAndroidTestHelpers.testContext
import dev.transmute.image.CanonicalImageDecodeOptions
import dev.transmute.image.HeifEncodeOptions
import dev.transmute.image.ImageFormat
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import org.junit.runner.RunWith

/**
 * Android instrumented tests for GStreamer image codecs.
 *
 * Tests exercise HEIF/HEIC/AVIF decode and encode pipelines.
 *
 * Encode tests require specific GStreamer elements:
 * - `x265enc` for HEIF/HEIC
 * - `av1enc` for AVIF
 *
 * Soft-skipped when `libgstreamer_bridge.so` is not bundled or
 * required elements are missing.
 *
 * Run: `./gradlew :transmute-gstreamer:connectedAndroidTest`
 */
@RunWith(AndroidJUnit4::class)
class GStreamerAndroidImageCodecIntegrationTest {

  @get:Rule val timeout: Timeout = Timeout.seconds(180)

  private val ctx = testContext()
  private val decoder = GstAndroidImageDecoder()
  private val encoder = GstAndroidImageEncoder()

  // -- Decoder metadata ---------------------------------------------------

  @Test
  fun decoder_supportedFormats_containsHeifHeicAvif() {
    val formats = decoder.supportedFormats
    assertTrue(ImageFormat.Heif in formats, "Must support HEIF")
    assertTrue(ImageFormat.Heic in formats, "Must support HEIC")
    assertTrue(ImageFormat.Avif in formats, "Must support AVIF")
  }

  // -- Encoder metadata ---------------------------------------------------

  @Test
  fun encoder_supportedFormats_containsHeifHeicAvif() {
    val formats = encoder.supportedFormats
    assertTrue(ImageFormat.Heif in formats, "Must support HEIF")
    assertTrue(ImageFormat.Heic in formats, "Must support HEIC")
    assertTrue(ImageFormat.Avif in formats, "Must support AVIF")
  }

  // -- HEIF encode -> decode roundtrip -------------------------------------

  @Test
  fun heif_encodeAndDecode_roundTrip() = runBlocking {
    if (!GStreamerAndroidTestHelpers.gstreamerAvailable) {
      println("SKIP: GStreamer not available - test skipped")
      return@runBlocking
    }
    if (!GStreamerJni.hasElement("x265enc")) {
      println("SKIP: x265enc not available - test skipped")
      return@runBlocking
    }
    val ir = GStreamerAndroidTestHelpers.solidColor(64, 64, r = 128, g = 64, b = 32)
    val encoded = codecOp("HEIF encode") {
      encoder.encode(ir, ImageFormat.Heif, HeifEncodeOptions(), ctx)
    } ?: return@runBlocking
    assertTrue(encoded.isNotEmpty(), "Encoded HEIF output must not be empty")

    val decoded = codecOp("HEIF decode") {
      decoder.decode(encoded, CanonicalImageDecodeOptions(), ctx)
    } ?: return@runBlocking
    assertNotNull(decoded)
    assertEquals(64, decoded.width)
    assertEquals(64, decoded.height)
  }

  // -- HEIC encode -> decode roundtrip -------------------------------------

  @Test
  fun heic_encodeAndDecode_roundTrip() = runBlocking {
    if (!GStreamerAndroidTestHelpers.gstreamerAvailable) {
      println("SKIP: GStreamer not available - test skipped")
      return@runBlocking
    }
    if (!GStreamerJni.hasElement("x265enc")) {
      println("SKIP: x265enc not available - test skipped")
      return@runBlocking
    }
    val ir = GStreamerAndroidTestHelpers.solidColor(64, 64, r = 200, g = 100, b = 50)
    val encoded = codecOp("HEIC encode") {
      encoder.encode(ir, ImageFormat.Heic, HeifEncodeOptions(format = ImageFormat.Heic), ctx)
    } ?: return@runBlocking
    assertTrue(encoded.isNotEmpty())

    val decoded = codecOp("HEIC decode") {
      decoder.decode(encoded, CanonicalImageDecodeOptions(), ctx)
    } ?: return@runBlocking
    assertNotNull(decoded)
    assertEquals(64, decoded.width)
    assertEquals(64, decoded.height)
  }

  // -- AVIF encode -> decode roundtrip -------------------------------------

  @Test
  fun avif_encodeAndDecode_roundTrip() = runBlocking {
    if (!GStreamerAndroidTestHelpers.gstreamerAvailable) {
      println("SKIP: GStreamer not available - test skipped")
      return@runBlocking
    }
    if (!GStreamerJni.hasElement("av1enc")) {
      println("SKIP: av1enc not available - test skipped")
      return@runBlocking
    }
    val ir = GStreamerAndroidTestHelpers.solidColor(64, 64, r = 100, g = 200, b = 150)
    val encoded = codecOp("AVIF encode") {
      encoder.encode(ir, ImageFormat.Avif, HeifEncodeOptions(), ctx)
    } ?: return@runBlocking
    assertTrue(encoded.isNotEmpty())

    val decoded = codecOp("AVIF decode") {
      decoder.decode(encoded, CanonicalImageDecodeOptions(), ctx)
    } ?: return@runBlocking
    assertNotNull(decoded)
    assertEquals(64, decoded.width)
    assertEquals(64, decoded.height)
  }
}
