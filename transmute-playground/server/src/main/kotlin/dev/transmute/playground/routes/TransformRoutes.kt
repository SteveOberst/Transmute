package dev.transmute.playground.routes

import dev.transmute.playground.TransmuteService
import dev.transmute.playground.shared.TransformRequest
import dev.transmute.playground.shared.TransformResult
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.transformRoutes(service: TransmuteService) {
    post("/api/transform") {
        val request = call.receive<TransformRequest>()
        val uploaded = service.getFile(request.fileHandle)

        if (uploaded == null) {
            call.respond(HttpStatusCode.NotFound, mapOf("error" to "File not found: ${request.fileHandle}"))
            return@post
        }

        try {
            val startTime = System.currentTimeMillis()

            // Execute the transform pipeline via the Transmute DSL.
            val resultBytes = service.executeTransform(request)
            val resultHandle = service.storeFile(
                "${uploaded.name}.${request.outputFormat}",
                resultBytes,
            )

            val durationMs = System.currentTimeMillis() - startTime

            val code = generateCode(request)

            call.respond(
                TransformResult(
                    resultHandle = resultHandle.handle,
                    outputFormat = request.outputFormat,
                    fileSize = resultBytes.size.toLong(),
                    properties = buildMap {
                        put("inputSize", uploaded.size.toString())
                        if (request.pipeline.isNotEmpty()) {
                            put("pipeline", request.pipeline.joinToString(" → ") { it.transformId })
                        }
                    },
                    generatedCode = code,
                    durationMs = durationMs,
                )
            )
        } catch (e: Exception) {
            call.respond(
                HttpStatusCode.InternalServerError,
                mapOf("error" to (e.message ?: "Transform failed")),
            )
        }
    }
}

/**
 * Generates Kotlin code representing the current transform pipeline.
 */
private fun generateCode(request: TransformRequest): String = buildString {
    val domain = when {
        request.outputFormat.lowercase() in listOf("jpeg", "jpg", "png", "webp", "heif", "avif", "gif", "bmp", "tiff") -> "image"
        request.outputFormat.lowercase() in listOf("wav", "mp3", "aac", "m4a", "flac", "ogg", "opus") -> "audio"
        else -> "video"
    }

    appendLine("val result = transmute.$domain {")
    for (step in request.pipeline) {
        val params = step.parameters.entries.joinToString(", ") { (k, v) ->
            "$k = $v"
        }
        if (params.isEmpty()) {
            appendLine("    ${step.transformId}()")
        } else {
            appendLine("    ${step.transformId}($params)")
        }
    }
    appendLine("}.transmute(input)")
}
