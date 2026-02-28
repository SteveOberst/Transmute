package dev.transmute.playground.routes

import dev.transmute.playground.TransmuteService
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.toByteArray

fun Route.inspectRoutes(service: TransmuteService) {
    post("/api/upload") {
        val multipart = call.receiveMultipart()
        var fileName = "unknown"
        var fileBytes: ByteArray? = null

        multipart.forEachPart { part ->
            when (part) {
                is PartData.FileItem -> {
                    fileName = part.originalFileName ?: "unknown"
                    fileBytes = part.provider().toByteArray()
                }
                else -> {}
            }
            part.dispose()
        }

        val bytes = fileBytes
        if (bytes == null) {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "No file provided"))
            return@post
        }

        val handle = service.storeFile(fileName, bytes)
        call.respond(HttpStatusCode.Created, handle)
    }

    post("/api/inspect/{handle}") {
        val handle = call.parameters["handle"]
        if (handle == null) {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing handle"))
            return@post
        }

        val result = service.inspect(handle)
        if (result == null) {
            call.respond(HttpStatusCode.NotFound, mapOf("error" to "File not found"))
            return@post
        }

        call.respond(result)
    }

    get("/api/files/{handle}") {
        val handle = call.parameters["handle"]
        if (handle == null) {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing handle"))
            return@get
        }

        val bytes = service.getFileBytes(handle)
        if (bytes == null) {
            call.respond(HttpStatusCode.NotFound, mapOf("error" to "File not found"))
            return@get
        }

        val uploaded = service.getFile(handle)!!
        call.response.header(
            HttpHeaders.ContentDisposition,
            ContentDisposition.Attachment.withParameter(
                ContentDisposition.Parameters.FileName, uploaded.name
            ).toString()
        )
        call.respondBytes(bytes, ContentType.Application.OctetStream)
    }

    get("/api/files") {
        val files = service.listFiles().map { uploaded ->
            mapOf(
                "handle" to uploaded.handle,
                "name" to uploaded.name,
                "size" to uploaded.size.toString(),
            )
        }
        call.respond(files)
    }
}
