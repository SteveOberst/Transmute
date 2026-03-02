package dev.transmute.io

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * A suspending **read-write** byte channel.
 *
 * `TChannel` combines [TSource] and [TSink] - it can read the current
 * content of a resource and write updated content back. This is the
 * primary abstraction for **in-place transforms** where Transmute
 * reads a file, applies mutations, and writes the result back.
 *
 * Obtain a `TChannel` from `TransmuteFileSystem.channel()`:
 *
 * ```kotlin
 * val ch: TChannel = fs.channel(TPath.of("image.png"))
 * val bytes = ch.readAll()
 * // ... mutate ...
 * ch.writeAll(mutated)
 * ch.flush()
 * ch.close()
 * ```
 *
 * @see TSource
 * @see TSink
 */
interface TChannel : TSource, TSink

/**
 * In-memory [TChannel] backed by a [ByteArray].
 *
 * The initial [data] is the read content; after [writeAll] or [write],
 * the written bytes can be retrieved via [collect].
 *
 * Read and write operations use separate [Mutex] instances so that
 * concurrent readers and writers do not corrupt each other's state
 * while still allowing maximum parallelism.
 */
class ByteArrayChannel(private val data: ByteArray) : TChannel {
    // --- Read state ---
    private val readMutex = Mutex()
    private var readPos = 0

    // --- Write state ---
    private val writeMutex = Mutex()
    private val chunks = mutableListOf<ByteArray>()

    // -- TSource --

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

    // -- TSink --

    override suspend fun write(buffer: ByteArray, offset: Int, length: Int): Unit = writeMutex.withLock {
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
