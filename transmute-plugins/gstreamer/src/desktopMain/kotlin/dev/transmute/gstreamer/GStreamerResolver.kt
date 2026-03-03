package dev.transmute.gstreamer

import dev.transmute.filesystem.TPath
import java.io.File

/**
 * Resolves GStreamer CLI tools on the Desktop/JVM platform.
 *
 * Resolution order depends on the configured [installation] mode:
 *
 * - **[Bundled][GStreamerInstallation.Bundled]** (default):
 *   1. Extracted bundled binaries from JAR resources
 *   2. System PATH as fallback
 *   3. Windows-specific defaults as fallback
 *
 * - **[Custom][GStreamerInstallation.Custom]**:
 *   1. `<home>/bin/gst-launch-1.0`
 *   2. Additional [Custom.searchPaths]
 *
 * - **[System][GStreamerInstallation.System]**:
 *   1. System PATH (`which` / `where`)
 *   2. Windows-specific defaults
 *
 * Diagnostic information from the resolution attempt is always collected
 * in [diagnosticMessage] and can be logged via the plugin logger.
 */
internal object GStreamerResolver {

    // --- User-configurable fields (set before first access) ---

    /** How GStreamer binaries should be located. */
    var installation: GStreamerInstallation = GStreamerInstallation.Bundled

    // --- Resolved state ---

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

    /**
     * Human-readable diagnostic message explaining why GStreamer was or
     * was not found. Populated after the first resolution attempt.
     */
    var diagnosticMessage: String = ""
        private set

    @Volatile private var _available = false
    @Volatile private var _gstLaunchPath: String? = null
    @Volatile private var _gstInspectPath: String? = null
    @Volatile private var _gstDiscovererPath: String? = null
    @Volatile private var resolved = false
    private val lock = Any()

    /**
     * Force re-resolution. Useful after changing [installation] at runtime.
     */
    fun reset() {
        synchronized(lock) {
            resolved = false
            _available = false
            _gstLaunchPath = null
            _gstInspectPath = null
            _gstDiscovererPath = null
            diagnosticMessage = ""
        }
    }

    fun resolve() {
        if (resolved) return
        synchronized(lock) {
            if (resolved) return

            val diag = StringBuilder()

            when (val mode = installation) {
                is GStreamerInstallation.Bundled -> resolveBundled(diag)
                is GStreamerInstallation.Custom -> resolveCustom(mode, diag)
                is GStreamerInstallation.System -> resolveSystem(diag)
            }

            _available = _gstLaunchPath != null && testBinary(_gstLaunchPath!!)
            if (_gstLaunchPath != null && !_available) {
                diag.appendLine("[GStreamer] Binary found at $_gstLaunchPath but --version check failed")
            }
            if (!_available) {
                diag.appendLine("[GStreamer] UNAVAILABLE -- GStreamer codecs will be disabled")
            }

            diagnosticMessage = diag.toString().trimEnd()
            resolved = true
        }
    }

    // --- Installation-mode resolvers ---

    private fun resolveBundled(diag: StringBuilder) {
        diag.appendLine("[GStreamer] Installation mode: Bundled (bundled resources -> system PATH -> platform defaults)")

        // 1. Try extracting / finding bundled GStreamer from JAR resources
        val bundledBinDir = GStreamerBundleExtractor.extractedBinDir(diag)
        if (bundledBinDir != null) {
            resolveFromDir(bundledBinDir.absolutePath, diag, "bundled extraction")
        }

        // 2. Fallback: system PATH
        if (_gstLaunchPath == null) {
            resolveFromSystemPath(diag)
        }

        // 3. Fallback: Linux default paths
        if (_gstLaunchPath == null && !isWindows()) {
            resolveFromLinuxDefaults(diag)
        }

        // 4. Fallback: Windows defaults
        if (_gstLaunchPath == null && isWindows()) {
            resolveFromWindowsDefaults(diag)
        }
    }

    private fun resolveCustom(mode: GStreamerInstallation.Custom, diag: StringBuilder) {
        diag.appendLine("[GStreamer] Installation mode: Custom (home='${mode.home}')")
        // 1. Explicit home directory
        val homeStr = mode.home.toString()
        val binDir = File(homeStr, "bin")
        resolveFromDir(binDir.absolutePath, diag, "custom home '${mode.home}'")

        // 2. Additional search paths
        if (_gstLaunchPath == null) {
            for (searchPath in mode.searchPaths) {
                val dirStr = searchPath.toString()
                resolveFromDir(dirStr, diag, "custom search path '$searchPath'")
                if (_gstLaunchPath != null) break
            }
        }
    }

    private fun resolveSystem(diag: StringBuilder) {
        diag.appendLine("[GStreamer] Installation mode: System (system PATH -> platform defaults)")

        // 1. System PATH
        resolveFromSystemPath(diag)

        // 2. Linux defaults
        if (_gstLaunchPath == null && !isWindows()) {
            resolveFromLinuxDefaults(diag)
        }

        // 3. Windows defaults
        if (_gstLaunchPath == null && isWindows()) {
            resolveFromWindowsDefaults(diag)
        }
    }

    // --- Shared resolution helpers ---

    private fun resolveFromDir(dirPath: String, diag: StringBuilder, source: String) {
        val dir = File(dirPath)
        val ext = if (isWindows()) ".exe" else ""
        val launch = File(dir, "gst-launch-1.0$ext")
        if (launch.exists()) {
            _gstLaunchPath = launch.absolutePath
            _gstInspectPath = File(dir, "gst-inspect-1.0$ext").takeIf { it.exists() }?.absolutePath
            _gstDiscovererPath = File(dir, "gst-discoverer-1.0$ext").takeIf { it.exists() }?.absolutePath
            diag.appendLine("[GStreamer] Found via $source: ${launch.absolutePath}")
        } else {
            diag.appendLine("[GStreamer] Not found via $source: expected ${launch.absolutePath}")
        }
    }

    private fun resolveFromSystemPath(diag: StringBuilder) {
        _gstLaunchPath = findBinary("gst-launch-1.0")
        _gstInspectPath = findBinary("gst-inspect-1.0")
        _gstDiscovererPath = findBinary("gst-discoverer-1.0")
        if (_gstLaunchPath != null) {
            diag.appendLine("[GStreamer] Found via system PATH: $_gstLaunchPath")
        } else {
            diag.appendLine("[GStreamer] gst-launch-1.0 not found on system PATH")
        }
    }

    private fun resolveFromLinuxDefaults(diag: StringBuilder) {
        for (dir in listOf("/usr/bin", "/usr/local/bin", "/opt/local/bin")) {
            resolveFromDir(dir, diag, "Linux default: $dir")
            if (_gstLaunchPath != null) return
        }
        diag.appendLine("[GStreamer] Not found in Linux default paths (/usr/bin, /usr/local/bin, /opt/local/bin)")
    }

    private fun resolveFromWindowsDefaults(diag: StringBuilder) {
        for (dir in windowsSearchPaths()) {
            val launch = File(dir, "gst-launch-1.0.exe")
            if (launch.exists()) {
                _gstLaunchPath = launch.absolutePath
                val inspect = File(dir, "gst-inspect-1.0.exe")
                if (inspect.exists()) _gstInspectPath = inspect.absolutePath
                val discoverer = File(dir, "gst-discoverer-1.0.exe")
                if (discoverer.exists()) _gstDiscovererPath = discoverer.absolutePath
                diag.appendLine("[GStreamer] Found via Windows default path: ${launch.absolutePath}")
                return
            }
        }
        diag.appendLine("[GStreamer] Not found in Windows default paths")
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
