package dev.transmute.playground

import dev.transmute.playground.routes.*
import dev.transmute.playground.ws.progressSocket
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.http.content.*
import io.ktor.server.websocket.*
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.io.File
import kotlin.time.Duration.Companion.seconds

private val logger = LoggerFactory.getLogger("PlaygroundServer")

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    val host = System.getenv("HOST") ?: "0.0.0.0"

    logger.info("Starting Transmute Playground on $host:$port")

    embeddedServer(Netty, port = port, host = host) {
        configureServer()
    }.start(wait = true)
}

fun Application.configureServer(service: TransmuteService = TransmuteService()) {

    // -- Serialization --------------------------------------------------------
    install(ContentNegotiation) {
        json(Json {
            prettyPrint = false
            encodeDefaults = true
            ignoreUnknownKeys = true
            explicitNulls = false
            coerceInputValues = true
        })
    }

    // -- CORS (dev-friendly, allow all origins) --------------------------------
    install(CORS) {
        anyHost()
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Options)
    }

    // -- WebSockets -----------------------------------------------------------
    install(WebSockets) {
        pingPeriod = 15.seconds
        timeout = 60.seconds
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }

    // -- Call Logging ---------------------------------------------------------
    install(CallLogging) {
        level = org.slf4j.event.Level.INFO
    }

    // -- Status Pages (global error handler) -----------------------------------
    install(StatusPages) {
        exception<BadRequestException> { call, cause ->
            // Common during live-preview while the user is still typing.
            logger.debug("Bad request: ${cause.message}")
            call.respond(
                HttpStatusCode.BadRequest,
                mapOf("error" to (cause.message ?: "Bad request")),
            )
        }

        exception<io.ktor.serialization.JsonConvertException> { call, cause ->
            logger.debug("JSON conversion failed: ${cause.message}")
            call.respond(
                HttpStatusCode.BadRequest,
                mapOf("error" to (cause.message ?: "Invalid JSON")),
            )
        }

        exception<SerializationException> { call, cause ->
            logger.debug("Serialization failed: ${cause.message}")
            call.respond(
                HttpStatusCode.BadRequest,
                mapOf("error" to (cause.message ?: "Invalid request")),
            )
        }

        exception<Throwable> { call, cause ->
            logger.error("Unhandled exception", cause)
            call.respond(
                HttpStatusCode.InternalServerError,
                mapOf("error" to (cause.message ?: "Internal server error")),
            )
        }
    }

    // -- Routing --------------------------------------------------------------
    routing {
        // REST endpoints
        healthRoutes(service)
        formatRoutes(service)
        inspectRoutes(service)
        transformRoutes(service)
        pluginRoutes(service)

        // WebSocket
        progressSocket()

        // Serve the Compose/Wasm frontend as static files
        val staticDir = System.getenv("TRANSMUTE_STATIC_DIR")
        if (staticDir != null && File(staticDir).isDirectory) {
            staticFiles("/", File(staticDir)) {
                default("index.html")
            }
            logger.info("Serving static files from $staticDir")
        } else {
            // Fallback: return a simple landing page
            get("/") {
                call.respondText(
                    """
                    <!DOCTYPE html>
                    <html>
                    <head><title>Transmute Playground</title></head>
                    <body style="font-family: system-ui; background: #0E1117; color: #C9D1D9; display: flex; align-items: center; justify-content: center; height: 100vh; margin: 0;">
                        <div style="text-align: center;">
                            <h1>Transmute Playground</h1>
                            <p>API server is running. Set <code>TRANSMUTE_STATIC_DIR</code> to serve the web frontend.</p>
                            <p><a href="/api/health" style="color: #58A6FF;">Health Check</a> &middot; <a href="/api/formats" style="color: #58A6FF;">Formats</a> &middot; <a href="/api/transforms" style="color: #58A6FF;">Transforms</a></p>
                        </div>
                    </body>
                    </html>
                    """.trimIndent(),
                    ContentType.Text.Html,
                )
            }
        }
    }

    // Shutdown hook
    monitor.subscribe(ApplicationStopped) {
        logger.info("Shutting down -- cleaning up resources")
        service.cleanup()
    }
}
