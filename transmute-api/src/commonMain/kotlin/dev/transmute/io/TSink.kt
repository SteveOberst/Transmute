package dev.transmute.io

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * A suspending, sequential **write-only** byte sink.
 *
 * `TSink` is the Transmute abstraction for streaming output - files,
 * network uploads, in-memory buffers, etc. All writes are suspending
 * to support non-blocking I/O on every platform.
 *
 * Obtain a `TSink` from `TransmuteFileSystem.sink()` or create one
 * in-memory via [ByteArraySink]:
 *
 * ```kotlin
 * val sink: TSink = fs.sink(TPath.of("output.png"))
 * sink.write(data)
 * sink.flush()
 * sink.close()
 * ```
 *
 * @see TSource
 * @see TChannel
 */
interface TSink : AutoCloseable {

    /**
     * Write [length] bytes from [buffer] starting at [offset].
     */
    suspend fun write(buffer: ByteArray, offset: Int = 0, length: Int = buffer.size - offset)

    /**
     * Write all [data] bytes to this sink.
     */
    suspend fun writeAll(data: ByteArray) {
        write(data, 0, data.size)
    }

    /**
     * Flush any buffered data to the underlying destination.
     */
    suspend fun flush()

    /**
     * Release any resources held by this sink.
     */
    override fun close()
}

/**
 * In-memory [TSink] that collects all written bytes.
 *
 * All write operations are guarded by a [Mutex] so that concurrent
 * coroutines sharing this sink will not corrupt the write buffer.
 *
 * Useful for testing and for capturing output to a [ByteArray].
 */
class ByteArraySink : TSink {
    private val mutex = Mutex()
    private val chunks = mutableListOf<ByteArray>()

    override suspend fun write(buffer: ByteArray, offset: Int, length: Int): Unit = mutex.withLock {
        chunks.add(buffer.copyOfRange(offset, offset + length))
    }

    override suspend fun flush() { /* no-op */ }

    override fun close() { /* no-op */ }

    /**
     * Collect all written bytes into a single [ByteArray].
     *
     * Call only after all writes have completed.
     */
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
