package dev.transmute.core

import kotlin.jvm.JvmInline

/**
 * Canonical binary data container for Transmute.
 *
 * This avoids passing raw `ByteArray` through every public API surface while staying
 * multiplatform (unlike `ByteBuffer` or `InputStream`).
 */
@JvmInline
value class Bytes(val data: ByteArray) {
  val size: Int get() = data.size
  fun isEmpty(): Boolean = data.isEmpty()
  fun isNotEmpty(): Boolean = data.isNotEmpty()
  operator fun get(index: Int): Byte = data[index]
}

fun ByteArray.asBytes(): Bytes = Bytes(this)
