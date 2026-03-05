package dev.transmute.structure.util

import java.util.zip.Inflater

actual fun inflateBytes(data: ByteArray): ByteArray {
  val inflater = Inflater()
  try {
    inflater.setInput(data)
    val chunks = mutableListOf<ByteArray>()
    val buf = ByteArray(4096)
    while (!inflater.finished()) {
      val n = inflater.inflate(buf)
      if (n == 0 && inflater.needsInput()) break
      chunks.add(buf.copyOfRange(0, n))
    }
    val total = chunks.sumOf { it.size }
    val out = ByteArray(total)
    var pos = 0
    for (chunk in chunks) {
      chunk.copyInto(out, pos)
      pos += chunk.size
    }
    return out
  } finally {
    inflater.end()
  }
}
