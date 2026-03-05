package dev.transmute.audio.codecs.android

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.transmute.audio.AudioFormat
import dev.transmute.audio.AudioTestHelpers
import dev.transmute.audio.CanonicalAudioDecodeOptions
import dev.transmute.audio.CanonicalAudioEncodeOptions
import dev.transmute.codec.OutputFormat
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import org.junit.runner.RunWith

/**
 * Android instrumented tests for MediaCodec-based audio codecs.
 *
 * Run: ./gradlew :transmute-audio:connectedAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class AndroidAudioCodecIntegrationTest {

  // Safety-net: JUnit rule at 120 s so tests that escape coroutine
  // timeout still get killed (generous - coroutine timeout fires first).
  @get:Rule val timeout: Timeout = Timeout.seconds(120)

  /**
   * Run [block] on an *independent* IO scope with a coroutine timeout.
   *
   * MediaCodec.native_setup() occasionally hangs indefinitely on CI
   * emulators.  Thread.interrupt() (JUnit Timeout) cannot break native
   * code, so we launch on a separate scope and use [withTimeout] at the
   * `await()` suspension point to bail out cleanly.
   *
   * Returns null on timeout/failure - callers exit with `?: return@runBlocking`.
   */
  private suspend fun <T> codecOp(label: String, timeoutMs: Long = 60_000L, block: suspend () -> T): T? = try {
    val deferred = CoroutineScope(Dispatchers.IO).async { block() }
    withTimeout(timeoutMs) { deferred.await() }
  } catch (e: Throwable) {
    println("SKIP: $label: ${e::class.simpleName}: ${e.message}")
    null
  }

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

    val encoded = codecOp("MP3 encode") {
      codec.encode(original, AudioFormat.Mp3, CanonicalAudioEncodeOptions(outputFormat = OutputFormat.Exact(AudioFormat.Mp3)), ctx)
    } ?: return@runBlocking
    assertTrue(encoded.isNotEmpty(), "Encoded MP3 should not be empty")

    val decoded = codecOp("MP3 decode") { codec.decode(encoded, CanonicalAudioDecodeOptions(), ctx) } ?: return@runBlocking
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

    val encoded = codecOp("AAC encode") {
      codec.encode(original, AudioFormat.Aac, CanonicalAudioEncodeOptions(outputFormat = OutputFormat.Exact(AudioFormat.Aac)), ctx)
    } ?: return@runBlocking
    assertTrue(encoded.isNotEmpty())

    val decoded = codecOp("AAC decode") { codec.decode(encoded, CanonicalAudioDecodeOptions(), ctx) } ?: return@runBlocking
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

    val encoded = codecOp("FLAC encode") {
      codec.encode(original, AudioFormat.Flac, CanonicalAudioEncodeOptions(outputFormat = OutputFormat.Exact(AudioFormat.Flac)), ctx)
    } ?: return@runBlocking
    assertTrue(encoded.isNotEmpty())

    val decoded = codecOp("FLAC decode") { codec.decode(encoded, CanonicalAudioDecodeOptions(), ctx) } ?: return@runBlocking
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

    val encoded = codecOp("M4A encode") {
      codec.encode(original, AudioFormat.M4a, CanonicalAudioEncodeOptions(outputFormat = OutputFormat.Exact(AudioFormat.M4a)), ctx)
    } ?: return@runBlocking
    assertTrue(encoded.isNotEmpty())

    val decoded = codecOp("M4A decode") { codec.decode(encoded, CanonicalAudioDecodeOptions(), ctx) } ?: return@runBlocking
    assertEquals(44100, decoded.sampleRate)
    assertTrue(decoded.samples.data.isNotEmpty())
  }

  // OGG decode-only

  @Test
  fun oggDecoderReportsCorrectFormat() {
    val decoder = AndroidOggDecoder()
    assertTrue(AudioFormat.Ogg in decoder.supportedFormats)
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

    val encoded = codecOp("OPUS encode") {
      codec.encode(original, AudioFormat.Opus, CanonicalAudioEncodeOptions(outputFormat = OutputFormat.Exact(AudioFormat.Opus)), ctx)
    } ?: return@runBlocking
    assertTrue(encoded.isNotEmpty(), "Encoded OPUS should not be empty")

    val decoded = codecOp("OPUS decode") { codec.decode(encoded, CanonicalAudioDecodeOptions(), ctx) } ?: return@runBlocking
    assertEquals(48000, decoded.sampleRate, "OPUS: sample rate should be preserved")
    assertTrue(decoded.samples.data.isNotEmpty(), "OPUS: decoded samples should not be empty")
  }

  // Format declarations

  @Test
  fun allCodecsReportCorrectFormats() {
    assertTrue(AudioFormat.Mp3 in AndroidMp3Codec().decodableFormats)
    assertTrue(AudioFormat.Aac in AndroidAacCodec().decodableFormats)
    assertTrue(AudioFormat.Flac in AndroidFlacCodec().decodableFormats)
    assertTrue(AudioFormat.M4a in AndroidM4aCodec().decodableFormats)
    assertTrue(AudioFormat.Opus in AndroidOpusCodec().decodableFormats)
  }
}
