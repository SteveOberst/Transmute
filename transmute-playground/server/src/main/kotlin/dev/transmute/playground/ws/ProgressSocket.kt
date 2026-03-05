package dev.transmute.playground.ws

import dev.transmute.playground.shared.ProgressEvent
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * In-memory broadcast hub for progress events.
 * Server-side code calls [broadcast] to push events to all connected WebSocket clients.
 */
object ProgressHub {
  private val json = Json { encodeDefaults = true }
  private val sessions = mutableSetOf<DefaultWebSocketSession>()

  suspend fun addSession(session: DefaultWebSocketSession) {
    synchronized(sessions) { sessions.add(session) }
    try {
      // Keep the session alive until the client disconnects
      for (frame in session.incoming) {
        // Client messages are ignored - this is a server->client channel
      }
    } catch (_: ClosedReceiveChannelException) {
      // Normal disconnect
    } finally {
      synchronized(sessions) { sessions.remove(session) }
    }
  }

  suspend fun broadcast(event: ProgressEvent) {
    val text = json.encodeToString(event)
    val snapshot = synchronized(sessions) { sessions.toList() }
    for (session in snapshot) {
      try {
        session.send(Frame.Text(text))
      } catch (_: Exception) {
        synchronized(sessions) { sessions.remove(session) }
      }
    }
  }
}

fun Route.progressSocket() {
  webSocket("/ws/progress") {
    ProgressHub.addSession(this)
  }
}
