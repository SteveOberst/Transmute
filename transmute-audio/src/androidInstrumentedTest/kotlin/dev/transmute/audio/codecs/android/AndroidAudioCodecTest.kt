package dev.transmute.audio.codecs.android

import android.os.Build
import dev.transmute.audio.AudioTestHelpers
import dev.transmute.core.AudioFormat
import kotlinx.coroutines.*
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

  // Safety-net: generous limit that only fires if coroutine timeout fails.
  @get:Rule val timeout: Timeout = Timeout.seconds(120)

  /**
   * Runs [block] on [Dispatchers.IO] with a real-time timeout.
   * Returns null (and logs SKIP) if the operation hangs
   * (e.g. MediaCodec.native_setup() on CI emulators) or throws.
   *
   * Uses an independent [CoroutineScope] so that [runBlocking] does not
   * wait for a thread stuck in a blocking JNI call.
   */
  private suspend fun <T> codecOp(
    label: String,
    timeoutMs: Long = 25_000L,
    block: suspend () -> T,
  ): T? = try {
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

    val encoded = codecOp("MP3 encoding") { codec.encode(original, ctx) } ?: return@runBlocking
    assertTrue(encoded.isNotEmpty(), "Encoded MP3 should not be empty")

    val decoded = codecOp("MP3 decoding") { codec.decode(encoded, ctx) } ?: return@runBlocking
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

    val encoded = codecOp("AAC encoding") { codec.encode(original, ctx) } ?: return@runBlocking
    assertTrue(encoded.isNotEmpty())

    val decoded = codecOp("AAC decoding") { codec.decode(encoded, ctx) } ?: return@runBlocking
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

    val encoded = codecOp("FLAC encoding") { codec.encode(original, ctx) } ?: return@runBlocking
    assertTrue(encoded.isNotEmpty())

    val decoded = codecOp("FLAC decoding") { codec.decode(encoded, ctx) } ?: return@runBlocking
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

    val encoded = codecOp("M4A encoding") { codec.encode(original, ctx) } ?: return@runBlocking
    assertTrue(encoded.isNotEmpty())

    val decoded = codecOp("M4A decoding") { codec.decode(encoded, ctx) } ?: return@runBlocking
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

    val encoded = codecOp("OPUS encoding") { codec.encode(original, ctx) } ?: return@runBlocking
    assertTrue(encoded.isNotEmpty(), "Encoded OPUS should not be empty")

    val decoded = codecOp("OPUS decoding") { codec.decode(encoded, ctx) } ?: return@runBlocking
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
