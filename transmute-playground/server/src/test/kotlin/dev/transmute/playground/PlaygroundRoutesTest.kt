package dev.transmute.playground

import dev.transmute.gstreamer.GStreamer
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import io.ktor.server.testing.*
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Integration tests for all REST routes registered by [configureServer].
 *
 * Each test spins up an in-memory Ktor test server via [testApplication].
 * GStreamer is disabled so no native libraries are required in CI.
 */
class PlaygroundRoutesTest {

    /** Creates a fresh [TransmuteService] with GStreamer disabled. */
    private fun testService() = TransmuteService(
        tempDir = Files.createTempDirectory("test-routes").toFile(),
        initiallyDisabledPlugins = setOf(GStreamer.key),
    )

    // ── /api/health ───────────────────────────────────────────────────────────

    @Test
    fun healthRouteReturns200() = testApplication {
        application { configureServer(testService()) }
        val response = client.get("/api/health")
        assertEquals(HttpStatusCode.OK, response.status)
    }

    // ── /api/formats ──────────────────────────────────────────────────────────

    @Test
    fun formatsRouteReturns200() = testApplication {
        application { configureServer(testService()) }
        val response = client.get("/api/formats")
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun formatsResponseBodyIsNotEmpty() = testApplication {
        application { configureServer(testService()) }
        val response = client.get("/api/formats")
        assertTrue(response.headers[HttpHeaders.ContentType]?.contains("json") == true)
    }

    // ── /api/transforms ───────────────────────────────────────────────────────

    @Test
    fun transformsRouteReturns200() = testApplication {
        application { configureServer(testService()) }
        val response = client.get("/api/transforms")
        assertEquals(HttpStatusCode.OK, response.status)
    }

    // ── /api/plugins ──────────────────────────────────────────────────────────

    @Test
    fun pluginsRouteReturns200() = testApplication {
        application { configureServer(testService()) }
        val response = client.get("/api/plugins")
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun knownPluginRouteReturns200() = testApplication {
        application { configureServer(testService()) }
        val response = client.get("/api/plugins/gstreamer")
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun unknownPluginRouteReturns404() = testApplication {
        application { configureServer(testService()) }
        val response = client.get("/api/plugins/nonexistent-plugin")
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    // ── /api/files ────────────────────────────────────────────────────────────

    @Test
    fun filesListRouteReturns200() = testApplication {
        application { configureServer(testService()) }
        val response = client.get("/api/files")
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun fileByUnknownHandleReturns404() = testApplication {
        application { configureServer(testService()) }
        val response = client.get("/api/files/unknown-handle")
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    // ── /api/upload ───────────────────────────────────────────────────────────

    @Test
    fun uploadWithFilePart201() = testApplication {
        application { configureServer(testService()) }
        val response = client.post("/api/upload") {
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append(
                            "file",
                            ByteArray(8) { it.toByte() },
                            Headers.build {
                                append(
                                    HttpHeaders.ContentDisposition,
                                    "form-data; name=\"file\"; filename=\"test.bin\"",
                                )
                            },
                        )
                    },
                ),
            )
        }
        assertEquals(HttpStatusCode.Created, response.status)
    }

    @Test
    fun uploadWithoutFilePart400() = testApplication {
        application { configureServer(testService()) }
        val response = client.post("/api/upload") {
            setBody(
                MultiPartFormDataContent(
                    formData {
                        // submit a text field, not a file
                        append("not-a-file", "hello")
                    },
                ),
            )
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    // ── /api/inspect ──────────────────────────────────────────────────────────

    @Test
    fun inspectUnknownHandleReturns404() = testApplication {
        application { configureServer(testService()) }
        val response = client.post("/api/inspect/unknown-handle")
        assertEquals(HttpStatusCode.NotFound, response.status)
    }
}
