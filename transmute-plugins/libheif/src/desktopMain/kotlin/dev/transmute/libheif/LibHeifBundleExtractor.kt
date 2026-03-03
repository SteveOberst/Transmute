package dev.transmute.libheif

import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Extracts and manages bundled libheif binaries on Desktop/JVM.
 *
 * On first use, libheif CLI tools are extracted from JAR classpath resources
 * to a persistent cache directory under the user's home. Subsequent uses
 * reuse the cached extraction.
 *
 * The cache directory is `~/.transmute/libheif/<version>/`.
 *
 * ### Resource layout (staged at build time)
 *
 * Before building a distribution, libheif binaries should be staged into
 * `build/libheif-desktop/`, which is wired as a `desktopMain` resource root.
 * The resulting classpath layout expected by this extractor is:
 * ```
 * /libheif/<platform>/manifest.txt
 * /libheif/<platform>/bin/heif-dec[.exe]
 * /libheif/<platform>/bin/heif-enc[.exe]
 * /libheif/<platform>/lib/...
 * ```
 *
 * Where `<platform>` is `linux-x86_64`, `windows-x86_64`, `windows-aarch64`,
 * `macos-x86_64`, or `macos-aarch64`. On Linux, libheif should be installed
 * via the system package manager; bundled extraction is available but optional.
 */
internal object LibHeifBundleExtractor {

    /** Version marker -- must match the libheif version used during staging. */
    private const val BUNDLE_VERSION = "1.21.2"

    private val cacheDir: File by lazy {
        val home = System.getProperty("user.home")
        File(home, ".transmute/libheif/$BUNDLE_VERSION").also { it.mkdirs() }
    }

    /**
     * Deletes the current cache directory for this [BUNDLE_VERSION].
     *
     * Used to self-heal stale/partial extractions after upgrading the bundled
     * files without changing the version string.
     */
    fun invalidateCache(diag: StringBuilder, reason: String) {
        diag.appendLine("[libheif] Bundled: invalidating cache (${cacheDir.absolutePath}) -- $reason")
        try {
            cacheDir.deleteRecursively()
        } catch (_: Exception) {
            // best-effort
        }
        cacheDir.mkdirs()
    }

    /**
     * Forces extraction from JAR resources by first clearing the cache.
     */
    fun reextractFromJar(diag: StringBuilder): File? {
        val platform = detectPlatform() ?: return null
        invalidateCache(diag, "forced re-extraction")
        val binDir = File(cacheDir, "bin")
        diag.appendLine("[libheif] Bundled: re-extracting JAR resources for '$platform' -> ${cacheDir.absolutePath}")
        return if (extractBundle(platform, cacheDir, diag)) binDir else null
    }

    /**
     * Returns the path to the `bin/` directory containing `heif-dec`/`heif-enc`,
     * or `null` if no bundled libheif is available for this platform.
     *
     * Extraction happens lazily on the first call and is skipped if the cache
     * directory already contains the binary. Resolution steps and results are
     * appended to [diag] for INFO-level logging by the caller.
     */
    fun extractedBinDir(diag: StringBuilder): File? {
        val platform = detectPlatform()
        if (platform == null) {
            val os   = System.getProperty("os.name", "unknown")
            val arch = System.getProperty("os.arch", "unknown")
            diag.appendLine("[libheif] Bundled: unsupported platform ($os / $arch) -- skipping bundle extraction")
            return null
        }
        diag.appendLine("[libheif] Bundled: detected platform '$platform', cache dir: ${cacheDir.absolutePath}")

        val binDir = File(cacheDir, "bin")
        val ext = if (platform.startsWith("windows")) ".exe" else ""

        // Check for heif-dec first (1.19+), then heif-convert (legacy)
        val decoderBinary = File(binDir, "heif-dec$ext")
        val legacyBinary = File(binDir, "heif-convert$ext")

        if (decoderBinary.exists() || legacyBinary.exists()) {
            diag.appendLine("[libheif] Bundled: cache hit -- using pre-extracted binaries at ${binDir.absolutePath}")
            return binDir
        }

        diag.appendLine("[libheif] Bundled: binary not cached, attempting JAR resource extraction...")
        if (extractBundle(platform, cacheDir, diag)) {
            diag.appendLine("[libheif] Bundled: extraction complete -> ${binDir.absolutePath}")
            return binDir
        }

        // JAR resources not available -- try runtime auto-provisioning (Windows only; MSYS2 CDN)
        if (platform.startsWith("windows")) {
            diag.appendLine("[libheif] Bundled: JAR resources not available, attempting runtime auto-provisioning...")
            val provisionedBinDir = LibHeifAutoProvisioner.provision(diag)
            if (provisionedBinDir != null) {
                return provisionedBinDir
            }
        }

        return null
    }

    /**
     * Extract the bundled libheif resources for [platform] into [targetDir].
     *
     * @return `true` if extraction succeeded and a decoder binary is present.
     */
    private fun extractBundle(platform: String, targetDir: File, diag: StringBuilder): Boolean {
        val resourcePrefix = "/libheif/$platform/"
        val manifest = javaClass.getResourceAsStream("${resourcePrefix}manifest.txt")
            ?: run {
                diag.appendLine("[libheif] Bundled: no manifest at classpath '${resourcePrefix}manifest.txt' -- no bundled libheif available for '$platform'")
                return false
            }

        var count = 0
        manifest.bufferedReader().useLines { lines ->
            for (relativePath in lines) {
                if (relativePath.isBlank()) continue
                val resource: InputStream = javaClass.getResourceAsStream("$resourcePrefix$relativePath")
                    ?: continue

                val target = File(targetDir, relativePath)
                target.parentFile?.mkdirs()
                resource.use { input ->
                    Files.copy(input, target.toPath(), StandardCopyOption.REPLACE_EXISTING)
                }

                // Make binaries executable on Unix
                if (relativePath.startsWith("bin/") && !relativePath.endsWith(".exe")) {
                    target.setExecutable(true, false)
                }
                count++
            }
        }

        diag.appendLine("[libheif] Bundled: extracted $count files to ${targetDir.absolutePath}")
        val ext = if (platform.startsWith("windows")) ".exe" else ""
        return File(targetDir, "bin/heif-dec$ext").exists() ||
               File(targetDir, "bin/heif-convert$ext").exists()
    }

    private fun detectPlatform(): String? {
        val os = System.getProperty("os.name", "").lowercase()
        val arch = System.getProperty("os.arch", "").lowercase()

        val osName = when {
            os.startsWith("windows") -> "windows"
            os.startsWith("linux") -> "linux"
            os.startsWith("mac") || os.contains("darwin") -> "macos"
            else -> return null
        }

        val archName = when {
            arch == "amd64" || arch == "x86_64" -> "x86_64"
            arch == "aarch64" || arch == "arm64" -> "aarch64"
            else -> return null
        }

        return "$osName-$archName"
    }
}
