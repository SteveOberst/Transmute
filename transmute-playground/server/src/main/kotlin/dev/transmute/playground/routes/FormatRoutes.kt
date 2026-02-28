package dev.transmute.playground.routes

import dev.transmute.playground.TransmuteService
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.formatRoutes(service: TransmuteService) {
    get("/api/formats") {
        val domain = call.request.queryParameters["domain"]
        val formats = when (domain?.uppercase()) {
            "IMAGE" -> service.imageFormats()
            "AUDIO" -> service.audioFormats()
            "VIDEO" -> service.videoFormats()
            else -> service.allFormats()
        }
        call.respond(formats)
    }

    get("/api/transforms") {
        val domain = call.request.queryParameters["domain"]
        val transforms = when (domain?.uppercase()) {
            "IMAGE" -> service.imageTransforms()
            "AUDIO" -> service.audioTransforms()
            "VIDEO" -> service.videoTransforms()
            else -> service.allTransforms()
        }
        call.respond(transforms)
    }
}
