package dev.transmute.filesystem

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InMemoryFileSystemTest {

    private fun fs() = InMemoryFileSystem()

    private val testPath = TPath.of("/test.txt")
    private val testData = "Hello, Transmute!".encodeToByteArray()

    // ── exists / metadata ──────────────────────────────────────

    @Test
    fun existsReturnsFalseForMissingFile() {
        assertFalse(fs().exists(testPath))
    }

    @Test
    fun existsReturnsTrueAfterWrite() {
        val f = fs()
        f.write(testPath, testData)
        assertTrue(f.exists(testPath))
    }

    @Test
    fun metadataReturnsCorrectSize() {
        val f = fs()
        f.write(testPath, testData)
        val meta = f.metadata(testPath)
        assertEquals(testData.size.toLong(), meta.size)
        assertTrue(meta.isRegularFile)
        assertFalse(meta.isDirectory)
    }

    @Test
    fun metadataThrowsForMissingFile() {
        assertFailsWith<FileNotFoundException> {
            fs().metadata(testPath)
        }
    }

    @Test
    fun metadataOrNullReturnsNullForMissing() {
        assertNull(fs().metadataOrNull(testPath))
    }

    @Test
    fun metadataForDirectory() {
        val f = fs()
        val dir = TPath.of("/mydir")
        f.createDirectory(dir)
        val meta = f.metadata(dir)
        assertTrue(meta.isDirectory)
        assertFalse(meta.isRegularFile)
    }

    // ── read / write ───────────────────────────────────────────

    @Test
    fun readReturnsWrittenData() {
        val f = fs()
        f.write(testPath, testData)
        assertContentEquals(testData, f.read(testPath))
    }

    @Test
    fun readThrowsForMissingFile() {
        assertFailsWith<FileNotFoundException> {
            fs().read(testPath)
        }
    }

    @Test
    fun writeCreateFailsIfExists() {
        val f = fs()
        f.write(testPath, testData)
        assertFailsWith<FileAlreadyExistsException> {
            f.write(testPath, testData, WriteMode.Create)
        }
    }

    @Test
    fun writeOverwriteReplacesContent() {
        val f = fs()
        f.write(testPath, testData)
        val newData = "Updated".encodeToByteArray()
        f.write(testPath, newData, WriteMode.Overwrite)
        assertContentEquals(newData, f.read(testPath))
    }

    @Test
    fun writeAppendAddsToExisting() {
        val f = fs()
        f.write(testPath, "Hello".encodeToByteArray())
        f.write(testPath, " World".encodeToByteArray(), WriteMode.Append)
        assertEquals("Hello World", f.read(testPath).decodeToString())
    }

    @Test
    fun writeCreatesParentDirectories() {
        val f = fs()
        val deep = TPath.of("/a/b/c/file.txt")
        f.write(deep, testData)
        assertTrue(f.exists(deep))
        assertTrue(f.exists(TPath.of("/a/b/c")))
    }

    // ── Streaming read / write ─────────────────────────────────

    @Test
    fun openReadReturnsCorrectContent() {
        val f = fs()
        f.write(testPath, testData)
        val handle = f.openRead(testPath)
        val buf = ByteArray(testData.size)
        val read = handle.read(buf)
        assertEquals(testData.size, read)
        assertContentEquals(testData, buf)
        handle.close()
    }

    @Test
    fun openReadSupportsSeek() {
        val f = fs()
        f.write(testPath, testData)
        val handle = f.openRead(testPath)
        handle.seek(7)
        assertEquals(7L, handle.position())
        val buf = ByteArray(10)
        val read = handle.read(buf)
        assertTrue(read > 0)
        handle.close()
    }

    @Test
    fun openReadReportsSize() {
        val f = fs()
        f.write(testPath, testData)
        f.openRead(testPath).use { handle ->
            assertEquals(testData.size.toLong(), handle.size())
        }
    }

    @Test
    fun openReadReturnsMinusOneAtEof() {
        val f = fs()
        f.write(testPath, ByteArray(2) { it.toByte() })
        f.openRead(testPath).use { handle ->
            val buf = ByteArray(10)
            val first = handle.read(buf)
            assertEquals(2, first)
            assertEquals(-1, handle.read(buf))
        }
    }

    @Test
    fun openWriteCreatesFile() {
        val f = fs()
        f.openWrite(testPath).use { handle ->
            handle.write(testData)
        }
        assertContentEquals(testData, f.read(testPath))
    }

    // ── Directory operations ───────────────────────────────────

    @Test
    fun createDirectoryAndList() {
        val f = fs()
        val dir = TPath.of("/mydir")
        f.createDirectory(dir)
        assertTrue(f.exists(dir))
        f.write(dir / "a.txt", "a".encodeToByteArray())
        f.write(dir / "b.txt", "b".encodeToByteArray())
        val children = f.list(dir)
        assertEquals(2, children.size)
        assertTrue(children.any { it.name == "a.txt" })
        assertTrue(children.any { it.name == "b.txt" })
    }

    @Test
    fun createDirectoryRecursive() {
        val f = fs()
        val deep = TPath.of("/a/b/c")
        f.createDirectory(deep, recursive = true)
        assertTrue(f.exists(deep))
        assertTrue(f.exists(TPath.of("/a")))
        assertTrue(f.exists(TPath.of("/a/b")))
    }

    @Test
    fun listThrowsForMissingDirectory() {
        assertFailsWith<FileNotFoundException> {
            fs().list(TPath.of("/missing"))
        }
    }

    @Test
    fun listThrowsForFile() {
        val f = fs()
        f.write(testPath, testData)
        assertFailsWith<NotDirectoryException> {
            f.list(testPath)
        }
    }

    // ── Delete ─────────────────────────────────────────────────

    @Test
    fun deleteRemovesFile() {
        val f = fs()
        f.write(testPath, testData)
        f.delete(testPath)
        assertFalse(f.exists(testPath))
    }

    @Test
    fun deleteThrowsForMissing() {
        assertFailsWith<FileNotFoundException> {
            fs().delete(testPath)
        }
    }

    @Test
    fun deleteNonEmptyDirectoryRequiresRecursive() {
        val f = fs()
        val dir = TPath.of("/mydir")
        f.createDirectory(dir)
        f.write(dir / "file.txt", testData)
        assertFailsWith<IllegalStateException> {
            f.delete(dir)
        }
    }

    @Test
    fun deleteRecursiveRemovesDirectoryAndContents() {
        val f = fs()
        val dir = TPath.of("/mydir")
        f.createDirectory(dir)
        f.write(dir / "file.txt", testData)
        f.delete(dir, recursive = true)
        assertFalse(f.exists(dir))
    }

    // ── Copy / Move ────────────────────────────────────────────

    @Test
    fun copyCopiesFileContent() {
        val f = fs()
        f.write(testPath, testData)
        val target = TPath.of("/copy.txt")
        f.copy(testPath, target)
        assertContentEquals(testData, f.read(target))
        assertTrue(f.exists(testPath)) // source still exists
    }

    @Test
    fun copyFailsIfTargetExistsAndNoOverwrite() {
        val f = fs()
        f.write(testPath, testData)
        val target = TPath.of("/other.txt")
        f.write(target, "existing".encodeToByteArray())
        assertFailsWith<FileAlreadyExistsException> {
            f.copy(testPath, target)
        }
    }

    @Test
    fun copyOverwriteReplacesTarget() {
        val f = fs()
        f.write(testPath, testData)
        val target = TPath.of("/other.txt")
        f.write(target, "existing".encodeToByteArray())
        f.copy(testPath, target, overwrite = true)
        assertContentEquals(testData, f.read(target))
    }

    @Test
    fun moveMovesFileContent() {
        val f = fs()
        f.write(testPath, testData)
        val target = TPath.of("/moved.txt")
        f.move(testPath, target)
        assertContentEquals(testData, f.read(target))
        assertFalse(f.exists(testPath)) // source removed
    }

    @Test
    fun moveFailsIfTargetExistsAndNoOverwrite() {
        val f = fs()
        f.write(testPath, testData)
        val target = TPath.of("/other.txt")
        f.write(target, "existing".encodeToByteArray())
        assertFailsWith<FileAlreadyExistsException> {
            f.move(testPath, target)
        }
    }
}
