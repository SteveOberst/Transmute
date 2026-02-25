@file:Suppress("unused")

package dev.transmute.model.view

/**
 * Platform-agnostic seekable byte channel for surgical read/write
 * access to binary data.
 *
 * Implementations can wrap a file handle, memory-mapped buffer,
 * byte array, or any other random-access byte store.  The view
 * module uses this to perform in-place edits — seeking to the
 * byte offset of a changed chunk and overwriting only those bytes.
 *
 * Obtain a channel from platform-specific factories:
 * - JVM/Android: wrap `java.io.RandomAccessFile` or `java.nio.channels.SeekableByteChannel`
 * - iOS: wrap `NSFileHandle`
 * - Testing: use [ByteArrayChannel]
 */
interface SeekableByteChannel : AutoCloseable {

    /** Total number of bytes currently in the channel. */
    val size: Long

    /** Current read/write cursor position (byte offset from start). */
    var position: Long

    /**
     * Read up to [length] bytes into [dst] starting at [offset].
     *
     * Returns the number of bytes actually read, or `-1` at end-of-channel.
     * Advances [position] by the number of bytes read.
     */
    fun read(dst: ByteArray, offset: Int = 0, length: Int = dst.size): Int

    /**
     * Write [length] bytes from [src] starting at [offset] to the
     * channel at the current [position].
     *
     * If [position] is beyond the current [size], the gap is
     * zero-filled.  Advances [position] by [length].
     */
    fun write(src: ByteArray, offset: Int = 0, length: Int = src.size)

    /**
     * Truncate the channel to [newSize] bytes.
     *
     * If [newSize] < [size], data beyond [newSize] is discarded.
     * If [position] > [newSize], position is set to [newSize].
     */
    fun truncate(newSize: Long)

    /** Release any underlying resources. */
    override fun close()
}

/**
 * In-memory [SeekableByteChannel] backed by a growable byte array.
 *
 * Useful for testing and for building output buffers without
 * touching the file system.
 *
 * ```kotlin
 * val channel = ByteArrayChannel(existingPngBytes)
 * pngFile.editStreaming(channel) { ihdr = ihdr.copy(width = 1920u) }
 * val result: ByteArray = channel.toByteArray()
 * ```
 */
class ByteArrayChannel(initialData: ByteArray = ByteArray(0)) : SeekableByteChannel {

    private var buf: ByteArray = initialData.copyOf()
    private var _size: Int = initialData.size
    private var _position: Int = 0
    private var closed = false

    override val size: Long get() = _size.toLong()

    override var position: Long
        get() = _position.toLong()
        set(value) {
            require(value >= 0) { "position must be >= 0, was $value" }
            _position = value.toInt()
        }

    override fun read(dst: ByteArray, offset: Int, length: Int): Int {
        check(!closed) { "Channel is closed" }
        if (_position >= _size) return -1
        val n = minOf(length, _size - _position)
        buf.copyInto(dst, offset, _position, _position + n)
        _position += n
        return n
    }

    override fun write(src: ByteArray, offset: Int, length: Int) {
        check(!closed) { "Channel is closed" }
        val end = _position + length
        if (end > buf.size) {
            buf = buf.copyOf(maxOf(buf.size * 2, end))
        }
        src.copyInto(buf, _position, offset, offset + length)
        _position = end
        if (end > _size) _size = end
    }

    override fun truncate(newSize: Long) {
        check(!closed) { "Channel is closed" }
        val ns = newSize.toInt()
        if (ns < _size) {
            _size = ns
            if (_position > ns) _position = ns
        }
    }

    override fun close() {
        closed = true
    }

    /** Snapshot of the channel contents (0 until [size]). */
    fun toByteArray(): ByteArray = buf.copyOfRange(0, _size)
}
