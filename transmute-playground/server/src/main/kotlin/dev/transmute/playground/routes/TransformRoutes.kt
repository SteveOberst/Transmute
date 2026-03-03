package dev.transmute.playground.routes

import dev.transmute.playground.TransmuteService
import dev.transmute.playground.shared.TransformRequest
import dev.transmute.playground.shared.TransformResult
import io.ktor.http.*
import io.ktor.server.plugins.*
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
                            put("pipeline", request.pipeline.joinToString(" -> ") { it.transformId })
                        }
                    },
                    generatedCode = code,
                    durationMs = durationMs,
                )
            )
        } catch (e: BadRequestException) {
            // Let StatusPages format this as a clean 400 without dumping stacks.
            throw e
        } catch (e: Exception) {
            call.respond(
                HttpStatusCode.InternalServerError,
                mapOf("error" to (e.message ?: "Transform failed")),
            )
        }
    }
}

/**
 * Generates illustrative Kotlin code representing the current transform pipeline.
 *
 * Uses the public `transmute.$domain.to($format) { transform { add(...) } }.transmute(input)`
 * DSL form that matches the Transmute API.
 */
private fun generateCode(request: TransformRequest): String = buildString {
    val fmt = request.outputFormat.lowercase()

    val imageFormats = setOf("jpeg", "jpg", "png", "webp", "heif", "heic", "avif", "gif", "bmp", "tiff")
    val audioFormats = setOf("wav", "mp3", "aac", "m4a", "flac", "ogg", "opus")

    val (domain, formatEnum) = when {
        fmt in imageFormats -> {
            val name = if (fmt == "jpg") "jpeg" else fmt
            "image" to "ImageFormat.${name.replaceFirstChar { it.uppercase() }}"
        }
        fmt in audioFormats ->
            "audio" to "AudioFormat.${fmt.replaceFirstChar { it.uppercase() }}"
        else ->
            "video" to "VideoFormat.${fmt.replaceFirstChar { it.uppercase() }}"
    }

    appendLine("val result = transmute.$domain.to($formatEnum) {")
    if (request.pipeline.isNotEmpty()) {
        appendLine("    transform {")
        for (step in request.pipeline) {
            val params = step.parameters.entries
                .asSequence()
                .filter { (_, v) -> !v.isNullOrBlank() }
                .joinToString(", ") { (k, v) ->
                    val vv = v!!
                    val literal = vv.toIntOrNull()?.toString()
                        ?: vv.toLongOrNull()?.let { "${it}L" }
                        ?: vv.toFloatOrNull()?.let { "${it}f" }
                        ?: vv.toBooleanStrictOrNull()?.toString()
                    "$k = ${literal ?: "\"$vv\""}"
                }
            if (params.isEmpty()) {
                appendLine("        add(${step.transformId}())")
            } else {
                appendLine("        add(${step.transformId}($params))")
            }
        }
        appendLine("    }")
    }
    append("}.transmute(input)")
}
