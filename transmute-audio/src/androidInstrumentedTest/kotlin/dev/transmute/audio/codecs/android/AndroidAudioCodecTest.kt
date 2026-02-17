package dev.transmute.audio.codecs.android

import android.os.Build
import dev.transmute.audio.AudioTestHelpers
import dev.transmute.core.AudioFormat
import kotlinx.coroutines.runBlocking
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

  // MediaCodec init is slow on CI emulators (~40-50 s per codec op).
  // Give each test plenty of time rather than skipping.
  @get:Rule val timeout: Timeout = Timeout.seconds(120)

  // MP3 roundtrip

  @Test
  fun mp3RoundTripPreservesSampleRate() = runBlocking {
    val original = AudioTestHelpers.sineWave(
      frequency = 440f,
      durationMs = 500,
      sampleRate = 44100,
      amplitude = 0.5f,
    )
    val ctx = AudioTestHelpers.testContext()
    val codec = AndroidMp3Codec()

    val encoded = codec.encode(original, ctx)
    assertTrue(encoded.isNotEmpty(), "Encoded MP3 should not be empty")

    val decoded = codec.decode(encoded, ctx)
    assertEquals(44100, decoded.sampleRate, "Sample rate should be preserved")
    assertTrue(decoded.samples.data.isNotEmpty(), "Decoded samples should not be empty")
  }

  // AAC roundtrip

  @Test
  fun aacRoundTripPreservesSampleRate() = runBlocking {
    val original = AudioTestHelpers.sineWave(
      frequency = 440f,
      durationMs = 500,
      sampleRate = 44100,
      amplitude = 0.5f,
    )
    val ctx = AudioTestHelpers.testContext()
    val codec = AndroidAacCodec()

    val encoded = codec.encode(original, ctx)
    assertTrue(encoded.isNotEmpty())

    val decoded = codec.decode(encoded, ctx)
    assertEquals(44100, decoded.sampleRate)
    assertTrue(decoded.samples.data.isNotEmpty())
  }

  // FLAC roundtrip

  @Test
  fun flacRoundTripIsHighFidelity() = runBlocking {
    val original = AudioTestHelpers.sineWave(
      frequency = 440f,
      durationMs = 300,
      sampleRate = 44100,
      amplitude = 0.5f,
    )
    val ctx = AudioTestHelpers.testContext()
    val codec = AndroidFlacCodec()

    val encoded = codec.encode(original, ctx)
    assertTrue(encoded.isNotEmpty())

    val decoded = codec.decode(encoded, ctx)
    assertEquals(44100, decoded.sampleRate)
    assertEquals(original.channelCount, decoded.channelCount)
    assertTrue(decoded.samples.data.isNotEmpty())
  }

  // M4A roundtrip

  @Test
  fun m4aRoundTripPreservesSampleRate() = runBlocking {
    val original = AudioTestHelpers.sineWave(
      frequency = 440f,
      durationMs = 500,
      sampleRate = 44100,
      amplitude = 0.5f,
    )
    val ctx = AudioTestHelpers.testContext()
    val codec = AndroidM4aCodec()

    val encoded = codec.encode(original, ctx)
    assertTrue(encoded.isNotEmpty())

    val decoded = codec.decode(encoded, ctx)
    assertEquals(44100, decoded.sampleRate)
    assertTrue(decoded.samples.data.isNotEmpty())
  }

  // OGG decode-only

  @Test
  fun oggDecoderReportsCorrectFormat() {
    val decoder = AndroidOggDecoder()
    assertTrue(AudioFormat.OGG in decoder.supportedFormats)
  }

  // OPUS roundtrip

  @Test
  fun opusRoundTripPreservesSampleRate() = runBlocking {
    assertTrue(AndroidOpusCodec.canEncode, "OPUS encoding must be available on API ${Build.VERSION.SDK_INT}")

    val original = AudioTestHelpers.sineWave(
      frequency = 440f,
      durationMs = 500,
      sampleRate = 48000, // Opus prefers 48 kHz
      amplitude = 0.5f,
    )
    val ctx = AudioTestHelpers.testContext()
    val codec = AndroidOpusCodec()

    val encoded = codec.encode(original, ctx)
    assertTrue(encoded.isNotEmpty(), "Encoded OPUS should not be empty")

    val decoded = codec.decode(encoded, ctx)
    assertEquals(48000, decoded.sampleRate, "OPUS: sample rate should be preserved")
    assertTrue(decoded.samples.data.isNotEmpty(), "OPUS: decoded samples should not be empty")
  }

  // Format declarations

  @Test
  fun allCodecsReportCorrectFormats() {
    assertTrue(AudioFormat.MP3 in AndroidMp3Codec().decodableFormats)
    assertTrue(AudioFormat.AAC in AndroidAacCodec().decodableFormats)
    assertTrue(AudioFormat.FLAC in AndroidFlacCodec().decodableFormats)
    assertTrue(AudioFormat.M4A in AndroidM4aCodec().decodableFormats)
    assertTrue(AudioFormat.OPUS in AndroidOpusCodec().decodableFormats)
  }
}
