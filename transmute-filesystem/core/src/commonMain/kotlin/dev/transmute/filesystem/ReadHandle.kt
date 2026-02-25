package dev.transmute.filesystem

/**
 * A handle for reading bytes from a file with random-access support.
 *
 * Implementations MUST release underlying resources when [close] is called.
 */
interface ReadHandle : AutoCloseable {

    /**
     * Read up to [length] bytes into [buffer] starting at [offset].
     *
     * @return the number of bytes actually read, or `-1` if end-of-file.
     */
    fun read(buffer: ByteArray, offset: Int = 0, length: Int = buffer.size - offset): Int

    /**
     * Seek to the given absolute [position] in the file.
     */
    fun seek(position: Long)

    /**
     * The current read position.
     */
    fun position(): Long

    /**
     * The total size of the file in bytes.
     */
    fun size(): Long
}
