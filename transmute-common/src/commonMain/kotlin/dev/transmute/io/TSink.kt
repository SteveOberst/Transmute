package dev.transmute.io

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

// Interface definition lives in transmute-model:core (dev.transmute.io.TSink)

/**
 * In-memory [TSink] that collects all written bytes.
 */
class ByteArraySink : TSink {
    private val mutex = Mutex()
    private val chunks = mutableListOf<ByteArray>()

    override suspend fun write(buffer: ByteArray, offset: Int, length: Int): Unit = mutex.withLock {
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
