package dev.transmute.io

/**
 * A suspending, sequential **read-only** byte source.
 *
 * `TSource` is the Transmute abstraction for streaming input -- files,
 * network responses, in-memory buffers, etc.  All reads are suspending
 * to support non-blocking I/O on every platform.
 *
 * Note: implementations are allowed to perform blocking I/O internally
 * (e.g. when wrapping a synchronous filesystem handle).  Callers that
 * require strict non-blocking behavior should provide an implementation
 * that is natively asynchronous for their platform.
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
