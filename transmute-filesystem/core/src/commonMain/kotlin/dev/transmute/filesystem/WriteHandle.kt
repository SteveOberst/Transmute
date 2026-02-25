package dev.transmute.filesystem

/**
 * A handle for writing bytes to a file.
 *
 * Implementations MUST release underlying resources when [close] is called.
 */
interface WriteHandle : AutoCloseable {

    /**
     * Write [length] bytes from [buffer] starting at [offset].
     */
    fun write(buffer: ByteArray, offset: Int = 0, length: Int = buffer.size - offset)

    /**
     * Flush any buffered data to the underlying storage.
     */
    fun flush()
}
