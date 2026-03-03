package dev.transmute.structure.util

/**
 * Decompress zlib-wrapped data (RFC 1950: 2-byte header + DEFLATE payload + 4-byte checksum).
 *
 * Used for PNG iCCP (compressed ICC profile) and zTXt (compressed text) chunks.
 *
 * @param data the zlib-compressed byte array
 * @return the decompressed byte array
 * @throws Exception if decompression fails
 */
expect fun inflateBytes(data: ByteArray): ByteArray
