@file:Suppress("unused")

package dev.transmute.filesystem

/**
 * In-memory filesystem implementation for testing.
 *
 * Stores files and directories in a map. Supports all [TransmuteFileSystem]
 * operations without any platform dependencies.
 *
 * ```kotlin
 * val mem = InMemoryFileSystem()
 * mem.write(TPath.of("/hello.txt"), "Hello".encodeToByteArray())
 * val data = mem.read(TPath.of("/hello.txt"))
 * ```
 */
class InMemoryFileSystem : TransmuteFileSystem {

    private sealed class Node {
        data class File(var data: ByteArray) : Node()
        data class Directory(val children: MutableSet<String> = mutableSetOf()) : Node()
    }

    private val nodes = mutableMapOf<String, Node>()

    init {
        // Root directory always exists
        nodes["/"] = Node.Directory()
    }

    private fun key(path: TPath): String = path.normalize().toString().let {
        if (it.isEmpty()) "/" else it
    }

    private fun parentKey(path: TPath): String? {
        val p = path.normalize().parent ?: return null
        return key(p)
    }

    // -- Metadata -----------------------------------------------

    override fun exists(path: TPath): Boolean = nodes.containsKey(key(path))

    override fun metadata(path: TPath): FileMetadata =
        metadataOrNull(path) ?: throw FileNotFoundException("Not found: $path", path)

    override fun metadataOrNull(path: TPath): FileMetadata? {
        return when (val node = nodes[key(path)]) {
            is Node.File -> FileMetadata(
                size = node.data.size.toLong(),
                isRegularFile = true,
            )
            is Node.Directory -> FileMetadata(
                size = 0,
                isDirectory = true,
            )
            null -> null
        }
    }

    // -- Bulk read / write --------------------------------------

    override fun read(path: TPath): ByteArray {
        val node = nodes[key(path)]
            ?: throw FileNotFoundException("Not found: $path", path)
        return when (node) {
            is Node.File -> node.data.copyOf()
            is Node.Directory -> throw IllegalStateException("Is a directory: $path")
        }
    }

    override fun write(path: TPath, data: ByteArray, mode: WriteMode) {
        val k = key(path)
        val existing = nodes[k]
        when (mode) {
            WriteMode.Create -> {
                if (existing != null) throw FileAlreadyExistsException("Already exists: $path", path)
                ensureParent(path)
                nodes[k] = Node.File(data.copyOf())
            }
            WriteMode.Overwrite -> {
                ensureParent(path)
                nodes[k] = Node.File(data.copyOf())
            }
            WriteMode.Append -> {
                ensureParent(path)
                val prev = (existing as? Node.File)?.data ?: ByteArray(0)
                nodes[k] = Node.File(prev + data)
            }
        }
        // Register as child of parent
        val pk = parentKey(path)
        if (pk != null) {
            (nodes[pk] as? Node.Directory)?.children?.add(path.normalize().name)
        }
    }

    // -- Streaming / random-access ------------------------------

    override fun openRead(path: TPath): ReadHandle {
        val data = read(path)
        return InMemoryReadHandle(data)
    }

    override fun openWrite(path: TPath, mode: WriteMode): WriteHandle {
        return InMemoryWriteHandle(this, path, mode)
    }

    // -- Directory operations -----------------------------------

    override fun list(path: TPath): List<TPath> {
        val node = nodes[key(path)]
            ?: throw FileNotFoundException("Not found: $path", path)
        if (node !is Node.Directory) throw NotDirectoryException("Not a directory: $path", path)
        return node.children.sorted().map { path / it }
    }

    override fun createDirectory(path: TPath, recursive: Boolean) {
        val normalized = path.normalize()
        if (recursive) {
            // Create all parents first
            val segments = normalized.segments
            for (i in segments.indices) {
                val partial = TPath(segments.subList(0, i + 1), normalized.root)
                val pk = key(partial)
                if (!nodes.containsKey(pk)) {
                    nodes[pk] = Node.Directory()
                    val ppk = parentKey(partial)
                    if (ppk != null) {
                        (nodes[ppk] as? Node.Directory)?.children?.add(partial.name)
                    }
                }
            }
        } else {
            val k = key(normalized)
            if (nodes.containsKey(k)) return // already exists
            val pk = parentKey(normalized)
            if (pk != null && !nodes.containsKey(pk)) {
                throw FileNotFoundException("Parent not found: ${normalized.parent}", normalized.parent)
            }
            nodes[k] = Node.Directory()
            if (pk != null) {
                (nodes[pk] as? Node.Directory)?.children?.add(normalized.name)
            }
        }
    }

    // -- Delete -------------------------------------------------

    override fun delete(path: TPath, recursive: Boolean) {
        val k = key(path)
        val node = nodes[k]
            ?: throw FileNotFoundException("Not found: $path", path)
        if (node is Node.Directory && node.children.isNotEmpty() && !recursive) {
            throw IllegalStateException("Directory not empty: $path")
        }
        if (node is Node.Directory && recursive) {
            // Delete children first
            for (child in node.children.toList()) {
                delete(path / child, recursive = true)
            }
        }
        nodes.remove(k)
        val pk = parentKey(path)
        if (pk != null) {
            (nodes[pk] as? Node.Directory)?.children?.remove(path.normalize().name)
        }
    }

    // -- Copy / Move --------------------------------------------

    override fun copy(source: TPath, target: TPath, overwrite: Boolean) {
        if (!overwrite && exists(target)) {
            throw FileAlreadyExistsException("Target already exists: $target", target)
        }
        val data = read(source)
        write(target, data, WriteMode.Overwrite)
    }

    override fun move(source: TPath, target: TPath, overwrite: Boolean) {
        copy(source, target, overwrite)
        delete(source)
    }

    // -- Internal helpers ---------------------------------------

    private fun ensureParent(path: TPath) {
        val p = path.normalize().parent ?: return
        val pk = key(p)
        if (!nodes.containsKey(pk)) {
            createDirectory(p, recursive = true)
        }
    }
}

// -- In-memory ReadHandle ---------------------------------------

private class InMemoryReadHandle(private val data: ByteArray) : ReadHandle {
    private var pos = 0L

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (pos >= data.size) return -1
        val available = minOf(length, (data.size - pos).toInt())
        data.copyInto(buffer, offset, pos.toInt(), pos.toInt() + available)
        pos += available
        return available
    }

    override fun seek(position: Long) {
        pos = position.coerceIn(0, data.size.toLong())
    }

    override fun position(): Long = pos
    override fun size(): Long = data.size.toLong()
    override fun close() { /* no-op */ }
}

// -- In-memory WriteHandle --------------------------------------

private class InMemoryWriteHandle(
    private val fs: InMemoryFileSystem,
    private val path: TPath,
    private val mode: WriteMode,
) : WriteHandle {
    private val buffer = mutableListOf<Byte>()

    override fun write(buffer: ByteArray, offset: Int, length: Int) {
        for (i in offset until offset + length) {
            this.buffer.add(buffer[i])
        }
    }

    override fun flush() {
        fs.write(path, buffer.toByteArray(), mode)
    }

    override fun close() {
        flush()
    }
}
