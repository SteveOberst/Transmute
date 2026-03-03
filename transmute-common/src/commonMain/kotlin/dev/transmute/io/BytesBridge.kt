package dev.transmute.io

import dev.transmute.model.core.Bytes

/** Create a [TSource] that reads from this [Bytes] instance. */
fun Bytes.asSource(): TSource = ByteArraySource(data)

/** Create a [TChannel] seeded with this [Bytes] instance. */
fun Bytes.asChannel(): ByteArrayChannel = ByteArrayChannel(data)
