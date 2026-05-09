package dev.transmute.filesystem.okio

import dev.transmute.filesystem.*
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.*

class OkioFileSystemTest {

    private fun create(): Pair<OkioFileSystem, FakeFileSystem> {
        val fake = FakeFileSystem()
        fake.emulateUnix()
        return OkioFileSystem(fake) to fake
    }

    private val testPath = TPath.of("/test.txt")
    private val testData = "Hello, Transmute!".encodeToByteArray()

    // -- exists / metadata ---

    @Test
    fun existsReturnsFalseForMissingFile() {
        val (fs, _) = create()
        assertFalse(fs.exists(testPath))
    }

    @Test
    fun existsReturnsTrueAfterWrite() {
        val (fs, _) = create()
        fs.write(testPath, testData)
        assertTrue(fs.exists(testPath))
    }

    @Test
    fun metadataReturnsCorrectSize() {
        val (fs, _) = create()
        fs.write(testPath, testData)
        val meta = fs.metadata(testPath)
        assertEquals(testData.size.toLong(), meta.size)
        assertTrue(meta.isRegularFile)
        assertFalse(meta.isDirectory)
    }

    @Test
    fun metadataThrowsForMissingFile() {
        val (fs, _) = create()
        assertFailsWith<FileNotFoundException> {
            fs.metadata(testPath)
        }
    }

    @Test
    fun metadataOrNullReturnsNullForMissing() {
        val (fs, _) = create()
        assertNull(fs.metadataOrNull(testPath))
    }

    @Test
    fun metadataForDirectory() {
        val (fs, _) = create()
        val dir = TPath.of("/mydir")
        fs.createDirectory(dir)
        val meta = fs.metadata(dir)
        assertTrue(meta.isDirectory)
        assertFalse(meta.isRegularFile)
    }

    // -- read / write ---

    @Test
    fun readReturnsWrittenData() {
        val (fs, _) = create()
        fs.write(testPath, testData)
        assertContentEquals(testData, fs.read(testPath))
    }

    @Test
    fun readThrowsForMissingFile() {
        val (fs, _) = create()
        assertFailsWith<FileNotFoundException> {
            fs.read(testPath)
        }
    }

    @Test
    fun writeCreateFailsIfExists() {
        val (fs, _) = create()
        fs.write(testPath, testData)
        assertFailsWith<FileAlreadyExistsException> {
            fs.write(testPath, testData, WriteMode.Create)
        }
    }

    @Test
    fun writeOverwriteReplacesContent() {
        val (fs, _) = create()
        fs.write(testPath, testData)
        val newData = "Updated".encodeToByteArray()
        fs.write(testPath, newData, WriteMode.Overwrite)
        assertContentEquals(newData, fs.read(testPath))
    }

    @Test
    fun writeAppendAddsToExisting() {
        val (fs, _) = create()
        fs.write(testPath, "Hello".encodeToByteArray())
        fs.write(testPath, " World".encodeToByteArray(), WriteMode.Append)
        assertEquals("Hello World", fs.read(testPath).decodeToString())
    }

    // -- Streaming read / write ---

    @Test
    fun openReadReturnsCorrectContent() {
        val (fs, _) = create()
        fs.write(testPath, testData)
        fs.openRead(testPath).use { handle ->
            val buf = ByteArray(testData.size)
            val read = handle.read(buf)
            assertEquals(testData.size, read)
            assertContentEquals(testData, buf)
        }
    }

    @Test
    fun openReadSupportsSeek() {
        val (fs, _) = create()
        fs.write(testPath, testData)
        fs.openRead(testPath).use { handle ->
            handle.seek(7)
            assertEquals(7L, handle.position())
            val buf = ByteArray(10)
            val read = handle.read(buf)
            assertTrue(read > 0)
        }
    }

    @Test
    fun openReadReportsSize() {
        val (fs, _) = create()
        fs.write(testPath, testData)
        fs.openRead(testPath).use { handle ->
            assertEquals(testData.size.toLong(), handle.size())
        }
    }

    @Test
    fun openReadReturnsMinusOneAtEof() {
        val (fs, _) = create()
        fs.write(testPath, ByteArray(2) { it.toByte() })
        fs.openRead(testPath).use { handle ->
            val buf = ByteArray(10)
            val first = handle.read(buf)
            assertEquals(2, first)
            assertEquals(-1, handle.read(buf))
        }
    }

    @Test
    fun openWriteCreatesFile() {
        val (fs, _) = create()
        fs.openWrite(testPath).use { handle ->
            handle.write(testData)
        }
        assertContentEquals(testData, fs.read(testPath))
    }

    // -- Directory operations ---

    @Test
    fun createDirectoryAndList() {
        val (fs, _) = create()
        val dir = TPath.of("/mydir")
        fs.createDirectory(dir)
        assertTrue(fs.exists(dir))
        fs.write(dir / "a.txt", "a".encodeToByteArray())
        fs.write(dir / "b.txt", "b".encodeToByteArray())
        val children = fs.list(dir)
        assertEquals(2, children.size)
        assertTrue(children.any { it.name == "a.txt" })
        assertTrue(children.any { it.name == "b.txt" })
    }

    @Test
    fun createDirectoryRecursive() {
        val (fs, _) = create()
        val deep = TPath.of("/a/b/c")
        fs.createDirectory(deep, recursive = true)
        assertTrue(fs.exists(deep))
        assertTrue(fs.exists(TPath.of("/a")))
        assertTrue(fs.exists(TPath.of("/a/b")))
    }

    @Test
    fun listThrowsForMissingDirectory() {
        val (fs, _) = create()
        assertFailsWith<FileNotFoundException> {
            fs.list(TPath.of("/missing"))
        }
    }

    @Test
    fun listThrowsForFile() {
        val (fs, _) = create()
        fs.write(testPath, testData)
        assertFailsWith<NotDirectoryException> {
            fs.list(testPath)
        }
    }

    // -- Delete ---

    @Test
    fun deleteRemovesFile() {
        val (fs, _) = create()
        fs.write(testPath, testData)
        fs.delete(testPath)
        assertFalse(fs.exists(testPath))
    }

    @Test
    fun deleteThrowsForMissing() {
        val (fs, _) = create()
        assertFailsWith<FileNotFoundException> {
            fs.delete(testPath)
        }
    }

    @Test
    fun deleteRecursiveRemovesDirectoryAndContents() {
        val (fs, _) = create()
        val dir = TPath.of("/mydir")
        fs.createDirectory(dir)
        fs.write(dir / "file.txt", testData)
        fs.delete(dir, recursive = true)
        assertFalse(fs.exists(dir))
    }

    // -- Copy / Move ---

    @Test
    fun copyCopiesFileContent() {
        val (fs, _) = create()
        fs.write(testPath, testData)
        val target = TPath.of("/copy.txt")
        fs.copy(testPath, target)
        assertContentEquals(testData, fs.read(target))
        assertTrue(fs.exists(testPath)) // source still exists
    }

    @Test
    fun copyFailsIfTargetExistsAndNoOverwrite() {
        val (fs, _) = create()
        fs.write(testPath, testData)
        val target = TPath.of("/other.txt")
        fs.write(target, "existing".encodeToByteArray())
        assertFailsWith<FileAlreadyExistsException> {
            fs.copy(testPath, target)
        }
    }

    @Test
    fun copyOverwriteReplacesTarget() {
        val (fs, _) = create()
        fs.write(testPath, testData)
        val target = TPath.of("/other.txt")
        fs.write(target, "existing".encodeToByteArray())
        fs.copy(testPath, target, overwrite = true)
        assertContentEquals(testData, fs.read(target))
    }

    @Test
    fun moveMovesFileContent() {
        val (fs, _) = create()
        fs.write(testPath, testData)
        val target = TPath.of("/moved.txt")
        fs.move(testPath, target)
        assertContentEquals(testData, fs.read(target))
        assertFalse(fs.exists(testPath)) // source removed
    }

    @Test
    fun moveFailsIfTargetExistsAndNoOverwrite() {
        val (fs, _) = create()
        fs.write(testPath, testData)
        val target = TPath.of("/other.txt")
        fs.write(target, "existing".encodeToByteArray())
        assertFailsWith<FileAlreadyExistsException> {
            fs.move(testPath, target)
        }
    }
}
