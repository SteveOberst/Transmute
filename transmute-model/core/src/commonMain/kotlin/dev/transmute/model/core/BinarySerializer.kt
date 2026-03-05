@file:Suppress("unused")

package dev.transmute.model.core

import kotlinx.serialization.BinaryFormat
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule

/**
 * A kotlinx [BinaryFormat] that produces the canonical on-disk binary
 * representation of [BinarySerializable] types.
 *
 * **Encoding** delegates entirely to [BinarySerializable.toBytes] -
 * the serialization descriptor and encoder are not used; the value
 * must implement [BinarySerializable].
 *
 * **Decoding** is not supported and will throw
 * [UnsupportedOperationException].  Format-specific parsers should
 * be used instead.
 *
 * ```kotlin
 * val pngBytes: ByteArray = BinarySerializer.encodeToByteArray(Png.serializer(), pngFile)
 * // pngBytes is a valid PNG file
 * ```
 */
object BinarySerializer : BinaryFormat {

  override val serializersModule: SerializersModule = EmptySerializersModule()

  /**
   * Encodes [value] to its canonical binary representation.
   *
   * @throws IllegalArgumentException if [value] does not implement [BinarySerializable].
   */
  override fun <T> encodeToByteArray(serializer: SerializationStrategy<T>, value: T): ByteArray {
    require(value is BinarySerializable) {
      "BinarySerializer only supports types that implement BinarySerializable, " +
        "got ${value!!::class.simpleName}"
    }
    return value.toBytes().data
  }

  /**
   * Decoding from raw bytes is not supported by [BinarySerializer].
   *
   * Use format-specific parsers to construct model instances from binary data.
   *
   * @throws UnsupportedOperationException always.
   */
  override fun <T> decodeFromByteArray(deserializer: DeserializationStrategy<T>, bytes: ByteArray): T = throw UnsupportedOperationException(
    "BinarySerializer does not support decoding. " +
      "Use format-specific parsers to construct model instances from binary data.",
  )
}
