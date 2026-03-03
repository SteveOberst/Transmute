package dev.transmute.libheif

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for [LibHeifResolver] - libheif binary discovery logic.
 *
 * The `available` property must always return a boolean (never throw).
 * Path lookups are tested only when libheif is present.
 *
 * The `desktopTest` Gradle task is gated by the `TRANSMUTE_LIBHEIF_TESTS`
 * environment variable, so all tests below may safely assume that a working
 * libheif installation exists.
 */
class LibHeifResolverTest {

    @Test
    fun available_returnsBooleanWithoutThrowing() {
        // This must never throw, regardless of whether libheif is installed.
        val result = LibHeifResolver.available
        assertTrue(result || !result, "available must return a boolean without throwing")
    }

    @Test
    fun decoderPath_nonBlankWhenAvailable() {
        val path = LibHeifResolver.decoderPath
        assertTrue(path.isNotBlank(), "Decoder path must be non-blank when libheif is available")
    }

    @Test
    fun encoderPath_nonNullAndNonBlankWhenAvailable() {
        val path = LibHeifResolver.encoderPath
        assertNotNull(path, "heif-enc path must be non-null when libheif is available")
        assertTrue(path.isNotBlank(), "heif-enc path must be non-blank")
    }

    @Test
    fun encoderAvailable_trueWhenLibHeifIsInstalled() {
        assertTrue(LibHeifResolver.encoderAvailable, "heif-enc must be available alongside heif-dec")
    }

    @Test
    fun diagnosticMessage_populatedAfterResolution() {
        LibHeifResolver.resolve()
        assertTrue(
            LibHeifResolver.diagnosticMessage.isNotBlank(),
            "Diagnostic message must be populated after resolution",
        )
    }

    @Test
    fun diagnosticMessage_containsLibHeifTag() {
        LibHeifResolver.resolve()
        assertTrue(
            "[libheif]" in LibHeifResolver.diagnosticMessage,
            "Diagnostic message must include the [libheif] prefix",
        )
    }
}
