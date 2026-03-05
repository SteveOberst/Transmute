package dev.transmute.io

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

// Interface definition lives in transmute-model:core (dev.transmute.io.TChannel)

/**
 * In-memory [TChannel] backed by a [ByteArray].
 */
class ByteArrayChannel(private val data: ByteArray) : TChannel {
  private val readMutex = Mutex()
  private var readPos = 0

  private val writeMutex = Mutex()
  private val chunks = mutableListOf<ByteArray>()

  override suspend fun read(buffer: ByteArray, offset: Int, length: Int): Int = readMutex.withLock {
    if (readPos >= data.size) return@withLock -1
    val available = minOf(length, data.size - readPos)
    data.copyInto(buffer, destinationOffset = offset, startIndex = readPos, endIndex = readPos + available)
    readPos += available
    available
  }

  override suspend fun readAll(): ByteArray = readMutex.withLock {
    if (readPos >= data.size) return@withLock ByteArray(0)
    val remaining = data.copyOfRange(readPos, data.size)
    readPos = data.size
    remaining
  }

  override suspend fun write(buffer: ByteArray, offset: Int, length: Int): Unit = writeMutex.withLock {
    chunks.add(buffer.copyOfRange(offset, offset + length))
  }

  override suspend fun flush() { /* no-op */ }

  override fun close() { /* no-op */ }

  fun collect(): ByteArray {
    if (chunks.isEmpty()) return ByteArray(0)
    val total = chunks.sumOf { it.size }
    val result = ByteArray(total)
    var pos = 0
    for (chunk in chunks) {
      chunk.copyInto(result, destinationOffset = pos)
      pos += chunk.size
    }
    return result
  }
}
