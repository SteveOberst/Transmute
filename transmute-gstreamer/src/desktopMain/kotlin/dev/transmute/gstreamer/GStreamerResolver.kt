package dev.transmute.gstreamer

import java.io.File

/**
 * Resolves GStreamer CLI tools on the Desktop/JVM platform.
 *
 * Searches the system PATH for `gst-launch-1.0`, `gst-inspect-1.0`, and
 * `gst-discoverer-1.0`. On Windows, also checks common GStreamer install
 * directories and the `GSTREAMER_1_0_ROOT_*` environment variables.
 */
internal object GStreamerResolver {

    /** `true` when a usable `gst-launch-1.0` binary has been found. */
    val available: Boolean by lazy { resolve(); _available }

    /** Resolved path to `gst-launch-1.0`. */
    val gstLaunchPath: String
        get() {
            resolve()
            return _gstLaunchPath ?: "gst-launch-1.0"
        }

    /** Resolved path to `gst-inspect-1.0`. */
    val gstInspectPath: String
        get() {
            resolve()
            return _gstInspectPath ?: "gst-inspect-1.0"
        }

    /** Resolved path to `gst-discoverer-1.0`. */
    val gstDiscovererPath: String
        get() {
            resolve()
            return _gstDiscovererPath ?: "gst-discoverer-1.0"
        }

    @Volatile private var _available = false
    @Volatile private var _gstLaunchPath: String? = null
    @Volatile private var _gstInspectPath: String? = null
    @Volatile private var _gstDiscovererPath: String? = null
    @Volatile private var resolved = false
    private val lock = Any()

    fun resolve() {
        if (resolved) return
        synchronized(lock) {
            if (resolved) return

            // Try PATH first
            _gstLaunchPath = findBinary("gst-launch-1.0")
            _gstInspectPath = findBinary("gst-inspect-1.0")
            _gstDiscovererPath = findBinary("gst-discoverer-1.0")

            // On Windows, check common GStreamer install locations
            if (_gstLaunchPath == null && isWindows()) {
                for (dir in windowsSearchPaths()) {
                    val launch = File(dir, "gst-launch-1.0.exe")
                    if (launch.exists()) {
                        _gstLaunchPath = launch.absolutePath
                        val inspect = File(dir, "gst-inspect-1.0.exe")
                        if (inspect.exists()) _gstInspectPath = inspect.absolutePath
                        val discoverer = File(dir, "gst-discoverer-1.0.exe")
                        if (discoverer.exists()) _gstDiscovererPath = discoverer.absolutePath
                        break
                    }
                }
            }

            _available = _gstLaunchPath != null && testBinary(_gstLaunchPath!!)
            resolved = true
        }
    }

    /**
     * Check whether a specific GStreamer element/plugin is installed.
     *
     * Uses `gst-inspect-1.0 <element>` and checks the exit code.
     */
    fun hasElement(elementName: String): Boolean {
        if (!available) return false
        val inspectPath = _gstInspectPath ?: return false
        return try {
            val process = ProcessBuilder(inspectPath, elementName)
                .redirectErrorStream(true)
                .start()
            process.inputStream.bufferedReader().readText() // drain
            process.waitFor() == 0
        } catch (_: Exception) {
            false
        }
    }

    // -- internals --

    private fun findBinary(name: String): String? {
        val ext = if (isWindows()) ".exe" else ""
        val fullName = name + ext
        return try {
            val cmd = if (isWindows()) listOf("where", fullName) else listOf("which", fullName)
            val process = ProcessBuilder(cmd)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText().trim()
            if (process.waitFor() == 0 && output.isNotBlank()) {
                output.lines().first().trim()
            } else null
        } catch (_: Exception) {
            null
        }
    }

    private fun testBinary(path: String): Boolean = try {
        val process = ProcessBuilder(path, "--version")
            .redirectErrorStream(true)
            .start()
        process.inputStream.bufferedReader().readText()
        process.waitFor() == 0
    } catch (_: Exception) {
        false
    }

    private fun isWindows(): Boolean =
        System.getProperty("os.name", "").startsWith("Windows", ignoreCase = true)

    private fun windowsSearchPaths(): List<String> = buildList {
        System.getenv("GSTREAMER_1_0_ROOT_MSVC_X86_64")?.let { add("$it\\bin") }
        System.getenv("GSTREAMER_1_0_ROOT_X86_64")?.let { add("$it\\bin") }
        add("C:\\gstreamer\\1.0\\msvc_x86_64\\bin")
        add("C:\\gstreamer\\1.0\\x86_64\\bin")
    }
}
