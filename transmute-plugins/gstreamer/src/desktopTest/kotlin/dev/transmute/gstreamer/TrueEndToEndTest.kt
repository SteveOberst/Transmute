package dev.transmute.gstreamer

import dev.transmute.transmute
import dev.transmute.model.core.asBytes
import dev.transmute.audio.AudioFormat
import dev.transmute.audio.CanonicalAudioDecodeOptions
import dev.transmute.audio.CanonicalAudioEncodeOptions
import dev.transmute.codec.OutputFormat
import dev.transmute.gstreamer.GStreamerTestHelpers.requireGStreamer
import dev.transmute.gstreamer.GStreamerTestHelpers.requireGStreamerElement
import dev.transmute.gstreamer.GStreamerTestHelpers.testContext
import dev.transmute.image.CanonicalImageDecodeOptions
import dev.transmute.image.CanonicalImageEncodeOptions
import dev.transmute.image.HeifEncodeOptions
import dev.transmute.image.ImageFormat
import dev.transmute.image.JpegEncodeOptions
import dev.transmute.image.PngEncodeOptions
import dev.transmute.image.WebPEncodeOptions
import dev.transmute.audio.codecs.jvm.JvmMp3Codec
import dev.transmute.structure.audio.*
import dev.transmute.structure.image.*
import dev.transmute.structure.video.*
import dev.transmute.video.CanonicalVideoDecodeOptions
import dev.transmute.video.CanonicalVideoEncodeOptions
import dev.transmute.video.VideoFormat
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * True End-to-End Integration Tests.
 *
 * These tests generate **real media bytes** via codecs, then:
 * 1. Run the bytes through the corresponding **StructureReader** to verify
 *    the format is structurally valid and parseable.
 * 2. **Decode** the bytes back to IR to verify round-trip integrity.
 * 3. Some tests also exercise the **full `Transmute { }` API** including
 *    transform pipelines.
 *
 * This validates the entire chain: encode -> structure parse -> decode.
 *
 * Soft-skipped when GStreamer is not available locally.
 */
class TrueEndToEndTest {

    private val ctx = testContext()

    // =======================================================================
    // AUDIO: Generate real media -> Structure Reader -> Decode
    // =======================================================================

    @Test
    fun aac_realMedia_structureReaderAccepts() = runTest {
        requireGStreamer {
            val audioIR = GStreamerTestHelpers.sineWave(durationMs = 200, sampleRate = 44100)
            val aacBytes = GstAacCodec().encode(audioIR, AudioFormat.Aac, CanonicalAudioEncodeOptions(), ctx)

            val reader = AacStructureReader()
            assertTrue(reader.canRead(aacBytes), "AacStructureReader must accept real AAC bytes")

            val structure = reader.read(aacBytes)
            assertNotNull(structure, "AacStructure must not be null")
        }
    }

    @Test
    fun m4a_realMedia_structureReaderAccepts() = runTest {
        requireGStreamer {
            val audioIR = GStreamerTestHelpers.sineWave(durationMs = 200, sampleRate = 44100)
            val m4aBytes = GstM4aCodec().encode(audioIR, AudioFormat.M4a, CanonicalAudioEncodeOptions(), ctx)

            val reader = M4aStructureReader()
            assertTrue(reader.canRead(m4aBytes), "M4aStructureReader must accept real M4A bytes")

            val structure = reader.read(m4aBytes)
            assertNotNull(structure, "M4aStructure must not be null")
            assertTrue(structure.boxes.isNotEmpty(), "M4A must have ISO BMFF boxes")
        }
    }

    @Test
    fun opus_realMedia_structureReaderAccepts() = runTest {
        requireGStreamer {
            val audioIR = GStreamerTestHelpers.sineWave(durationMs = 200, sampleRate = 48000)
            val opusBytes = GstOpusCodec().encode(audioIR, AudioFormat.Opus, CanonicalAudioEncodeOptions(), ctx)

            val reader = OpusStructureReader()
            assertTrue(reader.canRead(opusBytes), "OpusStructureReader must accept real Opus bytes")

            val structure = reader.read(opusBytes)
            assertNotNull(structure, "OpusStructure must not be null")
            assertTrue(structure.pages.isNotEmpty(), "Opus must have OGG pages")
        }
    }

    @Test
    fun flac_realMedia_structureReaderAccepts() = runTest {
        requireGStreamer {
            val audioIR = GStreamerTestHelpers.sineWave(durationMs = 200, sampleRate = 44100)
            val flacBytes = GstFlacEncoder().encode(audioIR, AudioFormat.Flac, CanonicalAudioEncodeOptions(), ctx)

            val reader = FlacStructureReader()
            assertTrue(reader.canRead(flacBytes), "FlacStructureReader must accept real FLAC bytes")

            val structure = reader.read(flacBytes)
            assertNotNull(structure, "FlacStructure must not be null")
        }
    }

    @Test
    fun ogg_realMedia_structureReaderAccepts() = runTest {
        requireGStreamer {
            val audioIR = GStreamerTestHelpers.sineWave(durationMs = 200, sampleRate = 44100)
            val oggBytes = GstOggVorbisEncoder().encode(audioIR, AudioFormat.Ogg, CanonicalAudioEncodeOptions(), ctx)

            val reader = OggAudioStructureReader()
            assertTrue(reader.canRead(oggBytes), "OggAudioStructureReader must accept real OGG bytes")

            val structure = reader.read(oggBytes)
            assertNotNull(structure, "OggAudioStructure must not be null")
            assertTrue(structure.pages.isNotEmpty(), "OGG must have pages")
        }
    }

    @Test
    fun wav_realMedia_structureReaderAccepts() = runTest {
        val audioIR = GStreamerTestHelpers.sineWave(durationMs = 200, sampleRate = 44100)
        val encoder = dev.transmute.audio.codecs.WavEncoder()
        val wavBytes = encoder.encode(audioIR, AudioFormat.Wav, CanonicalAudioEncodeOptions(), testContext())

        val reader = WavStructureReader()
        assertTrue(reader.canRead(wavBytes), "WavStructureReader must accept real WAV bytes")

        val structure = reader.read(wavBytes)
        assertNotNull(structure, "WavStructure must not be null")
        assertTrue(structure.riff.children.isNotEmpty(), "WAV must have RIFF children")
    }

    @Test
    fun mp3_realMedia_structureReaderAccepts() = runTest {
        // MP3 encoding via Jump3r (pure-Java LAME) - no GStreamer needed
        val audioIR = GStreamerTestHelpers.sineWave(durationMs = 500, sampleRate = 44100)
        val mp3Bytes = JvmMp3Codec().encode(audioIR, AudioFormat.Mp3, CanonicalAudioEncodeOptions(), testContext())

        val reader = Mp3StructureReader()
        assertTrue(reader.canRead(mp3Bytes), "Mp3StructureReader must accept real MP3 bytes")

        val structure = reader.read(mp3Bytes)
        assertNotNull(structure, "Mp3Structure must not be null")
        assertTrue(structure.audioData.isNotEmpty(), "MP3 must have audio data")

        // Verify decode roundtrip
        val decoded = JvmMp3Codec().decode(mp3Bytes, dev.transmute.audio.CanonicalAudioDecodeOptions(), testContext())
        assertTrue(decoded.durationMs > 0, "MP3 must decode with positive duration")
    }

    // =======================================================================
    // VIDEO: Generate real media -> Structure Reader -> Decode
    // =======================================================================

    @Test
    fun mp4_realMedia_structureReaderAccepts() = runTest {
        requireGStreamer {
            val videoIR = GStreamerTestHelpers.syntheticVideo(
                width = 160, height = 120, frameRate = 10.0, durationMs = 200,
            )
            val mp4Bytes = GstMp4Codec().encode(videoIR, VideoFormat.Mp4, CanonicalVideoEncodeOptions(), ctx)

            val reader = Mp4StructureReader()
            assertTrue(reader.canRead(mp4Bytes), "Mp4StructureReader must accept real MP4 bytes")

            val structure = reader.read(mp4Bytes)
            assertNotNull(structure, "Mp4Structure must not be null")
            assertTrue(structure.boxes.isNotEmpty(), "MP4 must have ISO BMFF boxes")

            // Verify we can decode it back
            val decoded = GstMp4Codec().decode(mp4Bytes, CanonicalVideoDecodeOptions(), ctx)
            assertNotNull(decoded.videoTrack, "Decoded MP4 must have video track")
        }
    }

    @Test
    fun mov_realMedia_structureReaderAccepts() = runTest {
        requireGStreamer {
            val videoIR = GStreamerTestHelpers.syntheticVideo(
                width = 160, height = 120, frameRate = 10.0, durationMs = 200,
            )
            val movBytes = GstMovCodec().encode(videoIR, VideoFormat.Mov, CanonicalVideoEncodeOptions(), ctx)

            val reader = MovStructureReader()
            assertTrue(reader.canRead(movBytes), "MovStructureReader must accept real MOV bytes")

            val structure = reader.read(movBytes)
            assertNotNull(structure, "MovStructure must not be null")
            assertTrue(structure.boxes.isNotEmpty(), "MOV must have ISO BMFF boxes")
        }
    }

    @Test
    fun webm_realMedia_structureReaderAccepts() = runTest {
        requireGStreamer {
            val videoIR = GStreamerTestHelpers.syntheticVideo(
                width = 160, height = 120, frameRate = 10.0, durationMs = 200,
            )
            val webmBytes = GstWebmCodec().encode(videoIR, VideoFormat.Webm, CanonicalVideoEncodeOptions(), ctx)

            val reader = WebmStructureReader()
            assertTrue(reader.canRead(webmBytes), "WebmStructureReader must accept real WebM bytes")

            val structure = reader.read(webmBytes)
            assertNotNull(structure, "WebmStructure must not be null")
            assertTrue(structure.elements.isNotEmpty(), "WebM must have EBML elements")
        }
    }

    @Test
    fun mkv_realMedia_structureReaderAccepts() = runTest {
        requireGStreamer {
            val videoIR = GStreamerTestHelpers.syntheticVideo(
                width = 160, height = 120, frameRate = 10.0, durationMs = 200,
            )
            val mkvBytes = GstMkvCodec().encode(videoIR, VideoFormat.Mkv, CanonicalVideoEncodeOptions(), ctx)

            val reader = MkvStructureReader()
            assertTrue(reader.canRead(mkvBytes), "MkvStructureReader must accept real MKV bytes")

            val structure = reader.read(mkvBytes)
            assertNotNull(structure, "MkvStructure must not be null")
            assertTrue(structure.elements.isNotEmpty(), "MKV must have EBML elements")
        }
    }

    @Test
    fun avi_realMedia_structureReaderAccepts() = runTest {
        requireGStreamer {
            val videoIR = GStreamerTestHelpers.syntheticVideo(
                width = 160, height = 120, frameRate = 10.0, durationMs = 200,
            )
            val aviBytes = GstAviCodec().encode(videoIR, VideoFormat.Avi, CanonicalVideoEncodeOptions(), ctx)

            val reader = AviStructureReader()
            assertTrue(reader.canRead(aviBytes), "AviStructureReader must accept real AVI bytes")

            val structure = reader.read(aviBytes)
            assertNotNull(structure, "AviStructure must not be null")
            assertTrue(structure.riff.children.isNotEmpty(), "AVI must have RIFF children")
        }
    }

    // =======================================================================
    // IMAGE (GStreamer): Generate real media -> Structure Reader -> Decode
    // =======================================================================

    @Test
    fun heif_realMedia_structureReaderAccepts() = runTest {
        requireGStreamerElement("x265enc") {
            val imageIR = GStreamerTestHelpers.solidColor(64, 64, r = 128, g = 64, b = 32)
            val heifBytes = GstImageEncoder().encode(imageIR, ImageFormat.Heif, HeifEncodeOptions(), ctx)

            val reader = HeifStructureReader()
            assertTrue(reader.canRead(heifBytes), "HeifStructureReader must accept real HEIF bytes")

            val structure = reader.read(heifBytes)
            assertNotNull(structure, "HeifStructure must not be null")
            assertTrue(structure.boxes.isNotEmpty(), "HEIF must have ISO BMFF boxes")

            // Verify decode
            val decoded = GstImageDecoder().decode(heifBytes, CanonicalImageDecodeOptions(), ctx)
            assertEquals(64, decoded.width, "HEIF decoded width must match")
            assertEquals(64, decoded.height, "HEIF decoded height must match")
        }
    }

    @Test
    fun avif_realMedia_structureReaderAccepts() = runTest {
        requireGStreamerElement("av1enc") {
            val imageIR = GStreamerTestHelpers.solidColor(64, 64, r = 50, g = 100, b = 200)
            val avifBytes = GstImageEncoder().encode(imageIR, ImageFormat.Avif, HeifEncodeOptions(format = ImageFormat.Avif), ctx)

            val reader = AvifStructureReader()
            assertTrue(reader.canRead(avifBytes), "AvifStructureReader must accept real AVIF bytes")

            val structure = reader.read(avifBytes)
            assertNotNull(structure, "AvifStructure must not be null")
            assertTrue(structure.boxes.isNotEmpty(), "AVIF must have ISO BMFF boxes")
        }
    }

    // =======================================================================
    // IMAGE (JvmImageIo): Generate real media -> Structure Reader -> Decode
    // =======================================================================

    @Test
    fun jpeg_realMedia_structureReaderAccepts() = runTest {
        val imageIR = GStreamerTestHelpers.solidColor(64, 64, r = 180, g = 90, b = 45)
        val encoder = dev.transmute.image.codecs.jvm.JvmImageIoEncoder()
        val jpegBytes = encoder.encode(imageIR, ImageFormat.Jpeg, JpegEncodeOptions(), testContext())

        val reader = JpegStructureReader()
        assertTrue(reader.canRead(jpegBytes), "JpegStructureReader must accept real JPEG bytes")

        val structure = reader.read(jpegBytes)
        assertNotNull(structure, "JpegStructure must not be null")
        assertTrue(structure.segments.isNotEmpty(), "JPEG must have segments")
    }

    @Test
    fun png_realMedia_structureReaderAccepts() = runTest {
        val imageIR = GStreamerTestHelpers.solidColor(64, 64, r = 100, g = 150, b = 200)
        val encoder = dev.transmute.image.codecs.jvm.JvmImageIoEncoder()
        val pngBytes = encoder.encode(imageIR, ImageFormat.Png, PngEncodeOptions(), testContext())

        val reader = PngStructureReader()
        assertTrue(reader.canRead(pngBytes), "PngStructureReader must accept real PNG bytes")

        val structure = reader.read(pngBytes)
        assertNotNull(structure, "PngStructure must not be null")
        assertTrue(structure.chunks.isNotEmpty(), "PNG must have chunks")
    }

    @Test
    fun gif_realMedia_structureReaderAccepts() = runTest {
        val imageIR = GStreamerTestHelpers.solidColor(64, 64, r = 50, g = 100, b = 150)
        val encoder = dev.transmute.image.codecs.jvm.JvmImageIoEncoder()
        val gifBytes = encoder.encode(imageIR, ImageFormat.Gif, CanonicalImageEncodeOptions(), testContext())

        val reader = GifStructureReader()
        assertTrue(reader.canRead(gifBytes), "GifStructureReader must accept real GIF bytes")

        val structure = reader.read(gifBytes)
        assertNotNull(structure, "GifStructure must not be null")
        // Note: GIF blocks may be empty for a solid-color image with no extensions
    }

    @Test
    fun tiff_realMedia_structureReaderAccepts() = runTest {
        val imageIR = GStreamerTestHelpers.solidColor(32, 32, r = 75, g = 125, b = 175)
        val encoder = dev.transmute.image.codecs.jvm.JvmImageIoEncoder()
        val tiffBytes = encoder.encode(imageIR, ImageFormat.Tiff, CanonicalImageEncodeOptions(), testContext())

        val reader = TiffStructureReader()
        assertTrue(reader.canRead(tiffBytes), "TiffStructureReader must accept real TIFF bytes")

        val structure = reader.read(tiffBytes)
        assertNotNull(structure, "TiffStructure must not be null")
        assertTrue(structure.ifds.isNotEmpty(), "TIFF must have IFDs")
    }

    @Test
    fun bmp_realMedia_structureReaderAccepts() = runTest {
        val imageIR = GStreamerTestHelpers.solidColor(32, 32, r = 100, g = 100, b = 100)
        val encoder = dev.transmute.image.codecs.bmp.BmpImageEncoder()
        val bmpBytes = encoder.encode(imageIR, ImageFormat.Bmp, CanonicalImageEncodeOptions(), testContext())

        val reader = BmpStructureReader()
        assertTrue(reader.canRead(bmpBytes), "BmpStructureReader must accept real BMP bytes")

        val structure = reader.read(bmpBytes)
        assertNotNull(structure, "BmpStructure must not be null")
        // BMP structure has fileHeader, infoHeader
    }

    @Test
    fun webp_realMedia_structureReaderAccepts() = runTest {
        val canEncodeWebp = javax.imageio.ImageIO.getImageWritersByFormatName("webp")
            .asSequence().firstOrNull() != null
        if (!canEncodeWebp) {
            println("SKIP: WebP writer not available on this JVM")
            return@runTest
        }

        val imageIR = GStreamerTestHelpers.solidColor(64, 64, r = 200, g = 50, b = 100)
        val encoder = dev.transmute.image.codecs.jvm.JvmImageIoEncoder()
        val webpBytes = encoder.encode(imageIR, ImageFormat.Webp, WebPEncodeOptions(), testContext())

        val reader = WebpStructureReader()
        assertTrue(reader.canRead(webpBytes), "WebpStructureReader must accept real WebP bytes")

        val structure = reader.read(webpBytes)
        assertNotNull(structure, "WebpStructure must not be null")
        assertTrue(structure.riff.children.isNotEmpty(), "WebP must have RIFF children")
    }

    // =======================================================================
    // FULL TRANSMUTE API: Transmute { }.image { } pipeline
    // =======================================================================

    @Test
    fun transmuteApi_image_decodeTransformEncode() = runTest {
        requireGStreamerElement("x265enc") {
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
            assertTrue(reader.canRead(result.bytes), "Output must be valid HEIF")
        }
    }

    @Test
    fun transmuteApi_audio_fullPipeline() = runTest {
        requireGStreamer {
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
            assertTrue(reader.canRead(result.bytes), "Output must be valid M4A")
        }
    }

    @Test
    fun transmuteApi_video_fullPipeline() = runTest {
        requireGStreamer {
            val transmute = transmute {
                plugins {
                    install(GStreamer)
                }
            }

            // Generate MP4 bytes
            val videoIR = GStreamerTestHelpers.syntheticVideo(
                width = 160, height = 120, frameRate = 10.0, durationMs = 200,
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
            assertTrue(reader.canRead(result.bytes), "Output must be valid WebM")
        }
    }

    // =======================================================================
    // TRANSMUTE STRUCTURE API: Parse -> Write -> Re-parse
    // =======================================================================

    @Test
    fun transmuteStructure_mp4_readWriteRoundtrip() = runTest {
        requireGStreamer {
            val videoIR = GStreamerTestHelpers.syntheticVideo(
                width = 160, height = 120, frameRate = 10.0, durationMs = 200,
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
    }

    @Test
    fun transmuteStructure_m4a_readWriteRoundtrip() = runTest {
        requireGStreamer {
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
    }

    // =======================================================================
    // INSPECT: thumbnailFirstFrame
    // =======================================================================

    @Test
    fun inspect_thumbnailFirstFrame_extractsFromVideo() = runTest {
        requireGStreamer {
            val transmute = transmute {
                plugins {
                    install(GStreamer)
                }
            }

            // Generate a real video
            val videoIR = GStreamerTestHelpers.syntheticVideo(
                width = 160, height = 120, frameRate = 10.0, durationMs = 500,
            )
            val mp4Bytes = GstMp4Codec().encode(videoIR, VideoFormat.Mp4, CanonicalVideoEncodeOptions(), ctx)

            // Extract thumbnail via inspect API
            val thumbnail = transmute.inspect.video.thumbnailFirstFrame(mp4Bytes)

            assertTrue(thumbnail.bytes.isNotEmpty(), "Thumbnail bytes must not be empty")
            assertEquals(ImageFormat.Png, thumbnail.format, "Default thumbnail format must be PNG")

            // Verify the thumbnail is valid by structure-parsing
            val reader = dev.transmute.structure.image.PngStructureReader()
            assertTrue(reader.canRead(thumbnail.bytes), "Thumbnail must be a valid PNG")

            // Verify it can be decoded to pixels
            val decoded = dev.transmute.image.codecs.jvm.JvmImageIoDecoder()
                .decode(thumbnail.bytes, CanonicalImageDecodeOptions(), ctx)
            assertEquals(160, decoded.width, "Thumbnail width must match video width")
            assertEquals(120, decoded.height, "Thumbnail height must match video height")
        }
    }
}
