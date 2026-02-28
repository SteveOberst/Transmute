package dev.transmute.playground.shared

import kotlinx.serialization.Serializable

/**
 * Handle returned when a file is uploaded.
 *
 * Returned by `POST /api/upload`.
 */
@Serializable
data class FileHandle(
    val handle: String,
    val originalName: String,
    val fileSize: Long,
    val domain: MediaDomainDto? = null,
    val format: String? = null,
)

/**
 * Server health response.
 *
 * Returned by `GET /api/health`.
 */
@Serializable
data class HealthResponse(
    val status: String = "ok",
    val pluginCount: Int = 0,
    val imageFormats: Int = 0,
    val audioFormats: Int = 0,
    val videoFormats: Int = 0,
    val diagnostics: Map<String, Boolean> = emptyMap(),
)

/**
 * WebSocket progress event.
 */
@Serializable
data class ProgressEvent(
    val jobId: String,
    val stage: String,
    val progress: Float,
    val message: String? = null,
)

/**
 * Waveform amplitude data for audio preview.
 *
 * Returned by `GET /api/waveform/{handle}`.
 */
@Serializable
data class WaveformData(
    val samples: List<Float>,
    val sampleRate: Int,
    val channels: Int,
    val durationMs: Long,
)
