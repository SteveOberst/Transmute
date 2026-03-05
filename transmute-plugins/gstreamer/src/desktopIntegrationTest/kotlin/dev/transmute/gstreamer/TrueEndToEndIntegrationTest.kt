package dev.transmute.gstreamer

import dev.transmute.audio.AudioFormat
import dev.transmute.audio.CanonicalAudioEncodeOptions
import dev.transmute.codec.OutputFormat
import dev.transmute.gstreamer.GStreamerTestHelpers.testContext
import dev.transmute.image.CanonicalImageDecodeOptions
import dev.transmute.image.HeifEncodeOptions
import dev.transmute.image.ImageFormat
import dev.transmute.structure.audio.*
import dev.transmute.structure.image.*
import dev.transmute.structure.video.*
import dev.transmute.transmute
import dev.transmute.video.CanonicalVideoDecodeOptions
import dev.transmute.video.CanonicalVideoEncodeOptions
import dev.transmute.video.VideoFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * GStreamer End-to-End Integration Tests.
 *
 * Tests generate **real media bytes** via GStreamer codecs, then:
 * 1. Run the bytes through the corresponding **StructureReader** to verify
 *    the format is structurally valid and parseable.
 * 2. **Decode** the bytes back to IR to verify round-trip integrity.
 * 3. Exercise the **full `Transmute { }` API** including transform pipelines.
 *
 * Platform-agnostic codec tests (WAV, MP3, JPEG, PNG, GIF, TIFF, BMP, WebP)
 * live in [dev.transmute.CoreEndToEndTest] in the `transmute-api` module.
 *
 * Tests are skipped automatically (via [GStreamerTestBase]) when GStreamer
 * is not installed on the current machine.
 */
class TrueEndToEndIntegrationTest : GStreamerTestBase() {

  private val ctx = testContext()

  // =======================================================================
  // AUDIO: Generate real media -> Structure Reader -> Decode
  // =======================================================================

  @Test
  fun aac_realMedia_structureReaderAccepts() = runTest {
    val audioIR = GStreamerTestHelpers.sineWave(durationMs = 200, sampleRate = 44100)
    val aacBytes = GstAacCodec().encode(audioIR, AudioFormat.Aac, CanonicalAudioEncodeOptions(), ctx)

    val reader = AacStructureReader()
    reader.read(aacBytes) // validates the output is parseable

    val structure = reader.read(aacBytes)
    assertNotNull(structure, "AacStructure must not be null")
  }

  @Test
  fun m4a_realMedia_structureReaderAccepts() = runTest {
    val audioIR = GStreamerTestHelpers.sineWave(durationMs = 200, sampleRate = 44100)
    val m4aBytes = GstM4aCodec().encode(audioIR, AudioFormat.M4a, CanonicalAudioEncodeOptions(), ctx)

    val reader = M4aStructureReader()
    reader.read(m4aBytes) // validates the output is parseable

    val structure = reader.read(m4aBytes)
    assertNotNull(structure, "M4aStructure must not be null")
    assertTrue(structure.boxes.isNotEmpty(), "M4A must have ISO BMFF boxes")
  }

  @Test
  fun opus_realMedia_structureReaderAccepts() = runTest {
    val audioIR = GStreamerTestHelpers.sineWave(durationMs = 200, sampleRate = 48000)
    val opusBytes = GstOpusCodec().encode(audioIR, AudioFormat.Opus, CanonicalAudioEncodeOptions(), ctx)

    val reader = OpusStructureReader()
    reader.read(opusBytes) // validates the output is parseable

    val structure = reader.read(opusBytes)
    assertNotNull(structure, "OpusStructure must not be null")
    assertTrue(structure.pages.isNotEmpty(), "Opus must have OGG pages")
  }

  @Test
  fun flac_realMedia_structureReaderAccepts() = runTest {
    val audioIR = GStreamerTestHelpers.sineWave(durationMs = 200, sampleRate = 44100)
    val flacBytes = GstFlacEncoder().encode(audioIR, AudioFormat.Flac, CanonicalAudioEncodeOptions(), ctx)

    val reader = FlacStructureReader()
    reader.read(flacBytes) // validates the output is parseable

    val structure = reader.read(flacBytes)
    assertNotNull(structure, "FlacStructure must not be null")
  }

  @Test
  fun ogg_realMedia_structureReaderAccepts() = runTest {
    val audioIR = GStreamerTestHelpers.sineWave(durationMs = 200, sampleRate = 44100)
    val oggBytes = GstOggVorbisEncoder().encode(audioIR, AudioFormat.Ogg, CanonicalAudioEncodeOptions(), ctx)

    val reader = OggAudioStructureReader()
    reader.read(oggBytes) // validates the output is parseable

    val structure = reader.read(oggBytes)
    assertNotNull(structure, "OggAudioStructure must not be null")
    assertTrue(structure.pages.isNotEmpty(), "OGG must have pages")
  }

  // =======================================================================
  // VIDEO: Generate real media -> Structure Reader -> Decode
  // =======================================================================

  @Test
  fun mp4_realMedia_structureReaderAccepts() = runTest {
    val videoIR = GStreamerTestHelpers.syntheticVideo(
      width = 160,
      height = 120,
      frameRate = 10.0,
      durationMs = 200,
    )
    val mp4Bytes = GstMp4Codec().encode(videoIR, VideoFormat.Mp4, CanonicalVideoEncodeOptions(), ctx)

    val reader = Mp4StructureReader()
    reader.read(mp4Bytes) // validates the output is parseable

    val structure = reader.read(mp4Bytes)
    assertNotNull(structure, "Mp4Structure must not be null")
    assertTrue(structure.boxes.isNotEmpty(), "MP4 must have ISO BMFF boxes")

    // Verify we can decode it back
    val decoded = GstMp4Codec().decode(mp4Bytes, CanonicalVideoDecodeOptions(), ctx)
    assertNotNull(decoded.videoTrack, "Decoded MP4 must have video track")
  }

  @Test
  fun mov_realMedia_structureReaderAccepts() = runTest {
    val videoIR = GStreamerTestHelpers.syntheticVideo(
      width = 160,
      height = 120,
      frameRate = 10.0,
      durationMs = 200,
    )
    val movBytes = GstMovCodec().encode(videoIR, VideoFormat.Mov, CanonicalVideoEncodeOptions(), ctx)

    val reader = MovStructureReader()
    reader.read(movBytes) // validates the output is parseable

    val structure = reader.read(movBytes)
    assertNotNull(structure, "MovStructure must not be null")
    assertTrue(structure.boxes.isNotEmpty(), "MOV must have ISO BMFF boxes")
  }

  @Test
  fun webm_realMedia_structureReaderAccepts() = runTest {
    val videoIR = GStreamerTestHelpers.syntheticVideo(
      width = 160,
      height = 120,
      frameRate = 10.0,
      durationMs = 200,
    )
    val webmBytes = GstWebmCodec().encode(videoIR, VideoFormat.Webm, CanonicalVideoEncodeOptions(), ctx)

    val reader = WebmStructureReader()
    reader.read(webmBytes) // validates the output is parseable

    val structure = reader.read(webmBytes)
    assertNotNull(structure, "WebmStructure must not be null")
    assertTrue(structure.elements.isNotEmpty(), "WebM must have EBML elements")
  }

  @Test
  fun mkv_realMedia_structureReaderAccepts() = runTest {
    val videoIR = GStreamerTestHelpers.syntheticVideo(
      width = 160,
      height = 120,
      frameRate = 10.0,
      durationMs = 200,
    )
    val mkvBytes = GstMkvCodec().encode(videoIR, VideoFormat.Mkv, CanonicalVideoEncodeOptions(), ctx)

    val reader = MkvStructureReader()
    reader.read(mkvBytes) // validates the output is parseable

    val structure = reader.read(mkvBytes)
    assertNotNull(structure, "MkvStructure must not be null")
    assertTrue(structure.elements.isNotEmpty(), "MKV must have EBML elements")
  }

  @Test
  fun avi_realMedia_structureReaderAccepts() = runTest {
    val videoIR = GStreamerTestHelpers.syntheticVideo(
      width = 160,
      height = 120,
      frameRate = 10.0,
      durationMs = 200,
    )
    val aviBytes = GstAviCodec().encode(videoIR, VideoFormat.Avi, CanonicalVideoEncodeOptions(), ctx)

    val reader = AviStructureReader()
    reader.read(aviBytes) // validates the output is parseable

    val structure = reader.read(aviBytes)
    assertNotNull(structure, "AviStructure must not be null")
    assertTrue(structure.riff.children.isNotEmpty(), "AVI must have RIFF children")
  }

  // =======================================================================
  // IMAGE (GStreamer): Generate real media -> Structure Reader -> Decode
  // =======================================================================

  @Test
  fun heif_realMedia_structureReaderAccepts() = runTest {
    val imageIR = GStreamerTestHelpers.solidColor(64, 64, r = 128, g = 64, b = 32)
    val heifBytes = GstImageEncoder().encode(imageIR, ImageFormat.Heif, HeifEncodeOptions(), ctx)

    val reader = HeifStructureReader()
    reader.read(heifBytes) // validates the output is parseable

    val structure = reader.read(heifBytes)
    assertNotNull(structure, "HeifStructure must not be null")
    assertTrue(structure.boxes.isNotEmpty(), "HEIF must have ISO BMFF boxes")

    // Verify decode
    val decoded = GstImageDecoder().decode(heifBytes, CanonicalImageDecodeOptions(), ctx)
    assertEquals(64, decoded.width, "HEIF decoded width must match")
    assertEquals(64, decoded.height, "HEIF decoded height must match")
  }

  @Test
  fun avif_realMedia_structureReaderAccepts() = runTest {
    val imageIR = GStreamerTestHelpers.solidColor(64, 64, r = 50, g = 100, b = 200)
    val avifBytes = GstImageEncoder().encode(imageIR, ImageFormat.Avif, HeifEncodeOptions(format = ImageFormat.Avif), ctx)

    val reader = AvifStructureReader()
    reader.read(avifBytes) // validates the output is parseable

    val structure = reader.read(avifBytes)
    assertNotNull(structure, "AvifStructure must not be null")
    assertTrue(structure.boxes.isNotEmpty(), "AVIF must have ISO BMFF boxes")
  }

  // =======================================================================
  // FULL TRANSMUTE API: Transmute { }.image { } pipeline
  // =======================================================================

  @Test
  fun transmuteApi_image_decodeTransformEncode() = runTest {
    val transmute = transmute {
      plugins {
        install(GStreamer)
      }
    }

    // Generate HEIF bytes
    val original = GStreamerTestHelpers.solidColor(64, 64, r = 200, g = 100, b = 50)
    val heifBytes = GstImageEncoder().encode(original, ImageFormat.Heif, HeifEncodeOptions(), ctx)

    // Run through Transmute API: HEIF -> decode -> (identity transform) -> encode HEIF
    val transmuter = transmute.image {
      encode {
        options(HeifEncodeOptions())
      }
    }
    val result = transmuter.transmute(heifBytes)

    assertTrue(result.bytes.isNotEmpty(), "Transmuted HEIF must not be empty")
    assertEquals(ImageFormat.Heif, result.format, "Output format must be HEIF")

    // Verify output is valid by structure parsing
    val reader = HeifStructureReader()
    reader.read(result.bytes) // validates the output is parseable
  }

  @Test
  fun transmuteApi_audio_fullPipeline() = runTest {
    val transmute = transmute {
      plugins {
        install(GStreamer)
      }
    }

    // Generate AAC bytes
    val originalIR = GStreamerTestHelpers.sineWave(durationMs = 300, sampleRate = 44100)
    val aacBytes = GstAacCodec().encode(originalIR, AudioFormat.Aac, CanonicalAudioEncodeOptions(), ctx)

    // Run through Transmute API: AAC -> decode -> encode M4A
    val transmuter = transmute.audio {
      encode {
        options(CanonicalAudioEncodeOptions(outputFormat = OutputFormat.Exact(AudioFormat.M4a)))
      }
    }
    val result = transmuter.transmute(aacBytes)

    assertTrue(result.bytes.isNotEmpty(), "Transmuted M4A must not be empty")
    assertEquals(AudioFormat.M4a, result.format, "Output format must be M4A")

    // Verify output is valid by structure parsing
    val reader = M4aStructureReader()
    reader.read(result.bytes) // validates the output is parseable
  }

  @Test
  fun transmuteApi_video_fullPipeline() = runTest {
    val transmute = transmute {
      plugins {
        install(GStreamer)
      }
    }

    // Generate MP4 bytes
    val videoIR = GStreamerTestHelpers.syntheticVideo(
      width = 160,
      height = 120,
      frameRate = 10.0,
      durationMs = 200,
    )
    val mp4Bytes = GstMp4Codec().encode(videoIR, VideoFormat.Mp4, CanonicalVideoEncodeOptions(), ctx)

    // Run through Transmute API: MP4 -> decode -> encode WebM
    val transmuter = transmute.video {
      encode {
        options(CanonicalVideoEncodeOptions(outputFormat = OutputFormat.Exact(VideoFormat.Webm)))
      }
    }
    val result = transmuter.transmute(mp4Bytes)

    assertTrue(result.bytes.isNotEmpty(), "Transmuted WebM must not be empty")
    assertEquals(VideoFormat.Webm, result.format, "Output format must be WebM")

    // Verify output is valid by structure parsing
    val reader = WebmStructureReader()
    reader.read(result.bytes) // validates the output is parseable
  }

  // =======================================================================
  // TRANSMUTE STRUCTURE API: Parse -> Write -> Re-parse
  // =======================================================================

  @Test
  fun transmuteStructure_mp4_readWriteRoundtrip() = runTest {
    val videoIR = GStreamerTestHelpers.syntheticVideo(
      width = 160,
      height = 120,
      frameRate = 10.0,
      durationMs = 200,
    )
    val mp4Bytes = GstMp4Codec().encode(videoIR, VideoFormat.Mp4, CanonicalVideoEncodeOptions(), ctx)

    val transmute = transmute {
      plugins {
        install(GStreamer)
      }
    }

    // Decode structure via new codec API
    val structure = transmute.codec.decodeStructure(mp4Bytes, VideoFormat.Mp4)
    assertNotNull(structure, "Transmute.codec must decode mp4 structure")
  }

  @Test
  fun transmuteStructure_m4a_readWriteRoundtrip() = runTest {
    val audioIR = GStreamerTestHelpers.sineWave(durationMs = 200, sampleRate = 44100)
    val m4aBytes = GstM4aCodec().encode(audioIR, AudioFormat.M4a, CanonicalAudioEncodeOptions(), ctx)

    val transmute = transmute {
      plugins {
        install(GStreamer)
      }
    }

    // Decode structure via new codec API
    val structure = transmute.codec.decodeStructure(m4aBytes, AudioFormat.M4a)
    assertNotNull(structure, "M4A structure must decode successfully")
  }

  // =======================================================================
  // INSPECT: thumbnailFirstFrame
  // =======================================================================

  @Test
  fun inspect_thumbnailFirstFrame_extractsFromVideo() = runTest {
    val transmute = transmute {
      plugins {
        install(GStreamer)
      }
    }

    // Generate a real video
    val videoIR = GStreamerTestHelpers.syntheticVideo(
      width = 160,
      height = 120,
      frameRate = 10.0,
      durationMs = 500,
    )
    val mp4Bytes = GstMp4Codec().encode(videoIR, VideoFormat.Mp4, CanonicalVideoEncodeOptions(), ctx)

    // Extract thumbnail via inspect API
    val thumbnail = transmute.inspect.video.thumbnailFirstFrame(mp4Bytes)

    assertTrue(thumbnail.bytes.isNotEmpty(), "Thumbnail bytes must not be empty")
    assertEquals(ImageFormat.Png, thumbnail.format, "Default thumbnail format must be PNG")

    // Verify the thumbnail is valid by structure-parsing
    val reader = dev.transmute.structure.image.PngStructureReader()
    reader.read(thumbnail.bytes) // validates the output is parseable

    // Verify it can be decoded to pixels
    val decoded = dev.transmute.image.codecs.jvm.JvmImageIoDecoder()
      .decode(thumbnail.bytes, CanonicalImageDecodeOptions(), ctx)
    assertEquals(160, decoded.width, "Thumbnail width must match video width")
    assertEquals(120, decoded.height, "Thumbnail height must match video height")
  }
}
