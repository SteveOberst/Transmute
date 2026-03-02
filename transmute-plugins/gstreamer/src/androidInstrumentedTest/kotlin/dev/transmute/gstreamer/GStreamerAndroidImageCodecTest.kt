package dev.transmute.gstreamer

import dev.transmute.gstreamer.GStreamerAndroidTestHelpers.codecOp
import dev.transmute.gstreamer.GStreamerAndroidTestHelpers.testContext
import dev.transmute.image.CanonicalImageDecodeOptions
import dev.transmute.image.HeifEncodeOptions
import dev.transmute.image.ImageFormat
import dev.transmute.model.core.Bytes
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

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
class GStreamerAndroidImageCodecTest {

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

    // -- Sniff tests --------------------------------------------------------

    @Test
    fun sniff_heicBrand() {
        val header = byteArrayOf(
            0x00, 0x00, 0x00, 0x20,
            'f'.code.toByte(), 't'.code.toByte(), 'y'.code.toByte(), 'p'.code.toByte(),
            'h'.code.toByte(), 'e'.code.toByte(), 'i'.code.toByte(), 'c'.code.toByte(),
        )
        assertEquals(ImageFormat.Heic, decoder.sniff(Bytes(header)))
    }

    @Test
    fun sniff_mif1Brand_isHeif() {
        val header = byteArrayOf(
            0x00, 0x00, 0x00, 0x20,
            'f'.code.toByte(), 't'.code.toByte(), 'y'.code.toByte(), 'p'.code.toByte(),
            'm'.code.toByte(), 'i'.code.toByte(), 'f'.code.toByte(), '1'.code.toByte(),
        )
        assertEquals(ImageFormat.Heif, decoder.sniff(Bytes(header)))
    }

    @Test
    fun sniff_avifBrand() {
        val header = byteArrayOf(
            0x00, 0x00, 0x00, 0x20,
            'f'.code.toByte(), 't'.code.toByte(), 'y'.code.toByte(), 'p'.code.toByte(),
            'a'.code.toByte(), 'v'.code.toByte(), 'i'.code.toByte(), 'f'.code.toByte(),
        )
        assertEquals(ImageFormat.Avif, decoder.sniff(Bytes(header)))
    }

    @Test
    fun sniff_unknownBrand_returnsNull() {
        val header = byteArrayOf(
            0x00, 0x00, 0x00, 0x20,
            'f'.code.toByte(), 't'.code.toByte(), 'y'.code.toByte(), 'p'.code.toByte(),
            'X'.code.toByte(), 'X'.code.toByte(), 'X'.code.toByte(), 'X'.code.toByte(),
        )
        assertNull(decoder.sniff(Bytes(header)))
    }

    @Test
    fun sniff_nonIsoBmff_returnsNull() {
        assertNull(decoder.sniff(Bytes(byteArrayOf(
            'R'.code.toByte(), 'I'.code.toByte(), 'F'.code.toByte(), 'F'.code.toByte(),
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        ))))
    }

    @Test
    fun sniff_shortData_returnsNull() {
        assertNull(decoder.sniff(Bytes(byteArrayOf(0x00, 0x00, 0x00))))
    }

    // -- HEIF encode -> decode roundtrip -------------------------------------

    @Test
    fun heif_encodeAndDecode_roundTrip() = runBlocking {
        if (!GStreamerAndroidTestHelpers.gstreamerAvailable) {
            println("SKIP: GStreamer not available – test skipped")
            return@runBlocking
        }
        if (!GStreamerJni.hasElement("x265enc")) {
            println("SKIP: x265enc not available – test skipped")
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
            println("SKIP: GStreamer not available – test skipped")
            return@runBlocking
        }
        if (!GStreamerJni.hasElement("x265enc")) {
            println("SKIP: x265enc not available – test skipped")
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
            println("SKIP: GStreamer not available – test skipped")
            return@runBlocking
        }
        if (!GStreamerJni.hasElement("av1enc")) {
            println("SKIP: av1enc not available – test skipped")
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
