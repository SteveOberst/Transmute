@file:Suppress("unused")

package dev.transmute.model.structure

import dev.transmute.model.core.Bytes
import dev.transmute.model.core.RawMediaStructure

/**
 * Writes [RawMediaStructure] instances to a destination.
 *
 * Every [RawMediaStructure] already implements [toBytes()][dev.transmute.model.core.BinarySerializable.toBytes]
 * for in-memory serialisation. A [StructureSink] abstracts the *destination*:
 * a file on disk, a network stream, an in-memory buffer, etc.
 *
 * ```
 * val sink = BytesSink()
 * sink.write(pngStructure)
 * val raw: Bytes = sink.collect()
 * ```
 *
 * Implementations that target I/O should be `suspend` - the interface
 * uses a suspending [write] to accommodate both blocking and non-blocking
 * backends.
 */
interface StructureSink {
    /** Write [structure] to this sink. */
    suspend fun write(structure: RawMediaStructure)

    /** Flush any buffered data. No-op for non-buffered sinks. */
    suspend fun flush() {}

    /** Release any resources held by this sink. */
    suspend fun close() {}
}

/**
 * Simple in-memory sink that collects the serialised bytes.
 */
class BytesSink : StructureSink {
    private var result: Bytes? = null

    override suspend fun write(structure: RawMediaStructure) {
        result = structure.toBytes()
    }

    /**
     * Returns the bytes written to this sink.
     *
     * @throws IllegalStateException if [write] has not been called.
     */
    fun collect(): Bytes = result ?: error("No structure has been written to this sink")

    /**
     * Returns the bytes written, or `null` if [write] has not been called.
     */
    fun collectOrNull(): Bytes? = result
}
