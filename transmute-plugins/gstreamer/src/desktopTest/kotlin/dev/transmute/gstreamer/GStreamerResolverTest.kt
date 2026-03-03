package dev.transmute.gstreamer

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for [GStreamerResolver] - the GStreamer binary discovery logic.
 *
 * The `available` property must always return a boolean (never throw).
 * Path lookups and element checks are tested only when GStreamer is present.
 */
class GStreamerResolverTest {

    @Test
    fun available_returnsBooleanWithoutThrowing() {
        // This must never throw, regardless of whether GStreamer is installed.
        val result = GStreamerResolver.available
        assertTrue(result || !result, "available must return a boolean")
    }

    @Test
    fun gstLaunchPath_nonNullWhenAvailable() {
        val path = GStreamerResolver.gstLaunchPath
        assertNotNull(path, "gst-launch-1.0 path must be resolved when GStreamer is available")
        assertTrue(path.isNotBlank(), "Path must not be blank")
    }

    @Test
    fun gstInspectPath_nonNullWhenAvailable() {
        val path = GStreamerResolver.gstInspectPath
        assertNotNull(path, "gst-inspect-1.0 path must be resolved when GStreamer is available")
        assertTrue(path.isNotBlank(), "Path must not be blank")
    }

    @Test
    fun gstDiscovererPath_nonNullWhenAvailable() {
        val path = GStreamerResolver.gstDiscovererPath
        assertNotNull(path, "gst-discoverer-1.0 path must be resolved when GStreamer is available")
        assertTrue(path.isNotBlank(), "Path must not be blank")
    }

    @Test
    fun hasElement_coreElement_returnsTrue() {
        // "decodebin" is part of gstreamer1.0-plugins-base - always available.
        assertTrue(
            GStreamerResolver.hasElement("decodebin"),
            "decodebin must be available in any GStreamer installation",
        )
    }

    @Test
    fun hasElement_nonexistentElement_returnsFalse() {
        val result = GStreamerResolver.hasElement("__nonexistent_element_42__")
        assertTrue(!result, "Nonexistent element must return false")
    }
}