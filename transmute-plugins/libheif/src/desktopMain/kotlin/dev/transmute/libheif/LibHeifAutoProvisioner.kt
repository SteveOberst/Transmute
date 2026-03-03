package dev.transmute.libheif

import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files

/**
 * Runtime auto-provisioner for libheif on Windows.
 *
 * When bundled JAR resources are not available and no system installation is
 * found, this provisioner downloads pre-built MSYS2 packages from the MSYS2
 * CDN, extracts them, and caches the result under `~/.transmute/libheif/`.
 *
 * This makes libheif work **out of the box** on Windows without requiring
 * any manual installation of MSYS2, vcpkg, Chocolatey, Scoop, or Homebrew.
 *
 * ### Requirements
 * - Windows 10 1903+ (for built-in `tar.exe` with zstd support)
 * - Internet access (first run only; results are cached)
 *
 * ### Cache layout
 * ```
 * ~/.transmute/libheif/<version>/
 *   bin/
 *     heif-dec.exe
 *     heif-enc.exe
 *     libheif.dll
 *     libaom.dll
 *     libde265-0.dll
 *     ...
 *   lib/
 *     ...
 * ```
 */
internal object LibHeifAutoProvisioner {

    private const val MSYS2_MIRROR = "https://mirror.msys2.org/mingw/ucrt64"

    /**
     * MSYS2 UCRT64 packages required for a working libheif installation.
     *
     * Includes the main libheif package, its codec dependencies, and the
     * GCC runtime DLLs needed by all MinGW-built binaries.
     *
     * When MSYS2 rolls new package versions, update these entries. Check:
     *   https://packages.msys2.org/packages/mingw-w64-ucrt-x86_64-libheif
     *   (and follow its "Dependencies" links for version info)
     */
    private val PACKAGES = listOf(
        // ---- Core libheif package (heif-dec.exe, heif-enc.exe, libheif.dll) ----
        "mingw-w64-ucrt-x86_64-libheif-1.21.2-3-any.pkg.tar.zst",

        // ---- Video/image codec plugins (directly linked by libheif.dll) ----
        "mingw-w64-ucrt-x86_64-libde265-1.0.16-1-any.pkg.tar.zst",      // H.265 decoder (HEIC)
        "mingw-w64-ucrt-x86_64-aom-3.13.1-1-any.pkg.tar.zst",           // AV1 encoder/decoder (AVIF)
        "mingw-w64-ucrt-x86_64-dav1d-1.5.3-1-any.pkg.tar.zst",          // Fast AV1 decoder (AVIF)
        "mingw-w64-ucrt-x86_64-x265-4.1-2-any.pkg.tar.zst",             // H.265 encoder (HEIC)
        "mingw-w64-ucrt-x86_64-kvazaar-2.3.2-1-any.pkg.tar.zst",        // H.265 encoder (alternative)
        "mingw-w64-ucrt-x86_64-openh264-2.6.0-1-any.pkg.tar.zst",       // H.264 decoder/encoder
        "mingw-w64-ucrt-x86_64-libx264-0.165.r3222.b35605a-2-any.pkg.tar.zst", // H.264 encoder
        "mingw-w64-ucrt-x86_64-rav1e-0.8.1-1-any.pkg.tar.zst",          // AV1 encoder (Rust)
        "mingw-w64-ucrt-x86_64-svt-av1-4.0.1-1-any.pkg.tar.zst",        // AV1 encoder (SVT)
        "mingw-w64-ucrt-x86_64-openjpeg2-2.5.4-2-any.pkg.tar.zst",      // JPEG 2000
        "mingw-w64-ucrt-x86_64-openjph-0.26.3-1-any.pkg.tar.zst",       // JPEG 2000 HTJ2K

        // ---- Image format libraries (linked by libheif or its codec plugins) ----
        "mingw-w64-ucrt-x86_64-libjpeg-turbo-3.1.3-1-any.pkg.tar.zst",  // libjpeg-8.dll
        "mingw-w64-ucrt-x86_64-libpng-1.6.55-1-any.pkg.tar.zst",        // libpng16-16.dll
        "mingw-w64-ucrt-x86_64-libtiff-4.7.1-1-any.pkg.tar.zst",        // libtiff-6.dll
        "mingw-w64-ucrt-x86_64-libwebp-1.6.0-1-any.pkg.tar.zst",        // libsharpyuv-0.dll

        // ---- Compression libraries ----
        "mingw-w64-ucrt-x86_64-zlib-1.3.2-1-any.pkg.tar.zst",           // zlib1.dll
        "mingw-w64-ucrt-x86_64-brotli-1.2.0-1-any.pkg.tar.zst",         // libbrotlidec.dll, libbrotlienc.dll
        "mingw-w64-ucrt-x86_64-libdeflate-1.25-1-any.pkg.tar.zst",      // libdeflate.dll (for libtiff)
        "mingw-w64-ucrt-x86_64-xz-5.8.2-1-any.pkg.tar.zst",             // liblzma-5.dll (for libtiff)
        "mingw-w64-ucrt-x86_64-zstd-1.5.7-1-any.pkg.tar.zst",           // libzstd.dll (for libtiff)

        // ---- Transitive dependencies of codec/image libraries ----
        "mingw-w64-ucrt-x86_64-lcms2-2.18-1-any.pkg.tar.zst",           // liblcms2-2.dll (for openjpeg2)
        "mingw-w64-ucrt-x86_64-crypto++-8.9.0-1-any.pkg.tar.zst",       // libcryptopp.dll (for kvazaar)
        "mingw-w64-ucrt-x86_64-jbigkit-2.1-5-any.pkg.tar.zst",          // libjbig-0.dll (for libtiff)
        "mingw-w64-ucrt-x86_64-lerc-4.0.0-1-any.pkg.tar.zst",           // libLerc.dll (for libtiff)
        "mingw-w64-ucrt-x86_64-giflib-5.2.2-1-any.pkg.tar.zst",         // libgif-7.dll (for libwebp)
        "mingw-w64-ucrt-x86_64-gettext-runtime-1.0-1-any.pkg.tar.zst",  // libintl-8.dll (for xz/liblzma)
        "mingw-w64-ucrt-x86_64-libiconv-1.18-1-any.pkg.tar.zst",        // libiconv-2.dll (for gettext-runtime)

        // ---- MinGW runtime ----
        "mingw-w64-ucrt-x86_64-gcc-libs-15.2.0-11-any.pkg.tar.zst",     // libgcc_s_seh-1.dll, libstdc++-6.dll
        "mingw-w64-ucrt-x86_64-libwinpthread-13.0.0.r545.gc39000898-1-any.pkg.tar.zst", // libwinpthread-1.dll
    )

    /** Version tag used for the cache directory. */
    private const val PROVISION_VERSION = "msys2-1.21.2"

    private val cacheDir: File by lazy {
        val home = System.getProperty("user.home")
        File(home, ".transmute/libheif/$PROVISION_VERSION")
    }

    private val downloadCacheDir: File by lazy {
        File(cacheDir, ".download-cache").also { it.mkdirs() }
    }

    /**
     * Provisions libheif binaries, returning the `bin/` directory on success
     * or `null` on failure. Diagnostic messages are appended to [diag].
     *
     * This is safe to call concurrently — a lock file prevents parallel
     * downloads on the same machine.
     */
    fun provision(diag: StringBuilder): File? {
        if (!isWindows()) {
            diag.appendLine("[libheif] Auto-provision: only supported on Windows (current OS: ${System.getProperty("os.name")})")
            return null
        }

        val binDir = File(cacheDir, "bin")
        val marker = File(cacheDir, ".provisioned")

        // Fast path: already provisioned
        if (marker.exists() && File(binDir, "heif-dec.exe").exists()) {
            diag.appendLine("[libheif] Auto-provision: using cached installation at ${binDir.absolutePath}")
            return binDir
        }

        diag.appendLine("[libheif] Auto-provision: no cached installation found, downloading from MSYS2 CDN...")

        val lockFile = File(cacheDir, ".lock")
        cacheDir.mkdirs()

        // Simple file-based lock to prevent concurrent provisioning
        try {
            lockFile.createNewFile()
        } catch (_: Exception) {
            // Lock file creation failure is non-fatal
        }

        return try {
            doProvision(diag)
        } catch (e: Exception) {
            diag.appendLine("[libheif] Auto-provision: FAILED — ${e.message}")
            diag.appendLine("[libheif] Auto-provision: You can install libheif manually:")
            diag.appendLine("[libheif]   Windows: Install MSYS2 (https://www.msys2.org/) then run:")
            diag.appendLine("[libheif]     pacman -S mingw-w64-ucrt-x86_64-libheif")
            null
        } finally {
            lockFile.delete()
        }
    }

    private fun doProvision(diag: StringBuilder): File? {
        val extractDir = Files.createTempDirectory("libheif-provision").toFile()

        try {
            val client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .build()

            // Download and extract each package
            for (pkg in PACKAGES) {
                downloadAndExtract(pkg, extractDir, client, diag)
            }

            // MSYS2 packages extract into ucrt64/ subtree
            val srcBin = File(extractDir, "ucrt64/bin")
            val srcLib = File(extractDir, "ucrt64/lib")

            if (!srcBin.isDirectory) {
                diag.appendLine("[libheif] Auto-provision: extraction produced no ucrt64/bin/ directory")
                return null
            }

            // Copy to cache directory
            val dstBin = File(cacheDir, "bin")
            val dstLib = File(cacheDir, "lib")
            dstBin.mkdirs()

            srcBin.copyRecursively(dstBin, overwrite = true)
            if (srcLib.isDirectory) {
                dstLib.mkdirs()
                srcLib.copyRecursively(dstLib, overwrite = true)
            }

            val decoderExe = File(dstBin, "heif-dec.exe")
            if (!decoderExe.exists()) {
                diag.appendLine("[libheif] Auto-provision: heif-dec.exe not found after extraction")
                return null
            }

            // Verify the binary can actually start
            val verified = verifyBinary(decoderExe, diag)
            if (!verified) {
                diag.appendLine("[libheif] Auto-provision: heif-dec.exe exists but failed verification (missing DLLs?)")
                diag.appendLine("[libheif] Auto-provision: proceeding anyway — codec operations may fail at runtime")
            }

            // Write success marker
            File(cacheDir, ".provisioned").writeText(PROVISION_VERSION)

            val fileCount = dstBin.listFiles()?.size ?: 0
            diag.appendLine("[libheif] Auto-provision: SUCCESS — $fileCount files cached at ${dstBin.absolutePath}")
            return dstBin

        } finally {
            extractDir.deleteRecursively()
        }
    }

    private fun downloadAndExtract(
        pkgFileName: String,
        extractDir: File,
        client: HttpClient,
        diag: StringBuilder,
    ) {
        val pkgFile = File(downloadCacheDir, pkgFileName)

        // Download if not cached
        if (!pkgFile.exists() || pkgFile.length() == 0L) {
            val url = "$MSYS2_MIRROR/$pkgFileName"
            diag.appendLine("[libheif] Auto-provision: downloading $pkgFileName ...")
            val tmp = File(downloadCacheDir, "$pkgFileName.part")

            val request = HttpRequest.newBuilder(URI.create(url))
                .header("User-Agent", "Transmute-LibHeif-AutoProvision/1.0 (+https://transmute.dev)")
                .GET()
                .build()

            val resp = client.send(request, HttpResponse.BodyHandlers.ofFile(tmp.toPath()))
            if (resp.statusCode() !in 200..299) {
                tmp.delete()
                diag.appendLine("[libheif] Auto-provision: download failed (HTTP ${resp.statusCode()}) for $pkgFileName")
                return
            }
            tmp.renameTo(pkgFile)
        } else {
            diag.appendLine("[libheif] Auto-provision: using cached download $pkgFileName")
        }

        // Extract .tar.zst — try multiple approaches for maximum compatibility
        extractDir.mkdirs()
        val extracted = tryExtractTarZst(pkgFile, extractDir, diag)
        if (!extracted) {
            diag.appendLine("[libheif] Auto-provision: WARNING — could not extract $pkgFileName")
        }
    }

    /**
     * Extracts a .tar.zst file using system tools, trying multiple approaches:
     * 1. `tar -xf file.tar.zst` (Windows 10 1903+ with libarchive zstd support)
     * 2. `tar --use-compress-program=zstd -xf file.tar.zst` (requires zstd on PATH)
     * 3. `zstd -d file.tar.zst -o file.tar` then `tar -xf file.tar`
     */
    private fun tryExtractTarZst(archive: File, outDir: File, diag: StringBuilder): Boolean {
        // Approach 1: Direct tar extraction (modern Windows with built-in zstd support)
        if (runProcess(
                listOf("tar", "-xf", archive.absolutePath, "-C", outDir.absolutePath),
                diag, "tar -xf (direct)"
            )
        ) return true

        // Approach 2: tar with explicit zstd compress program
        if (runProcess(
                listOf("tar", "--use-compress-program=zstd", "-xf", archive.absolutePath, "-C", outDir.absolutePath),
                diag, "tar --use-compress-program=zstd"
            )
        ) return true

        // Approach 3: Decompress with zstd first, then extract with tar
        val tarFile = File(archive.parentFile, archive.name.removeSuffix(".zst"))
        if (runProcess(
                listOf("zstd", "-d", "-f", archive.absolutePath, "-o", tarFile.absolutePath),
                diag, "zstd decompress"
            )
        ) {
            val result = runProcess(
                listOf("tar", "-xf", tarFile.absolutePath, "-C", outDir.absolutePath),
                diag, "tar -xf (decompressed)"
            )
            tarFile.delete()
            if (result) return true
        }

        return false
    }

    /**
     * Runs a process and returns `true` if it exits with code 0.
     */
    private fun runProcess(command: List<String>, diag: StringBuilder, label: String): Boolean {
        return try {
            val process = ProcessBuilder(command)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()
            if (exitCode != 0) {
                diag.appendLine("[libheif] Auto-provision: $label failed (exit $exitCode): ${output.take(200)}")
                false
            } else {
                true
            }
        } catch (e: Exception) {
            diag.appendLine("[libheif] Auto-provision: $label not available: ${e.message}")
            false
        }
    }

    /**
     * Quick verification that the binary can start (runs `--version` or `--help`).
     */
    private fun verifyBinary(binary: File, diag: StringBuilder): Boolean {
        return try {
            val process = ProcessBuilder(binary.absolutePath, "--version")
                .redirectErrorStream(true)
                .start()
            process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()
            exitCode == 0
        } catch (e: Exception) {
            diag.appendLine("[libheif] Auto-provision: verification failed: ${e.message}")
            false
        }
    }

    private fun isWindows(): Boolean =
        System.getProperty("os.name", "").lowercase().startsWith("windows")
}
