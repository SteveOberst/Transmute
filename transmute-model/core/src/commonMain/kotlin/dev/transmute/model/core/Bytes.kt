package dev.transmute.model.core

import dev.transmute.io.TSource
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ByteArraySerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Serializer for [Bytes] that delegates to the platform `ByteArray` serializer.
 */
object BytesSerializer : KSerializer<Bytes> {
    private val delegate = ByteArraySerializer()
    override val descriptor: SerialDescriptor = delegate.descriptor
    override fun serialize(encoder: Encoder, value: Bytes) = delegate.serialize(encoder, value.data)
    override fun deserialize(decoder: Decoder): Bytes = Bytes(delegate.deserialize(decoder))
}

/**
 * Canonical binary data container for Transmute.
 *
 * `Bytes` wraps a raw `ByteArray` and implements [TSource] so it can be
 * passed directly to any API that accepts a streaming source.  For
 * streaming-only access, prefer [TSource]; use `Bytes` when the full
 * payload needs to reside in memory (e.g. format detection, serialisation).
 */
@Serializable(with = BytesSerializer::class)
class Bytes(val data: ByteArray) : TSource {

    // -- data-container surface -------------------------------------------

    val size: Int get() = data.size
    fun isEmpty(): Boolean = data.isEmpty()
    fun isNotEmpty(): Boolean = data.isNotEmpty()
    operator fun get(index: Int): Byte = data[index]

    // -- TSource implementation -------------------------------------------

    private var position = 0

    override suspend fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (position >= data.size) return -1
        val available = minOf(length, data.size - position)
        data.copyInto(buffer, destinationOffset = offset, startIndex = position, endIndex = position + available)
        position += available
        return available
    }

    override suspend fun readAll(): ByteArray {
        if (position >= data.size) return ByteArray(0)
        val remaining = data.copyOfRange(position, data.size)
        position = data.size
        return remaining
    }

    override fun close() { /* no-op */ }
}

fun ByteArray.asBytes(): Bytes = Bytes(this)

/**
 * Concatenate a list of [BinarySerializable] items into a single [Bytes].
 *
 * This is the shared implementation of the `toBytes()` pattern used by
 * ISO BMFF, EBML, and Ogg raw structures, avoiding copy-pasted
 * concat loops across every format.
 */
fun List<BinarySerializable>.concatToBytes(): Bytes {
    val parts = map { it.toBytes().data }
    val total = parts.sumOf { it.size }
    val out = ByteArray(total)
    var pos = 0
    for (part in parts) { part.copyInto(out, pos); pos += part.size }
    return Bytes(out)
}