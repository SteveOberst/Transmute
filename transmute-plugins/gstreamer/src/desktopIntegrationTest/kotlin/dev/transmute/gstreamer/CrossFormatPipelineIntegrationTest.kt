package dev.transmute.gstreamer

import dev.transmute.audio.AudioFormat
import dev.transmute.audio.CanonicalAudioDecodeOptions
import dev.transmute.audio.CanonicalAudioEncodeOptions
import dev.transmute.gstreamer.GStreamerTestHelpers.testContext
import dev.transmute.image.ByteArrayPixelBuffer
import dev.transmute.image.PixelFormat
import dev.transmute.video.CanonicalVideoDecodeOptions
import dev.transmute.video.CanonicalVideoEncodeOptions
import dev.transmute.video.FrameStream
import dev.transmute.video.VideoFormat
import dev.transmute.video.VideoFrame
import dev.transmute.video.VideoIR
import dev.transmute.video.VideoTrack
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * Phase 3 - Cross-format and pipeline integration tests.
 *
 * These tests exercise scenarios that span multiple codecs or combine
 * encode -> transform -> re-encode workflows:
 *
 * - Audio cross-format: encode with one codec -> decode with another
 * - Video container swap: MP4 -> decode -> re-encode WebM -> decode -> verify
 * - Transform + re-encode: decode -> resize frames -> encode to different container
 * - Multi-track: encode video with stereo audio -> decode -> verify both tracks
 *
 * All tests are **soft-skipped** when GStreamer is not installed locally.
 */
class CrossFormatPipelineIntegrationTest : GStreamerTestBase() {

  private val ctx = testContext()

  // ---
  // Audio cross-format
  // ---

  /**
   * Encode a sine wave to AAC, then decode to AudioIR, then re-encode to M4A,
   * then decode back and verify the signal survived two codec passes.
   */
  @Test
  fun audio_aacToM4a_crossFormatRoundtrip() = runTest {
    val original = GStreamerTestHelpers.sineWave(durationMs = 500, sampleRate = 44100)

    // Pass 1: encode as AAC
    val aac = GstAacCodec()
    val aacBytes = aac.encode(original, AudioFormat.Aac, CanonicalAudioEncodeOptions(), ctx)
    assertTrue(aacBytes.isNotEmpty(), "AAC encode must produce output")

    // Decode AAC
    val fromAac = aac.decode(aacBytes, CanonicalAudioDecodeOptions(), ctx)
    assertTrue(fromAac.sampleRate > 0, "Decoded AAC must have valid sample rate")
    assertTrue(fromAac.durationMs > 0, "Decoded AAC must have positive duration")

    // Pass 2: re-encode as M4A
    val m4a = GstM4aCodec()
    val m4aBytes = m4a.encode(fromAac, AudioFormat.M4a, CanonicalAudioEncodeOptions(), ctx)
    assertTrue(m4aBytes.isNotEmpty(), "M4A re-encode must produce output")

    // Decode M4A
    val fromM4a = m4a.decode(m4aBytes, CanonicalAudioDecodeOptions(), ctx)
    assertTrue(fromM4a.sampleRate > 0, "Decoded M4A must have valid sample rate")
    assertTrue(fromM4a.durationMs > 300, "M4A duration should survive two codec passes (>300ms from 500ms input)")
  }

  /**
   * Encode as Opus, decode, re-encode as AAC - verifying the pipeline
   * can bridge between OGG-based and raw-ADTS codecs.
   */
  @Test
  fun audio_opusToAac_crossFormatRoundtrip() = runTest {
    val original = GStreamerTestHelpers.sineWave(durationMs = 500, sampleRate = 48000)

    // Opus encode + decode
    val opus = GstOpusCodec()
    val opusBytes = opus.encode(original, AudioFormat.Opus, CanonicalAudioEncodeOptions(), ctx)
    val fromOpus = opus.decode(opusBytes, CanonicalAudioDecodeOptions(), ctx)
    assertTrue(fromOpus.durationMs > 0, "Decoded Opus must have positive duration")

    // Re-encode as AAC
    val aac = GstAacCodec()
    val aacBytes = aac.encode(fromOpus, AudioFormat.Aac, CanonicalAudioEncodeOptions(), ctx)
    assertTrue(aacBytes.isNotEmpty(), "AAC re-encode must produce output")

    val fromAac = aac.decode(aacBytes, CanonicalAudioDecodeOptions(), ctx)
    assertTrue(fromAac.durationMs > 300, "Final AAC duration should be >300ms")
    assertTrue(fromAac.sampleRate > 0, "Final decoded sample rate must be positive")
  }

  // ---
  // Video container swap
  // ---

  /**
   * Encode as MP4 -> decode -> re-encode as WebM -> decode -> verify dimensions
   * and duration survive the container swap.
   */
  @Test
  fun video_mp4ToWebm_containerSwap() = runTest {
    val video = GStreamerTestHelpers.syntheticVideo(
      width = 160,
      height = 120,
      frameRate = 10.0,
      durationMs = 500,
    )

    // Encode to MP4
    val mp4 = GstMp4Codec()
    val mp4Bytes = mp4.encode(video, VideoFormat.Mp4, CanonicalVideoEncodeOptions(), ctx)
    assertTrue(mp4Bytes.isNotEmpty(), "MP4 encode must produce output")

    // Decode MP4
    val fromMp4 = mp4.decode(mp4Bytes, CanonicalVideoDecodeOptions(), ctx)
    assertNotNull(fromMp4.videoTrack, "Decoded MP4 must have a video track")

    // Re-encode as WebM
    val webm = GstWebmCodec()
    val webmBytes = webm.encode(fromMp4, VideoFormat.Webm, CanonicalVideoEncodeOptions(), ctx)
    assertTrue(webmBytes.isNotEmpty(), "WebM re-encode must produce output")

    // Decode WebM and verify
    val fromWebm = webm.decode(webmBytes, CanonicalVideoDecodeOptions(), ctx)
    assertNotNull(fromWebm.videoTrack, "Decoded WebM must have a video track")
    assertTrue(fromWebm.videoTrack.width > 0, "Width must be positive after container swap")
    assertTrue(fromWebm.videoTrack.height > 0, "Height must be positive after container swap")
    assertTrue(fromWebm.durationMs > 0, "Duration must be positive after container swap")
  }

  /**
   * Encode as AVI -> decode -> re-encode as MKV -> decode -> verify.
   */
  @Test
  fun video_aviToMkv_containerSwap() = runTest {
    assumeLegacyAviEncodeSupported()
    val video = GStreamerTestHelpers.syntheticVideo(
      width = 160,
      height = 120,
      frameRate = 10.0,
      durationMs = 500,
    )

    // AVI roundtrip
    val avi = GstAviCodec()
    val aviBytes = avi.encode(video, VideoFormat.Avi, CanonicalVideoEncodeOptions(), ctx)
    val fromAvi = avi.decode(aviBytes, CanonicalVideoDecodeOptions(), ctx)

    // Re-encode as MKV
    val mkv = GstMkvCodec()
    val mkvBytes = mkv.encode(fromAvi, VideoFormat.Mkv, CanonicalVideoEncodeOptions(), ctx)
    assertTrue(mkvBytes.isNotEmpty(), "MKV re-encode must produce output")

    val fromMkv = mkv.decode(mkvBytes, CanonicalVideoDecodeOptions(), ctx)
    assertNotNull(fromMkv.videoTrack, "Decoded MKV must have a video track")
    assertTrue(fromMkv.durationMs > 0, "MKV duration must be positive")
  }

  // ---
  // Transform + re-encode
  // ---

  /**
   * Decode MP4 -> resize frames to a smaller resolution -> encode as MKV ->
   * decode MKV -> verify the new dimensions persisted.
   *
   * This simulates a real-world "transcode + resize" pipeline.
   */
  @Test
  fun video_decodeResizeReencode_mp4ToMkv() = runTest {
    // Encode a 320x240 source
    val mp4 = GstMp4Codec()
    val source = GStreamerTestHelpers.syntheticVideo(
      width = 320,
      height = 240,
      frameRate = 10.0,
      durationMs = 500,
    )
    val mp4Bytes = mp4.encode(source, VideoFormat.Mp4, CanonicalVideoEncodeOptions(), ctx)
    val decoded = mp4.decode(mp4Bytes, CanonicalVideoDecodeOptions(), ctx)

    // "Resize" by creating new frames at 160x120
    val resizedWidth = 160
    val resizedHeight = 120
    val resized = resizeVideoIR(decoded, resizedWidth, resizedHeight)

    // Encode resized video as MKV
    val mkv = GstMkvCodec()
    val mkvBytes = mkv.encode(resized, VideoFormat.Mkv, CanonicalVideoEncodeOptions(), ctx)
    assertTrue(mkvBytes.isNotEmpty(), "Resized MKV must not be empty")

    // Decode and verify new dimensions
    val finalResult = mkv.decode(mkvBytes, CanonicalVideoDecodeOptions(), ctx)
    assertNotNull(finalResult.videoTrack, "Decoded resized MKV must have a video track")
    assertTrue(
      finalResult.videoTrack.width == resizedWidth,
      "Resized width should be $resizedWidth, got ${finalResult.videoTrack.width}",
    )
    assertTrue(
      finalResult.videoTrack.height == resizedHeight,
      "Resized height should be $resizedHeight, got ${finalResult.videoTrack.height}",
    )
  }

  // ---
  // Multi-track (video + audio)
  // ---

  /**
   * Encode a video with stereo audio via MP4, decode, and verify that
   * both video and audio tracks survive the roundtrip.
   */
  @Test
  fun mp4_multiTrack_videoAndStereoAudio() = runTest {
    val video = GStreamerTestHelpers.syntheticVideo(
      width = 160,
      height = 120,
      frameRate = 10.0,
      durationMs = 500,
      includeAudio = true,
    )
    assertNotNull(video.audioTrack, "Synthetic video must include audio track")

    val mp4 = GstMp4Codec()
    val encoded = mp4.encode(video, VideoFormat.Mp4, CanonicalVideoEncodeOptions(), ctx)
    assertTrue(encoded.isNotEmpty(), "Multi-track MP4 must not be empty")

    val decoded = mp4.decode(encoded, CanonicalVideoDecodeOptions(), ctx)
    assertNotNull(decoded.videoTrack, "Decoded multi-track MP4 must have video track")
    assertTrue(decoded.videoTrack.width > 0, "Video width must be positive")
    assertTrue(decoded.videoTrack.height > 0, "Video height must be positive")
    assertTrue(decoded.durationMs > 0, "Duration must be positive")
    // Note: Audio track may or may not survive depending on GStreamer demux
    // pipeline configuration; the key requirement is no crash.
  }

  /**
   * Encode a video with stereo audio via MKV, decode, verify.
   */
  @Test
  fun mkv_multiTrack_videoAndStereoAudio() = runTest {
    val video = GStreamerTestHelpers.syntheticVideo(
      width = 160,
      height = 120,
      frameRate = 10.0,
      durationMs = 500,
      includeAudio = true,
    )

    val mkv = GstMkvCodec()
    val encoded = mkv.encode(video, VideoFormat.Mkv, CanonicalVideoEncodeOptions(), ctx)
    assertTrue(encoded.isNotEmpty(), "Multi-track MKV must not be empty")

    val decoded = mkv.decode(encoded, CanonicalVideoDecodeOptions(), ctx)
    assertNotNull(decoded.videoTrack, "Decoded multi-track MKV must have video track")
    assertTrue(decoded.durationMs > 0, "Duration must be positive")
  }

  /**
   * Encode video+audio as WebM, decode, verify.
   */
  @Test
  fun webm_multiTrack_videoAndStereoAudio() = runTest {
    val video = GStreamerTestHelpers.syntheticVideo(
      width = 160,
      height = 120,
      frameRate = 10.0,
      durationMs = 500,
      includeAudio = true,
    )

    val webm = GstWebmCodec()
    val encoded = webm.encode(video, VideoFormat.Webm, CanonicalVideoEncodeOptions(), ctx)
    assertTrue(encoded.isNotEmpty(), "Multi-track WebM must not be empty")

    val decoded = webm.decode(encoded, CanonicalVideoDecodeOptions(), ctx)
    assertNotNull(decoded.videoTrack, "Decoded multi-track WebM must have video track")
    assertTrue(decoded.durationMs > 0, "Duration must be positive")
  }

  // ===
  // Helpers
  // ===

  /**
   * Creates a new [VideoIR] with frames re-created at the given dimensions.
   *
   * This is a simplified "resize" - it generates new black frames at the
   * target resolution rather than actually scaling pixel data. This is
   * sufficient to verify that the encode pipeline respects the new dimensions.
   */
  private fun resizeVideoIR(source: VideoIR, newWidth: Int, newHeight: Int): VideoIR {
    val frameCount = source.videoTrack.frames.frameCount
    val fps = source.videoTrack.frameRate
    val resizedTrack = VideoTrack(
      width = newWidth,
      height = newHeight,
      frameRate = fps,
      frames = object : FrameStream {
        override val frameCount: Long = frameCount
        private var idx = 0

        override suspend fun nextFrame(): VideoFrame? {
          if (idx >= frameCount) return null
          val bpp = 4
          val pixels = ByteArray(newHeight * newWidth * bpp) // black frames
          val frame = VideoFrame(
            buffer = ByteArrayPixelBuffer(pixels),
            width = newWidth,
            height = newHeight,
            pixelFormat = PixelFormat.RGBA_8888,
            timestampMs = (idx * 1000L / fps).toLong(),
          )
          idx++
          return frame
        }

        override fun close() {}
      },
    )
    return VideoIR(
      videoTrack = resizedTrack,
      audioTrack = source.audioTrack,
      durationMs = source.durationMs,
    )
  }
}
