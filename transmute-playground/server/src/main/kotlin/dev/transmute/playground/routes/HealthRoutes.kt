package dev.transmute.playground.routes

import dev.transmute.playground.TransmuteService
import dev.transmute.playground.shared.HealthResponse
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.healthRoutes(service: TransmuteService) {
  get("/api/health") {
    val diagnostics = service.transmute.diagnostics.summary()
      .mapKeys { it.key.toString() }

    call.respond(
      HealthResponse(
        status = "ok",
        pluginCount = service.transmute.installedPlugins.size,
        imageFormats = service.imageFormats().size,
        audioFormats = service.audioFormats().size,
        videoFormats = service.videoFormats().size,
        diagnostics = diagnostics,
      ),
    )
  }
}
