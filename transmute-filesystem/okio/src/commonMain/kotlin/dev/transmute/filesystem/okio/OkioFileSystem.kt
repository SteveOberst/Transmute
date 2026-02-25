@file:Suppress("unused")

package dev.transmute.filesystem.okio

import dev.transmute.filesystem.*
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.buffer
import okio.use

/**
 * [TransmuteFileSystem] implementation backed by Okio's [FileSystem].
 *
 * Supports all KMP targets that Okio supports (JVM, Android, iOS, JS/Wasm).
 *
 * ```kotlin
 * val fs = OkioFileSystem(FileSystem.SYSTEM)
 * val content = fs.read(TPath.of("/etc/hosts"))
 * ```
 *
 * @param delegate The Okio [FileSystem] to delegate to.
 */
class OkioFileSystem(
    private val delegate: FileSystem,
) : TransmuteFileSystem {

    // ── Path conversion ────────────────────────────────────────

    private fun TPath.toOkio(): okio.Path = toString().toPath()

    private fun okio.Path.toTPath(): TPath = TPath.of(toString())

    // ── Metadata ───────────────────────────────────────────────

    override fun exists(path: TPath): Boolean =
        delegate.exists(path.toOkio())

    override fun metadata(path: TPath): FileMetadata =
        metadataOrNull(path) ?: throw FileNotFoundException("Not found: $path", path)

    override fun metadataOrNull(path: TPath): FileMetadata? {
        val okPath = path.toOkio()
        if (!delegate.exists(okPath)) return null
        val meta = delegate.metadata(okPath)
        return FileMetadata(
            size = meta.size ?: 0L,
            lastModifiedMillis = meta.lastModifiedAtMillis,
            createdMillis = meta.createdAtMillis,
            isRegularFile = meta.isRegularFile,
            isDirectory = meta.isDirectory,
            isSymlink = meta.symlinkTarget != null,
        )
    }

    // ── Bulk read / write ──────────────────────────────────────

    override fun read(path: TPath): ByteArray {
        val okPath = path.toOkio()
        if (!delegate.exists(okPath)) throw FileNotFoundException("Not found: $path", path)
        return delegate.read(okPath) { readByteArray() }
    }

    override fun write(path: TPath, data: ByteArray, mode: WriteMode) {
        val okPath = path.toOkio()
        when (mode) {
            WriteMode.Create -> {
                if (delegate.exists(okPath)) {
                    throw FileAlreadyExistsException("Already exists: $path", path)
                }
                delegate.write(okPath) { write(data) }
            }
            WriteMode.Overwrite -> {
                delegate.write(okPath) { write(data) }
            }
            WriteMode.Append -> {
                delegate.appendingSink(okPath).buffer().use { sink ->
                    sink.write(data)
                }
            }
        }
    }

    // ── Streaming / random-access ──────────────────────────────

    override fun openRead(path: TPath): ReadHandle {
        val okPath = path.toOkio()
        if (!delegate.exists(okPath)) throw FileNotFoundException("Not found: $path", path)
        val handle = delegate.openReadOnly(okPath)
        return OkioReadHandle(handle)
    }

    override fun openWrite(path: TPath, mode: WriteMode): WriteHandle {
        val okPath = path.toOkio()
        when (mode) {
            WriteMode.Create -> {
                if (delegate.exists(okPath)) {
                    throw FileAlreadyExistsException("Already exists: $path", path)
                }
            }
            else -> { /* allow */ }
        }
        val handle = delegate.openReadWrite(okPath)
        if (mode == WriteMode.Append) {
            handle.resize(handle.size()) // ensure we start at the end
        }
        return OkioWriteHandle(handle, appendMode = mode == WriteMode.Append)
    }

    // ── Directory operations ───────────────────────────────────

    override fun list(path: TPath): List<TPath> {
        val okPath = path.toOkio()
        if (!delegate.exists(okPath)) throw FileNotFoundException("Not found: $path", path)
        val meta = delegate.metadata(okPath)
        if (!meta.isDirectory) throw NotDirectoryException("Not a directory: $path", path)
        return delegate.list(okPath).map { it.toTPath() }
    }

    override fun createDirectory(path: TPath, recursive: Boolean) {
        val okPath = path.toOkio()
        if (recursive) {
            delegate.createDirectories(okPath)
        } else {
            delegate.createDirectory(okPath)
        }
    }

    // ── Delete ─────────────────────────────────────────────────

    override fun delete(path: TPath, recursive: Boolean) {
        val okPath = path.toOkio()
        if (!delegate.exists(okPath)) throw FileNotFoundException("Not found: $path", path)
        if (recursive) {
            delegate.deleteRecursively(okPath)
        } else {
            delegate.delete(okPath)
        }
    }

    // ── Copy / Move ────────────────────────────────────────────

    override fun copy(source: TPath, target: TPath, overwrite: Boolean) {
        val src = source.toOkio()
        val tgt = target.toOkio()
        if (!delegate.exists(src)) throw FileNotFoundException("Not found: $source", source)
        if (!overwrite && delegate.exists(tgt)) {
            throw FileAlreadyExistsException("Target already exists: $target", target)
        }
        delegate.copy(src, tgt)
    }

    override fun move(source: TPath, target: TPath, overwrite: Boolean) {
        val src = source.toOkio()
        val tgt = target.toOkio()
        if (!delegate.exists(src)) throw FileNotFoundException("Not found: $source", source)
        if (!overwrite && delegate.exists(tgt)) {
            throw FileAlreadyExistsException("Target already exists: $target", target)
        }
        delegate.atomicMove(src, tgt)
    }
}

// ── OkioReadHandle ─────────────────────────────────────────────

private class OkioReadHandle(
    private val handle: okio.FileHandle,
) : ReadHandle {
    private var pos = 0L
    private val fileSize = handle.size()

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (pos >= fileSize) return -1
        val read = handle.read(pos, buffer, offset, length)
        if (read > 0) pos += read
        return read
    }

    override fun seek(position: Long) {
        pos = position.coerceIn(0, fileSize)
    }

    override fun position(): Long = pos
    override fun size(): Long = fileSize

    override fun close() {
        handle.close()
    }
}

// ── OkioWriteHandle ────────────────────────────────────────────

private class OkioWriteHandle(
    private val handle: okio.FileHandle,
    appendMode: Boolean,
) : WriteHandle {
    private var pos = if (appendMode) handle.size() else 0L

    override fun write(buffer: ByteArray, offset: Int, length: Int) {
        handle.write(pos, buffer, offset, length)
        pos += length
    }

    override fun flush() {
        handle.flush()
    }

    override fun close() {
        handle.close()
    }
}
