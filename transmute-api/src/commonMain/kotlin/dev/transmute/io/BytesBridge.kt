package dev.transmute.io

import dev.transmute.model.core.Bytes

/**
 * Create a [TSource] that reads from this [Bytes] instance.
 *
 * The returned source is backed by a [ByteArraySource] with
 * Mutex-guarded reads for concurrency safety.
 *
 * ```kotlin
 * val bytes = Bytes(rawData)
 * val src: TSource = bytes.asSource()
 * val all = src.readAll()
 * src.close()
 * ```
 */
fun Bytes.asSource(): TSource = ByteArraySource(data)

/**
 * Create a [TChannel] seeded with this [Bytes] instance.
 *
 * The initial data is available for reading; new data can be
 * written via [TSink.write] / [TSink.writeAll] and later
 * collected with [ByteArrayChannel.collect].
 *
 * Both reads and writes are Mutex-guarded for concurrency safety.
 *
 * ```kotlin
 * val bytes = Bytes(rawData)
 * val ch: TChannel = bytes.asChannel()
 * val original = ch.readAll()
 * ch.writeAll(transformed)
 * ch.close()
 * ```
 */
fun Bytes.asChannel(): ByteArrayChannel = ByteArrayChannel(data)
