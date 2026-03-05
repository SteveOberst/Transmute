package dev.transmute.audio.codecs.jvm

import dev.transmute.audio.AudioFormat
import dev.transmute.audio.AudioTestHelpers
import dev.transmute.audio.CanonicalAudioEncodeOptions
import kotlin.test.*
import kotlinx.coroutines.test.runTest

class JvmOggVorbisCodecIntegrationTest {

  private val codec = JvmOggVorbisCodec()

  // -- Format declarations --

  @Test
  fun decodableFormatsContainsOgg() {
    assertTrue(AudioFormat.Ogg in codec.decodableFormats)
  }

  @Test
  fun encodableFormatsIsEmpty() {
    // Native OGG/Vorbis encoding requires the transmute-gstreamer module.
    assertTrue(codec.encodableFormats.isEmpty())
  }

  // -- Encode (requires transmute-gstreamer) --

  @Test
  fun encodeThrowsWithoutGstreamer() = runTest {
    val ir = AudioTestHelpers.sineWave(durationMs = 50)
    assertFailsWith<IllegalStateException> {
      codec.encode(ir, AudioFormat.Ogg, CanonicalAudioEncodeOptions(), AudioTestHelpers.testContext())
    }
  }
}
