package dev.transmute.video

import dev.transmute.core.MetadataPolicy
import dev.transmute.core.pipeline.TransformId
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class VideoMetadataTransformTest {

    private fun videoWithMetadata(): VideoIR {
        val ir = VideoTestHelpers.syntheticVideo(
            width = 16,
            height = 16,
            frameRate = 30.0,
            durationMs = 66,
        )
        return ir.copy(
            metadata = VideoMetadata(
                title = "Test Video",
                artist = "Test Director",
                durationMs = 66,
                bitrateKbps = 5000,
                appMetadata = mapOf("encoder" to "transmute", "profile" to "main"),
            ),
        )
    }

    @Test
    fun preservePolicyKeepsAllMetadata() = runTest {
        val input = videoWithMetadata()
        val transform = VideoMetadataTransform(MetadataPolicy.PRESERVE)
        val result = transform.apply(input, VideoTestHelpers.testContext())
        assertSame(input, result, "PRESERVE should return the same instance")
    }

    @Test
    fun stripAllRemovesTitle() = runTest {
        val input = videoWithMetadata()
        val transform = VideoMetadataTransform(MetadataPolicy.STRIP_ALL)
        val result = transform.apply(input, VideoTestHelpers.testContext())
        assertNull(result.metadata.title, "Title should be stripped")
    }

    @Test
    fun stripAllRemovesArtist() = runTest {
        val input = videoWithMetadata()
        val transform = VideoMetadataTransform(MetadataPolicy.STRIP_ALL)
        val result = transform.apply(input, VideoTestHelpers.testContext())
        assertNull(result.metadata.artist, "Artist should be stripped")
    }

    @Test
    fun stripAllRemovesDuration() = runTest {
        val input = videoWithMetadata()
        val transform = VideoMetadataTransform(MetadataPolicy.STRIP_ALL)
        val result = transform.apply(input, VideoTestHelpers.testContext())
        assertNull(result.metadata.durationMs, "Duration metadata should be stripped")
    }

    @Test
    fun stripAllRemovesBitrate() = runTest {
        val input = videoWithMetadata()
        val transform = VideoMetadataTransform(MetadataPolicy.STRIP_ALL)
        val result = transform.apply(input, VideoTestHelpers.testContext())
        assertNull(result.metadata.bitrateKbps, "Bitrate should be stripped")
    }

    @Test
    fun stripAllRemovesAppMetadata() = runTest {
        val input = videoWithMetadata()
        val transform = VideoMetadataTransform(MetadataPolicy.STRIP_ALL)
        val result = transform.apply(input, VideoTestHelpers.testContext())
        assertEquals(emptyMap(), result.metadata.appMetadata, "App metadata should be cleared")
    }

    @Test
    fun stripAllPreservesVideoTrack() = runTest {
        val input = videoWithMetadata()
        val transform = VideoMetadataTransform(MetadataPolicy.STRIP_ALL)
        val result = transform.apply(input, VideoTestHelpers.testContext())
        assertEquals(input.videoTrack.width, result.videoTrack.width)
        assertEquals(input.videoTrack.height, result.videoTrack.height)
        assertEquals(input.videoTrack.frameRate, result.videoTrack.frameRate)
        assertEquals(input.durationMs, result.durationMs)
    }

    @Test
    fun transformId() {
        val transform = VideoMetadataTransform(MetadataPolicy.PRESERVE)
        assertEquals(TransformId("video-metadata"), transform.id)
    }
}
