package dev.transmute.io

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * A suspending, sequential **read-only** byte source.
 *
 * `TSource` is the Transmute abstraction for streaming input - files,
 * network responses, in-memory buffers, etc. All reads are suspending
 * to support non-blocking I/O on every platform.
 *
 * Obtain a `TSource` from `TransmuteFileSystem.source()` or create one
 * from raw bytes via [ByteArraySource]:
 *
 * ```kotlin
 * val src: TSource = fs.source(TPath.of("image.png"))
 * val bytes = src.readAll()
 * src.close()
 * ```
 *
 * @see TSink
 * @see TChannel
 */
interface TSource : AutoCloseable {

    /**
     * Read up to [length] bytes into [buffer] starting at [offset].
     *
     * @return the number of bytes actually read, or `-1` at end-of-stream.
     */
    suspend fun read(buffer: ByteArray, offset: Int = 0, length: Int = buffer.size - offset): Int

    /**
     * Read all remaining bytes from this source.
     */
    suspend fun readAll(): ByteArray

    /**
     * Release any resources held by this source.
     */
    override fun close()
}

/**
 * In-memory [TSource] backed by a [ByteArray].
 *
 * All read operations are guarded by a [Mutex] so that concurrent
 * coroutines sharing this source will not corrupt the read cursor.
 *
 * Useful for testing and for wrapping byte data that's already loaded.
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

    override fun close() { /* no-op for in-memory source */ }
}
