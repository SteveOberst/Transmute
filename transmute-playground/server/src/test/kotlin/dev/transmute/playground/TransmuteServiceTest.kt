package dev.transmute.playground

import dev.transmute.gstreamer.GStreamer
import dev.transmute.libheif.LibHeif
import dev.transmute.playground.shared.TransformRequest
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for [TransmuteService].
 *
 * GStreamer is disabled via [initiallyDisabledPlugins] so no native libraries
 * are loaded during these tests and the suite can run in a plain CI environment.
 */
class TransmuteServiceTest {

    private lateinit var tempDir: File
    private lateinit var service: TransmuteService

    @BeforeTest
    fun setUp() {
        tempDir = Files.createTempDirectory("transmute-service-test").toFile()
        service = TransmuteService(
            tempDir = tempDir,
            initiallyDisabledPlugins = setOf(GStreamer.key),
        )
    }

    @AfterTest
    fun tearDown() {
        service.cleanup()
        tempDir.deleteRecursively()
    }

    // -- File management -------------------------------------------------------

    @Test
    fun storeFileReturnsHandleWithMatchingName() {
        val bytes = ByteArray(8) { it.toByte() }
        val handle = service.storeFile("sample.bin", bytes)
        assertEquals("sample.bin", handle.originalName)
        assertEquals(8L, handle.fileSize)
    }

    @Test
    fun getFileReturnsStoredFile() {
        val bytes = ByteArray(4) { 0x42 }
        val handle = service.storeFile("data.bin", bytes)
        val uploaded = service.getFile(handle.handle)
        assertNotNull(uploaded)
        assertEquals("data.bin", uploaded.name)
    }

    @Test
    fun getFileReturnsNullForUnknownHandle() {
        assertNull(service.getFile("nonexistent-handle"))
    }

    @Test
    fun getFileBytesRoundTrips() {
        val original = byteArrayOf(0x01, 0x02, 0x03, 0x04)
        val handle = service.storeFile("bytes.bin", original)
        val retrieved = service.getFileBytes(handle.handle)
        assertNotNull(retrieved)
        assertEquals(original.toList(), retrieved.toList())
    }

    @Test
    fun getFileBytesReturnsNullForUnknownHandle() {
        assertNull(service.getFileBytes("nonexistent-handle"))
    }

    @Test
    fun listFilesIsEmptyInitially() {
        assertTrue(service.listFiles().isEmpty())
    }

    @Test
    fun listFilesContainsStoredFiles() {
        service.storeFile("a.bin", ByteArray(1))
        service.storeFile("b.bin", ByteArray(1))
        val files = service.listFiles()
        assertEquals(2, files.size)
        assertTrue(files.any { it.name == "a.bin" })
        assertTrue(files.any { it.name == "b.bin" })
    }

    // -- Format catalog --------------------------------------------------------

    @Test
    fun allFormatsReturnsWithoutThrowing() {
        // Primarily verifies the call completes - actual count depends on which
        // codecs are registered without GStreamer.
        val formats = service.allFormats()
        assertNotNull(formats)
    }

    // -- Plugin management -----------------------------------------------------

    @Test
    fun listPluginsReturnsGStreamer() {
        val plugins = service.listPlugins()
        // The playground exposes all known built-in plugins, even if disabled
        // or unavailable on the current machine.
        assertTrue(plugins.size >= 2)
        assertTrue(plugins.any { it.key == GStreamer.key.id })
        assertTrue(plugins.any { it.key == LibHeif.key.id })
    }

    @Test
    fun gStreamerIsDisabledWhenInInitiallyDisabledPlugins() {
        val gstreamer = service.listPlugins().first { it.key == GStreamer.key.id }
        assertEquals(false, gstreamer.enabled)
    }

    @Test
    fun getPluginReturnsNullForUnknownKey() {
        assertNull(service.getPlugin("unknown-plugin"))
    }

    @Test
    fun getPluginReturnsGStreamer() {
        assertNotNull(service.getPlugin(GStreamer.key.id))
    }

    // -- Transform execution validation ----------------------------------------

    @Test
    fun executeTransformThrowsForUnsupportedFormat() {
        val bytes = ByteArray(8) { it.toByte() }
        val handle = service.storeFile("test.bin", bytes)
        val request = TransformRequest(
            fileHandle = handle.handle,
            outputFormat = "xyz",
            pipeline = emptyList(),
        )
        assertFails {
            runBlocking { service.executeTransform(request) }
        }
    }

    @Test
    fun executeTransformThrowsIllegalArgumentForUnsupportedFormat() {
        val bytes = ByteArray(8) { it.toByte() }
        val handle = service.storeFile("test.bin", bytes)
        val request = TransformRequest(
            fileHandle = handle.handle,
            outputFormat = "unsupportedformat",
            pipeline = emptyList(),
        )
        val ex = assertFails {
            runBlocking { service.executeTransform(request) }
        }
        assertTrue(ex is IllegalArgumentException, "Expected IllegalArgumentException but got ${ex::class.simpleName}")
    }

    @Test
    fun executeTransformThrowsForMissingHandle() {
        val request = TransformRequest(
            fileHandle = "no-such-handle",
            outputFormat = "png",
            pipeline = emptyList(),
        )
        assertFails {
            runBlocking { service.executeTransform(request) }
        }
    }
}
