package dev.transmute.filesystem

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TPathTest {

    @Test
    fun ofParsesUnixAbsolutePath() {
        val path = TPath.of("/usr/local/bin")
        assertEquals("/", path.root)
        assertEquals(listOf("usr", "local", "bin"), path.segments)
        assertTrue(path.isAbsolute)
    }

    @Test
    fun ofParsesWindowsAbsolutePath() {
        val path = TPath.of("C:\\Users\\me\\docs")
        assertEquals("C:\\", path.root)
        assertEquals(listOf("Users", "me", "docs"), path.segments)
        assertTrue(path.isAbsolute)
    }

    @Test
    fun ofParsesRelativePath() {
        val path = TPath.of("src/main/kotlin")
        assertNull(path.root)
        assertEquals(listOf("src", "main", "kotlin"), path.segments)
        assertFalse(path.isAbsolute)
    }

    @Test
    fun ofParsesEmptyPath() {
        val path = TPath.of("")
        assertNull(path.root)
        assertTrue(path.segments.isEmpty())
    }

    @Test
    fun nameReturnsLastSegment() {
        assertEquals("file.txt", TPath.of("/home/user/file.txt").name)
    }

    @Test
    fun nameReturnsEmptyForEmptyPath() {
        assertEquals("", TPath.of("").name)
    }

    @Test
    fun extensionReturnsFileExtension() {
        assertEquals("txt", TPath.of("/file.txt").extension)
    }

    @Test
    fun extensionReturnsEmptyWhenNoDot() {
        assertEquals("", TPath.of("/Makefile").extension)
    }

    @Test
    fun extensionReturnsLastPart() {
        assertEquals("gz", TPath.of("/archive.tar.gz").extension)
    }

    @Test
    fun stemReturnsNameWithoutExtension() {
        assertEquals("file", TPath.of("/file.txt").stem)
    }

    @Test
    fun stemReturnsFullNameWhenNoExtension() {
        assertEquals("Makefile", TPath.of("/Makefile").stem)
    }

    @Test
    fun parentReturnsParentPath() {
        val path = TPath.of("/a/b/c")
        val parent = path.parent
        assertEquals(listOf("a", "b"), parent?.segments)
        assertEquals("/", parent?.root)
    }

    @Test
    fun parentReturnsRootForSingleSegment() {
        val path = TPath.of("/etc")
        val parent = path.parent
        assertTrue(parent?.segments?.isEmpty() == true)
        assertEquals("/", parent?.root)
    }

    @Test
    fun parentReturnsNullForEmpty() {
        assertNull(TPath.of("").parent)
    }

    @Test
    fun parentReturnsNullForRelativeSingleSegment() {
        assertNull(TPath.of("file.txt").parent)
    }

    @Test
    fun divAppendsChildSegment() {
        val path = TPath.of("/home") / "user" / "docs"
        assertEquals(listOf("home", "user", "docs"), path.segments)
        assertEquals("/", path.root)
    }

    @Test
    fun resolveAppendsRelativePath() {
        val base = TPath.of("/home/user")
        val rel = TPath.of("docs/file.txt")
        val resolved = base.resolve(rel)
        assertEquals(listOf("home", "user", "docs", "file.txt"), resolved.segments)
    }

    @Test
    fun resolveReturnsAbsolutePathAsIs() {
        val base = TPath.of("/home/user")
        val abs = TPath.of("/etc/config")
        assertEquals(abs, base.resolve(abs))
    }

    @Test
    fun normalizeRemovesDot() {
        val path = TPath.of("/a/./b/./c")
        assertEquals(listOf("a", "b", "c"), path.normalize().segments)
    }

    @Test
    fun normalizeResolvesDotDot() {
        val path = TPath.of("/a/b/../c")
        assertEquals(listOf("a", "c"), path.normalize().segments)
    }

    @Test
    fun normalizePreservesDotDotInRelativePath() {
        val path = TPath.of("../a")
        assertEquals(listOf("..", "a"), path.normalize().segments)
    }

    @Test
    fun toStringUnixAbsolute() {
        assertEquals("/usr/local/bin", TPath.of("/usr/local/bin").toString())
    }

    @Test
    fun toStringRelative() {
        assertEquals("src/main", TPath.of("src/main").toString())
    }

    @Test
    fun toStringWindowsAbsolute() {
        assertEquals("C:\\Users/me", TPath.of("C:\\Users\\me").toString())
    }
}
