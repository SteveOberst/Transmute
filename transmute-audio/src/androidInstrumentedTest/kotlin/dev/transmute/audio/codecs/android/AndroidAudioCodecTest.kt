package dev.transmute.audio.codecs.android

import android.os.Build
import dev.transmute.audio.AudioTestHelpers
import dev.transmute.core.AudioFormat
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Android instrumented tests for MediaCodec-based audio codecs.
 *
 * Run: ./gradlew :transmute-audio:connectedAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class AndroidAudioCodecTest {

  @get:Rule val timeout: Timeout = Timeout.seconds(30)

  // -----------------------------------------------------------------------
  // MP3 roundtrip
  // -----------------------------------------------------------------------

  @Test
  fun mp3RoundTripPreservesSampleRate() = runTest {
    println(">>> mp3RoundTripPreservesSampleRate: START")
    val original = AudioTestHelpers.sineWave(
      frequency = 440f,
      durationMs = 500,
      sampleRate = 44100,
      amplitude = 0.5f,
    )
    val ctx = AudioTestHelpers.testContext()
    val codec = AndroidMp3Codec()

    println(">>> mp3RoundTripPreservesSampleRate: encoding...")
    val encoded = codec.encode(original, ctx)
    assertTrue(encoded.isNotEmpty(), "Encoded MP3 should not be empty")
    println(">>> mp3RoundTripPreservesSampleRate: encoded ${encoded.size} bytes, decoding...")

    val decoded = codec.decode(encoded, ctx)
    assertEquals(44100, decoded.sampleRate, "Sample rate should be preserved")
    assertTrue(decoded.samples.data.isNotEmpty(), "Decoded samples should not be empty")
    println(">>> mp3RoundTripPreservesSampleRate: PASS")
  }

  // -----------------------------------------------------------------------
  // AAC roundtrip
  // -----------------------------------------------------------------------

  @Test
  fun aacRoundTripPreservesSampleRate() = runTest {
    println(">>> aacRoundTripPreservesSampleRate: START")
    val original = AudioTestHelpers.sineWave(
      frequency = 440f,
      durationMs = 500,
      sampleRate = 44100,
      amplitude = 0.5f,
    )
    val ctx = AudioTestHelpers.testContext()
    val codec = AndroidAacCodec()

    println(">>> aacRoundTripPreservesSampleRate: encoding...")
    val encoded = codec.encode(original, ctx)
    assertTrue(encoded.isNotEmpty())
    println(">>> aacRoundTripPreservesSampleRate: encoded ${encoded.size} bytes, decoding...")

    val decoded = codec.decode(encoded, ctx)
    assertEquals(44100, decoded.sampleRate)
    assertTrue(decoded.samples.data.isNotEmpty())
    println(">>> aacRoundTripPreservesSampleRate: PASS")
  }

  // -----------------------------------------------------------------------
  // FLAC roundtrip
  // -----------------------------------------------------------------------

  @Test
  fun flacRoundTripIsHighFidelity() = runTest {
    println(">>> flacRoundTripIsHighFidelity: START")
    val original = AudioTestHelpers.sineWave(
      frequency = 440f,
      durationMs = 300,
      sampleRate = 44100,
      amplitude = 0.5f,
    )
    val ctx = AudioTestHelpers.testContext()
    val codec = AndroidFlacCodec()

    println(">>> flacRoundTripIsHighFidelity: encoding...")
    val encoded = codec.encode(original, ctx)
    assertTrue(encoded.isNotEmpty())
    println(">>> flacRoundTripIsHighFidelity: encoded ${encoded.size} bytes, decoding...")

    val decoded = codec.decode(encoded, ctx)
    assertEquals(44100, decoded.sampleRate)
    assertEquals(original.channelCount, decoded.channelCount)
    assertTrue(decoded.samples.data.isNotEmpty())
    println(">>> flacRoundTripIsHighFidelity: PASS")
  }

  // -----------------------------------------------------------------------
  // M4A roundtrip
  // -----------------------------------------------------------------------

  @Test
  fun m4aRoundTripPreservesSampleRate() = runTest {
    println(">>> m4aRoundTripPreservesSampleRate: START")
    val original = AudioTestHelpers.sineWave(
      frequency = 440f,
      durationMs = 500,
      sampleRate = 44100,
      amplitude = 0.5f,
    )
    val ctx = AudioTestHelpers.testContext()
    val codec = AndroidM4aCodec()

    println(">>> m4aRoundTripPreservesSampleRate: encoding...")
    val encoded = codec.encode(original, ctx)
    assertTrue(encoded.isNotEmpty())
    println(">>> m4aRoundTripPreservesSampleRate: encoded ${encoded.size} bytes, decoding...")

    val decoded = codec.decode(encoded, ctx)
    assertEquals(44100, decoded.sampleRate)
    assertTrue(decoded.samples.data.isNotEmpty())
    println(">>> m4aRoundTripPreservesSampleRate: PASS")
  }

  // -----------------------------------------------------------------------
  // OGG decode-only
  // -----------------------------------------------------------------------

  @Test
  fun oggDecoderReportsCorrectFormat() {
    println(">>> oggDecoderReportsCorrectFormat: START")
    val decoder = AndroidOggDecoder()
    assertTrue(AudioFormat.OGG in decoder.supportedFormats)
    println(">>> oggDecoderReportsCorrectFormat: PASS")
  }

  // -----------------------------------------------------------------------    
  // OPUS roundtrip
  // -----------------------------------------------------------------------    

  @Test
  fun opusRoundTripPreservesSampleRate() = runTest {
    println(">>> opusRoundTripPreservesSampleRate: START (API=${Build.VERSION.SDK_INT})")
    assertTrue(AndroidOpusCodec.canEncode, "OPUS encoding must be available on API ${Build.VERSION.SDK_INT}")

    val original = AudioTestHelpers.sineWave(
      frequency = 440f,
      durationMs = 500,
      sampleRate = 48000, // Opus prefers 48 kHz
      amplitude = 0.5f,
    )
    val ctx = AudioTestHelpers.testContext()
    val codec = AndroidOpusCodec()

    println(">>> opusRoundTripPreservesSampleRate: encoding...")
    val encoded = codec.encode(original, ctx)
    assertTrue(encoded.isNotEmpty(), "Encoded OPUS should not be empty")
    println(">>> opusRoundTripPreservesSampleRate: encoded ${encoded.size} bytes, decoding...")

    val decoded = codec.decode(encoded, ctx)
    assertEquals(48000, decoded.sampleRate, "OPUS: sample rate should be preserved")
    assertTrue(decoded.samples.data.isNotEmpty(), "OPUS: decoded samples should not be empty")
    println(">>> opusRoundTripPreservesSampleRate: PASS")
  }

  // -----------------------------------------------------------------------    
  // Format declarations
  // -----------------------------------------------------------------------    

  @Test
  fun allCodecsReportCorrectFormats() {
    println(">>> allCodecsReportCorrectFormats: START")
    assertTrue(AudioFormat.MP3 in AndroidMp3Codec().decodableFormats)
    assertTrue(AudioFormat.AAC in AndroidAacCodec().decodableFormats)
    assertTrue(AudioFormat.FLAC in AndroidFlacCodec().decodableFormats)
    assertTrue(AudioFormat.M4A in AndroidM4aCodec().decodableFormats)
    assertTrue(AudioFormat.OPUS in AndroidOpusCodec().decodableFormats)
    println(">>> allCodecsReportCorrectFormats: PASS")
  }
}
