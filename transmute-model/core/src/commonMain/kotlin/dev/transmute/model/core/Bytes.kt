package dev.transmute.model.core

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ByteArraySerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.jvm.JvmInline

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
 * This avoids passing raw `ByteArray` through every public API surface while staying
 * multiplatform (unlike `ByteBuffer` or `InputStream`).
 */
@JvmInline
@Serializable(with = BytesSerializer::class)
value class Bytes(val data: ByteArray) {
    val size: Int get() = data.size
    fun isEmpty(): Boolean = data.isEmpty()
    fun isNotEmpty(): Boolean = data.isNotEmpty()
    operator fun get(index: Int): Byte = data[index]
}

fun ByteArray.asBytes(): Bytes = Bytes(this)
