package dev.transmute.audio

import dev.transmute.core.MetadataPolicy
import dev.transmute.core.pipeline.TransformId
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class AudioMetadataTransformTest {

    private fun audioWithMetadata(): AudioIR {
        val ir = AudioTestHelpers.sineWave(
            frequency = 440f,
            durationMs = 100,
            sampleRate = 44100,
            channelCount = 1,
        )
        return ir.copy(
            metadata = AudioMetadata(
                title = "Test Track",
                artist = "Test Artist",
                album = "Test Album",
                genre = "Electronic",
                durationMs = 100,
                bitrateKbps = 320,
                appMetadata = mapOf("encoder" to "transmute", "version" to "1.0"),
            ),
        )
    }

    @Test
    fun preservePolicyKeepsAllMetadata() = runTest {
        val input = audioWithMetadata()
        val transform = AudioMetadataTransform(MetadataPolicy.PRESERVE)
        val result = transform.apply(input, AudioTestHelpers.testContext())
        assertSame(input, result, "PRESERVE should return the same instance")
    }

    @Test
    fun stripAllRemovesTitle() = runTest {
        val input = audioWithMetadata()
        val transform = AudioMetadataTransform(MetadataPolicy.STRIP_ALL)
        val result = transform.apply(input, AudioTestHelpers.testContext())
        assertNull(result.metadata.title, "Title should be stripped")
    }

    @Test
    fun stripAllRemovesArtist() = runTest {
        val input = audioWithMetadata()
        val transform = AudioMetadataTransform(MetadataPolicy.STRIP_ALL)
        val result = transform.apply(input, AudioTestHelpers.testContext())
        assertNull(result.metadata.artist, "Artist should be stripped")
    }

    @Test
    fun stripAllRemovesAlbum() = runTest {
        val input = audioWithMetadata()
        val transform = AudioMetadataTransform(MetadataPolicy.STRIP_ALL)
        val result = transform.apply(input, AudioTestHelpers.testContext())
        assertNull(result.metadata.album, "Album should be stripped")
    }

    @Test
    fun stripAllRemovesGenre() = runTest {
        val input = audioWithMetadata()
        val transform = AudioMetadataTransform(MetadataPolicy.STRIP_ALL)
        val result = transform.apply(input, AudioTestHelpers.testContext())
        assertNull(result.metadata.genre, "Genre should be stripped")
    }

    @Test
    fun stripAllRemovesAppMetadata() = runTest {
        val input = audioWithMetadata()
        val transform = AudioMetadataTransform(MetadataPolicy.STRIP_ALL)
        val result = transform.apply(input, AudioTestHelpers.testContext())
        assertEquals(emptyMap(), result.metadata.appMetadata, "App metadata should be cleared")
    }

    @Test
    fun stripAllPreservesSamples() = runTest {
        val input = audioWithMetadata()
        val transform = AudioMetadataTransform(MetadataPolicy.STRIP_ALL)
        val result = transform.apply(input, AudioTestHelpers.testContext())
        assertEquals(input.sampleRate, result.sampleRate)
        assertEquals(input.channelCount, result.channelCount)
        assertEquals(input.durationMs, result.durationMs)
        assertEquals(input.samples.data.size, result.samples.data.size)
        for (i in input.samples.data.indices) {
            assertEquals(input.samples.data[i], result.samples.data[i], "Sample data should be unchanged")
        }
    }

    @Test
    fun transformId() {
        val transform = AudioMetadataTransform(MetadataPolicy.PRESERVE)
        assertEquals(TransformId("audio-metadata"), transform.id)
    }
}
