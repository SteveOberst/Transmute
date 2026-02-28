package dev.transmute.playground.routes

import dev.transmute.playground.TransmuteService
import dev.transmute.playground.shared.*
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.pluginRoutes(service: TransmuteService) {
    get("/api/plugins") {
        val plugins = service.listPlugins()
        call.respond(plugins)
    }

    get("/api/plugins/{key}") {
        val key = call.parameters["key"]
            ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing plugin key"))

        val plugin = service.getPlugin(key)
        if (plugin == null) {
            call.respond(HttpStatusCode.NotFound, mapOf("error" to "Plugin not found: $key"))
        } else {
            call.respond(plugin)
        }
    }

    put("/api/plugins/{key}") {
        val key = call.parameters["key"]
            ?: return@put call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing plugin key"))

        val update = call.receive<PluginUpdate>()

        try {
            val result = service.updatePlugin(key, update)
            if (result == null) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "Plugin not found: $key"))
            } else {
                call.respond(result)
            }
        } catch (e: Exception) {
            call.respond(
                HttpStatusCode.InternalServerError,
                mapOf("error" to (e.message ?: "Plugin update failed")),
            )
        }
    }
}
