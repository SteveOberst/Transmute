package dev.transmute.libheif

import java.io.File

/**
 * Resolves libheif CLI tools on the Desktop/JVM platform.
 *
 * Resolution order depends on the configured [installation] mode:
 *
 * - **[Bundled][LibHeifInstallation.Bundled]** (default):
 *   1. Extracted bundled binaries from JAR resources
 *   2. System PATH as fallback
 *   3. Platform-specific defaults:
 *      - **Windows:** MSYS2 (`C:\msys64`), vcpkg, Chocolatey, Scoop
 *      - **macOS:** Homebrew (`/opt/homebrew/bin`), MacPorts (`/opt/local/bin`)
 *      - **Linux:** `/usr/bin`, `/usr/local/bin`, `/opt/local/bin`
 *
 * - **[Custom][LibHeifInstallation.Custom]**:
 *   1. `<home>/bin/heif-dec` (or `heif-convert`)
 *   2. Additional [Custom.searchPaths]
 *
 * - **[System][LibHeifInstallation.System]**:
 *   1. System PATH (`which` / `where`)
 *   2. Platform-specific defaults (same locations as Bundled mode)
 *
 * libheif provides two generations of CLI tools:
 * - **v1.19+**: `heif-dec` (decode) and `heif-enc` (encode)
 * - **Earlier**: `heif-convert` (decode) and `heif-enc` (encode)
 *
 * The resolver checks for both naming conventions.
 */
internal object LibHeifResolver {

    // --- User-configurable fields (set before first access) ---

    /** How libheif binaries should be located. */
    var installation: LibHeifInstallation = LibHeifInstallation.Bundled

    // --- Resolved state ---

    /** `true` when a usable decode tool has been found. */
    val available: Boolean by lazy { resolve(); _available }

    /**
     * Resolved path to `heif-dec` or `heif-convert`.
     *
     * Prefer `heif-dec` (libheif >= 1.19), fall back to `heif-convert`.
     */
    val decoderPath: String
        get() {
            resolve()
            return _decoderPath ?: "heif-dec"
        }

    /**
     * Resolved path to `heif-enc`.
     *
     * Returns `null` if only the decoder was found (encode-only builds
     * of libheif are uncommon but possible).
     */
    val encoderPath: String?
        get() {
            resolve()
            return _encoderPath
        }

    /** `true` when an encoder (`heif-enc`) is available alongside the decoder. */
    val encoderAvailable: Boolean
        get() {
            resolve()
            return _encoderPath != null
        }

    /**
     * Human-readable diagnostic message explaining why libheif was or
     * was not found. Populated after the first resolution attempt.
     */
    var diagnosticMessage: String = ""
        private set

    @Volatile private var _available = false
    @Volatile private var _decoderPath: String? = null
    @Volatile private var _encoderPath: String? = null
    @Volatile private var resolved = false
    private val lock = Any()

    /** Force re-resolution. Useful after changing [installation] at runtime. */
    fun reset() {
        synchronized(lock) {
            resolved = false
            _available = false
            _decoderPath = null
            _encoderPath = null
            diagnosticMessage = ""
        }
    }

    fun resolve() {
        if (resolved) return
        synchronized(lock) {
            if (resolved) return

            val diag = StringBuilder()

            when (val mode = installation) {
                is LibHeifInstallation.Bundled -> resolveBundled(diag)
                is LibHeifInstallation.Custom -> resolveCustom(mode, diag)
                is LibHeifInstallation.System -> resolveSystem(diag)
            }

            _available = _decoderPath != null && testBinary(_decoderPath!!)
            if (_decoderPath != null && !_available) {
                diag.appendLine("[libheif] Binary found at $_decoderPath but version check failed")
            }
            if (!_available) {
                diag.appendLine("[libheif] UNAVAILABLE -- HEIF/HEIC/AVIF codecs will be disabled")
            }
            if (_available && _encoderPath != null) {
                if (!testBinary(_encoderPath!!)) {
                    diag.appendLine("[libheif] Encoder found at $_encoderPath but version check failed -- encoding disabled")
                    _encoderPath = null
                }
            }

            diagnosticMessage = diag.toString().trimEnd()
            resolved = true
        }
    }

    // --- Installation-mode resolvers ---

    private fun resolveBundled(diag: StringBuilder) {
        diag.appendLine("[libheif] Installation mode: Bundled (bundled resources -> system PATH -> platform defaults)")

        // 1. Try extracting / finding bundled libheif from JAR resources
        val bundledBinDir = LibHeifBundleExtractor.extractedBinDir(diag)
        if (bundledBinDir != null) {
            resolveFromDir(bundledBinDir.absolutePath, diag, "bundled extraction")

            // If the bundled binaries exist but can't start (common when the user's cache
            // was created by an older Transmute version missing transitive DLLs/.so files),
            // treat that as a broken cache and self-heal.
            if (_decoderPath != null && !testBinary(_decoderPath!!)) {
                diag.appendLine("[libheif] Bundled extraction present but decoder failed verification -- attempting repair")
                _decoderPath = null
                _encoderPath = null

                // Force re-extraction from current JAR resources.
                val repairedBinDir = LibHeifBundleExtractor.reextractFromJar(diag)
                if (repairedBinDir != null) {
                    resolveFromDir(repairedBinDir.absolutePath, diag, "bundled extraction (repaired)")
                }

                // If it still fails on Windows, fall back to auto-provisioning (MSYS2).
                if (_decoderPath != null && !testBinary(_decoderPath!!)) {
                    diag.appendLine("[libheif] Bundled repair did not yield a working decoder")
                    _decoderPath = null
                    _encoderPath = null
                }
            }
        }

        // 1b. If bundled resources are missing or broken, Windows can still recover via auto-provisioning.
        if (_decoderPath == null && isWindows()) {
            val provisionedBinDir = LibHeifAutoProvisioner.provision(diag)
            if (provisionedBinDir != null) {
                resolveFromDir(provisionedBinDir.absolutePath, diag, "runtime auto-provisioning")
                if (_decoderPath != null && !testBinary(_decoderPath!!)) {
                    diag.appendLine("[libheif] Auto-provisioning produced binaries but version check failed")
                    _decoderPath = null
                    _encoderPath = null
                }
            }
        }

        // 2. Fallback: system PATH
        if (_decoderPath == null) {
            resolveFromSystemPath(diag)
        }

        // 3. Fallback: platform-specific default paths (package managers, well-known locations)
        if (_decoderPath == null) {
            resolvePlatformDefaults(diag)
        }
    }

    private fun resolveCustom(mode: LibHeifInstallation.Custom, diag: StringBuilder) {
        diag.appendLine("[libheif] Installation mode: Custom (home='${mode.home}')")
        // 1. Explicit home directory
        val homeStr = mode.home.toString()
        val binDir = File(homeStr, "bin")
        resolveFromDir(binDir.absolutePath, diag, "custom home '${mode.home}'")

        // Also try the home directory itself (in case user points to bin/ directly)
        if (_decoderPath == null) {
            resolveFromDir(homeStr, diag, "custom home root '${mode.home}'")
        }

        // 2. Additional search paths
        if (_decoderPath == null) {
            for (searchPath in mode.searchPaths) {
                val dirStr = searchPath.toString()
                resolveFromDir(dirStr, diag, "custom search path '$searchPath'")
                if (_decoderPath != null) break
            }
        }
    }

    private fun resolveSystem(diag: StringBuilder) {
        diag.appendLine("[libheif] Installation mode: System (system PATH -> platform defaults)")

        // 1. System PATH
        resolveFromSystemPath(diag)

        // 2. Platform-specific defaults
        if (_decoderPath == null) {
            resolvePlatformDefaults(diag)
        }
    }

    // --- Shared resolution helpers ---

    /**
     * Probes [dirPath] for libheif CLI tools.
     *
     * Checks for `heif-dec` first (libheif >= 1.19), then falls back to
     * `heif-convert` (earlier versions). Also looks for `heif-enc`.
     */
    private fun resolveFromDir(dirPath: String, diag: StringBuilder, source: String) {
        val dir = File(dirPath)
        val ext = if (isWindows()) ".exe" else ""

        // Decoder: prefer heif-dec, fall back to heif-convert
        val dec = File(dir, "heif-dec$ext")
        val convert = File(dir, "heif-convert$ext")
        when {
            dec.exists() -> {
                _decoderPath = dec.absolutePath
                diag.appendLine("[libheif] Found decoder via $source: ${dec.absolutePath}")
            }
            convert.exists() -> {
                _decoderPath = convert.absolutePath
                diag.appendLine("[libheif] Found decoder (heif-convert) via $source: ${convert.absolutePath}")
            }
            else -> {
                diag.appendLine("[libheif] Decoder not found via $source: expected ${dec.absolutePath} or ${convert.absolutePath}")
            }
        }

        // Encoder: heif-enc
        val enc = File(dir, "heif-enc$ext")
        if (enc.exists()) {
            _encoderPath = enc.absolutePath
            diag.appendLine("[libheif] Found encoder via $source: ${enc.absolutePath}")
        } else if (_decoderPath != null) {
            diag.appendLine("[libheif] Encoder not found via $source: expected ${enc.absolutePath}")
        }
    }

    private fun resolveFromSystemPath(diag: StringBuilder) {
        // Decoder
        _decoderPath = findBinary("heif-dec") ?: findBinary("heif-convert")
        if (_decoderPath != null) {
            diag.appendLine("[libheif] Found decoder via system PATH: $_decoderPath")
        } else {
            diag.appendLine("[libheif] heif-dec / heif-convert not found on system PATH")
        }

        // Encoder
        _encoderPath = findBinary("heif-enc")
        if (_encoderPath != null) {
            diag.appendLine("[libheif] Found encoder via system PATH: $_encoderPath")
        } else {
            diag.appendLine("[libheif] heif-enc not found on system PATH")
        }
    }

    private fun resolveFromLinuxDefaults(diag: StringBuilder) {
        for (dir in listOf("/usr/bin", "/usr/local/bin", "/opt/local/bin")) {
            resolveFromDir(dir, diag, "Linux default: $dir")
            if (_decoderPath != null) return
        }
        diag.appendLine("[libheif] Not found in Linux default paths (/usr/bin, /usr/local/bin, /opt/local/bin)")
    }

    /**
     * Dispatches to platform-specific default path searches based on the OS.
     *
     * On Windows, probes MSYS2, vcpkg, Chocolatey and Scoop install locations.
     * On macOS, probes Homebrew and MacPorts.
     * On Linux, probes `/usr/bin`, `/usr/local/bin`, `/opt/local/bin`.
     */
    private fun resolvePlatformDefaults(diag: StringBuilder) {
        val os = System.getProperty("os.name", "").lowercase()
        when {
            os.startsWith("windows") -> resolveFromWindowsDefaults(diag)
            os.startsWith("mac") || os.contains("darwin") -> resolveFromMacDefaults(diag)
            else -> resolveFromLinuxDefaults(diag)
        }
    }

    private fun resolveFromWindowsDefaults(diag: StringBuilder) {
        // MSYS2 installations (most common source of libheif CLI on Windows)
        val msys2Roots = listOfNotNull(
            System.getenv("MSYS2_ROOT"),
            "C:\\msys64",
            "C:\\msys32",
        )
        for (root in msys2Roots) {
            for (env in listOf("ucrt64", "mingw64", "clang64")) {
                val dir = "$root\\$env\\bin"
                if (File(dir).isDirectory) {
                    resolveFromDir(dir, diag, "MSYS2: $root\\$env")
                    if (_decoderPath != null) return
                }
            }
        }

        // vcpkg installations
        val vcpkgRoot = System.getenv("VCPKG_ROOT")
        if (vcpkgRoot != null) {
            resolveFromDir("$vcpkgRoot\\installed\\x64-windows\\tools\\libheif", diag, "vcpkg tools")
            if (_decoderPath != null) return
            resolveFromDir("$vcpkgRoot\\installed\\x64-windows\\bin", diag, "vcpkg bin")
            if (_decoderPath != null) return
        }

        // Chocolatey
        val chocoRoot = System.getenv("ChocolateyInstall") ?: "C:\\ProgramData\\chocolatey"
        if (File(chocoRoot, "bin").isDirectory) {
            resolveFromDir("$chocoRoot\\bin", diag, "Chocolatey")
            if (_decoderPath != null) return
        }

        // Scoop
        val scoopDir = System.getenv("SCOOP") ?: "${System.getProperty("user.home")}\\scoop"
        if (File(scoopDir, "shims").isDirectory) {
            resolveFromDir("$scoopDir\\shims", diag, "Scoop")
            if (_decoderPath != null) return
        }

        diag.appendLine(
            "[libheif] Not found in Windows default paths (MSYS2, vcpkg, Chocolatey, Scoop).\n" +
                "  Install libheif via one of:\n" +
                "    - MSYS2: pacman -S mingw-w64-ucrt-x86_64-libheif\n" +
                "    - vcpkg: vcpkg install libheif[core]:x64-windows\n" +
                "    - Or place heif-dec.exe / heif-enc.exe on your system PATH",
        )
    }

    private fun resolveFromMacDefaults(diag: StringBuilder) {
        for (dir in listOf("/opt/homebrew/bin", "/usr/local/bin", "/opt/local/bin")) {
            resolveFromDir(dir, diag, "macOS default: $dir")
            if (_decoderPath != null) return
        }
        diag.appendLine(
            "[libheif] Not found in macOS default paths.\n" +
                "  Install via: brew install libheif",
        )
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
        val processBuilder = ProcessBuilder(path, "--version")
            .redirectErrorStream(true)
        configureLibHeifProcess(processBuilder, path)
        val process = processBuilder.start()
        process.inputStream.bufferedReader().readText()
        process.waitFor() == 0
    } catch (_: Exception) {
        // Some versions of heif-convert may not support --version;
        // try --help as a fallback
        try {
            val processBuilder = ProcessBuilder(path, "--help")
                .redirectErrorStream(true)
            configureLibHeifProcess(processBuilder, path)
            val process = processBuilder.start()
            process.inputStream.bufferedReader().readText()
            process.waitFor() == 0
        } catch (_: Exception) {
            false
        }
    }

    internal fun configureLibHeifProcess(processBuilder: ProcessBuilder, binaryPath: String) {
        // Make relative file loads deterministic and help some loaders that look at cwd.
        val binFile = runCatching { File(binaryPath) }.getOrNull()
        val binDir = binFile?.parentFile
        if (binDir != null && binDir.isDirectory) {
            processBuilder.directory(binDir)
        }

        // On Unix, bundled/provisioned binaries may ship adjacent shared libraries under ../lib.
        // Ensure the dynamic linker can find them.
        if (!isWindows() && isTransmuteLibHeifCachePath(binaryPath)) {
            val libDir = binDir?.parentFile?.let { File(it, "lib") }
            if (libDir != null && libDir.isDirectory) {
                val key = if (isMac()) "DYLD_LIBRARY_PATH" else "LD_LIBRARY_PATH"
                val env = processBuilder.environment()
                val existing = env[key]
                env[key] = if (existing.isNullOrBlank()) libDir.absolutePath else "${libDir.absolutePath}${File.pathSeparator}$existing"
            }
        }
    }

    private fun isTransmuteLibHeifCachePath(path: String): Boolean {
        val home = System.getProperty("user.home") ?: return false
        val cacheRoot = File(home, ".transmute/libheif").absolutePath
        return normalizePath(path).startsWith(normalizePath(cacheRoot))
    }

    private fun normalizePath(path: String): String =
        path.replace('\\', '/').lowercase()

    private fun isMac(): Boolean {
        val os = System.getProperty("os.name", "").lowercase()
        return os.startsWith("mac") || os.contains("darwin")
    }

    private fun isWindows(): Boolean =
        System.getProperty("os.name", "").startsWith("Windows", ignoreCase = true)
}
