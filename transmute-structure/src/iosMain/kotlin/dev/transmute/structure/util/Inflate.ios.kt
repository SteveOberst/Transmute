@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package dev.transmute.structure.util

import kotlinx.cinterop.*
import platform.zlib.*

actual fun inflateBytes(data: ByteArray): ByteArray = memScoped {
    val stream = alloc<z_stream>()
    stream.zalloc = null
    stream.zfree = null
    stream.opaque = null
    stream.avail_in = 0u
    stream.next_in = null

    if (inflateInit(stream.ptr) != Z_OK) {
        error("zlib inflateInit failed")
    }

    try {
        val chunks = mutableListOf<ByteArray>()
        val bufSize = 4096
        val outBuf = allocArray<UByteVar>(bufSize)

        data.usePinned { pinned ->
            stream.next_in = pinned.addressOf(0).reinterpret()
            stream.avail_in = data.size.toUInt()

            do {
                stream.next_out = outBuf
                stream.avail_out = bufSize.toUInt()

                val ret = inflate(stream.ptr, Z_NO_FLUSH)
                if (ret != Z_OK && ret != Z_STREAM_END && ret != Z_BUF_ERROR) {
                    error("zlib inflate failed: $ret")
                }

                val produced = bufSize - stream.avail_out.toInt()
                if (produced > 0) {
                    val chunk = ByteArray(produced)
                    for (i in 0 until produced) {
                        chunk[i] = outBuf[i].toByte()
                    }
                    chunks.add(chunk)
                }
            } while (ret != Z_STREAM_END)
        }

        val total = chunks.sumOf { it.size }
        val result = ByteArray(total)
        var pos = 0
        for (chunk in chunks) { chunk.copyInto(result, pos); pos += chunk.size }
        result
    } finally {
        inflateEnd(stream.ptr)
    }
}
