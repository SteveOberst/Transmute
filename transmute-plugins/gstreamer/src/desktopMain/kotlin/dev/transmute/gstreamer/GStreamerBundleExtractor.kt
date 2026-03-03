package dev.transmute.gstreamer

import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Extracts and manages bundled GStreamer binaries on Desktop/JVM.
 *
 * On first use, GStreamer CLI tools and required plugins are extracted from
 * JAR classpath resources to a persistent cache directory under the user's home.
 * Subsequent uses reuse the cached extraction.
 *
 * The cache directory is `~/.transmute/gstreamer/<version>/`.
 *
 * ### Resource layout (staged at build time)
 *
 * Before building a distribution, run:
 * ```
 * ./gradlew :transmute-plugins:gstreamer:stageGStreamerDesktop
 * ```
 * This downloads the official GStreamer release and stages the binaries into
 * `build/gstreamer-desktop/`, which is wired as a `desktopMain` resource root.
 * The resulting classpath layout expected by this extractor is:
 * ```
 * /gstreamer/<platform>/manifest.txt
 * /gstreamer/<platform>/bin/gst-launch-1.0[.exe]
 * /gstreamer/<platform>/bin/gst-inspect-1.0[.exe]
 * /gstreamer/<platform>/lib/...
 * ```
 *
 * Where `<platform>` is `linux-x86_64`, `windows-x86_64`, or `macos-x86_64` /
 * `macos-aarch64`. Linux is not bundled; GStreamer must be installed via the
 * system package manager on Linux.
 */
internal object GStreamerBundleExtractor {

    /** Version marker - must match GSTREAMER_VERSION used by stageGStreamerDesktop. */
    private const val BUNDLE_VERSION = "1.26.4"

    private val cacheDir: File by lazy {
        val home = System.getProperty("user.home")
        File(home, ".transmute/gstreamer/$BUNDLE_VERSION").also { it.mkdirs() }
    }

    /**
     * Returns the path to the `bin/` directory containing `gst-launch-1.0`, or
     * `null` if no bundled GStreamer is available for this platform.
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
            diag.appendLine("[GStreamer] Bundled: unsupported platform ($os / $arch) -- skipping bundle extraction")
            return null
        }
        diag.appendLine("[GStreamer] Bundled: detected platform '$platform', cache dir: ${cacheDir.absolutePath}")

        val binDir = File(cacheDir, "bin")
        val ext = if (platform.startsWith("windows")) ".exe" else ""
        val launchBinary = File(binDir, "gst-launch-1.0$ext")

        if (launchBinary.exists()) {
            diag.appendLine("[GStreamer] Bundled: cache hit -- using pre-extracted binaries at ${binDir.absolutePath}")
            return binDir
        }

        diag.appendLine("[GStreamer] Bundled: binary not cached, attempting JAR resource extraction...")
        return if (extractBundle(platform, cacheDir, diag)) {
            diag.appendLine("[GStreamer] Bundled: extraction complete -> ${binDir.absolutePath}")
            binDir
        } else {
            null
        }
    }

    /**
     * Extract the bundled GStreamer resources for [platform] into [targetDir].
     *
     * @return `true` if extraction succeeded and `gst-launch-1.0` is present.
     */
    private fun extractBundle(platform: String, targetDir: File, diag: StringBuilder): Boolean {
        val resourcePrefix = "/gstreamer/$platform/"
        val manifest = javaClass.getResourceAsStream("${resourcePrefix}manifest.txt")
            ?: run {
                diag.appendLine("[GStreamer] Bundled: no manifest at classpath '${resourcePrefix}manifest.txt' -- no bundled GStreamer available for '$platform'")
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

        diag.appendLine("[GStreamer] Bundled: extracted $count files to ${targetDir.absolutePath}")
        val ext = if (platform.startsWith("windows")) ".exe" else ""
        return File(targetDir, "bin/gst-launch-1.0$ext").exists()
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
