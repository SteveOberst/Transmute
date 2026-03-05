package dev.transmute.io

import dev.transmute.filesystem.ReadHandle
import dev.transmute.filesystem.TPath
import dev.transmute.filesystem.TransmuteFileSystem
import dev.transmute.filesystem.WriteHandle
import dev.transmute.filesystem.WriteMode
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Filesystem-backed [TSource] using a synchronous [ReadHandle]. */
class PathSource(private val handle: ReadHandle) : TSource {
  private val mutex = Mutex()

  override suspend fun read(buffer: ByteArray, offset: Int, length: Int): Int = mutex.withLock {
    handle.read(buffer, offset, length)
  }

  override suspend fun readAll(): ByteArray = mutex.withLock {
    val pos = handle.position()
    val size = handle.size()
    val remainingLong = size - pos
    if (remainingLong <= 0L) return@withLock ByteArray(0)
    require(remainingLong <= Int.MAX_VALUE.toLong()) { "Source too large to materialize in memory: $remainingLong bytes" }

    val remaining = remainingLong.toInt()
    val out = ByteArray(remaining)
    var offset = 0
    while (offset < remaining) {
      val n = handle.read(out, offset, remaining - offset)
      if (n <= 0) break
      offset += n
    }
    if (offset == remaining) out else out.copyOf(offset)
  }

  override fun close() {
    handle.close()
  }
}

/** Filesystem-backed [TSink] using a synchronous [WriteHandle]. */
class PathSink(private val handle: WriteHandle) : TSink {
  private val mutex = Mutex()

  override suspend fun write(buffer: ByteArray, offset: Int, length: Int): Unit = mutex.withLock {
    handle.write(buffer, offset, length)
  }

  override suspend fun flush(): Unit = mutex.withLock {
    handle.flush()
  }

  override fun close() {
    handle.close()
  }
}

/** Filesystem-backed [TChannel] built from separate read/write handles. */
class PathChannel(private val readHandle: ReadHandle, private val writeHandle: WriteHandle) : TChannel {
  private val readMutex = Mutex()
  private val writeMutex = Mutex()

  override suspend fun read(buffer: ByteArray, offset: Int, length: Int): Int = readMutex.withLock {
    readHandle.read(buffer, offset, length)
  }

  override suspend fun readAll(): ByteArray = readMutex.withLock {
    val pos = readHandle.position()
    val size = readHandle.size()
    val remainingLong = size - pos
    if (remainingLong <= 0L) return@withLock ByteArray(0)
    require(remainingLong <= Int.MAX_VALUE.toLong()) { "Source too large to materialize in memory: $remainingLong bytes" }
    val remaining = remainingLong.toInt()
    val out = ByteArray(remaining)
    var off = 0
    while (off < remaining) {
      val n = readHandle.read(out, off, remaining - off)
      if (n <= 0) break
      off += n
    }
    if (off == remaining) out else out.copyOf(off)
  }

  override suspend fun write(buffer: ByteArray, offset: Int, length: Int): Unit = writeMutex.withLock {
    writeHandle.write(buffer, offset, length)
  }

  override suspend fun flush(): Unit = writeMutex.withLock {
    writeHandle.flush()
  }

  override fun close() {
    runCatching { readHandle.close() }
    runCatching { writeHandle.close() }
  }
}

/** Open a [TSource] for [path]. */
fun TransmuteFileSystem.source(path: TPath): TSource = PathSource(openRead(path))

/** Open this path as a [TSource] via [fs]. */
fun TPath.asSource(fs: TransmuteFileSystem): TSource = fs.source(this)

/** Open a [TSink] for [path]. */
fun TransmuteFileSystem.sink(path: TPath, mode: WriteMode = WriteMode.Overwrite): TSink = PathSink(openWrite(path, mode))

/** Open a [TChannel] for [path]. */
fun TransmuteFileSystem.channel(path: TPath, mode: WriteMode = WriteMode.Overwrite): TChannel =
  PathChannel(openRead(path), openWrite(path, mode))
