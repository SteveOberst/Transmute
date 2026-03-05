package dev.transmute.io

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

// Interface definition lives in transmute-model:core (dev.transmute.io.TSource)

/**
 * In-memory [TSource] backed by a [ByteArray].
 *
 * All read operations are guarded by a [Mutex] so that concurrent
 * coroutines sharing this source will not corrupt the read cursor.
 */
class ByteArraySource(private val data: ByteArray) : TSource {
  private val mutex = Mutex()
  private var position = 0

  override suspend fun read(buffer: ByteArray, offset: Int, length: Int): Int = mutex.withLock {
    if (position >= data.size) return@withLock -1
    val available = minOf(length, data.size - position)
    data.copyInto(buffer, destinationOffset = offset, startIndex = position, endIndex = position + available)
    position += available
    available
  }

  override suspend fun readAll(): ByteArray = mutex.withLock {
    if (position >= data.size) return@withLock ByteArray(0)
    val remaining = data.copyOfRange(position, data.size)
    position = data.size
    remaining
  }

  override fun close() { /* no-op */ }
}
