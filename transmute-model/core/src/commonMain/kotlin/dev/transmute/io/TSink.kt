package dev.transmute.io

/**
 * A suspending, sequential **write-only** byte sink.
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
