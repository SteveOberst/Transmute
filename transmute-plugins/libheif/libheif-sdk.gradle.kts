/**
 * Optional Gradle script that downloads and stages libheif CLI tools for desktop
 * distributions.
 *
 * Apply from the module's build.gradle.kts:
 *     apply(from = "libheif-sdk.gradle.kts")
 *
 * Version is controlled by `libheifVersion` in gradle.properties (preferred) or
 * the `LIBHEIF_VERSION` environment variable.
 *
 * ## Windows (MSYS2)
 *
 * The staging task downloads the MinGW-w64 `libheif` package and its runtime
 * dependencies from the MSYS2 package repository. This is the most reliable
 * source of pre-built libheif CLI tools for Windows.
 *
 * Prerequisites: none — the task downloads directly from the MSYS2 CDN.
 * Extraction requires `tar` and `zstd` on PATH (available on Windows 11+;
 * for Windows 10, install zstd: `winget install Facebook.zstd`).
 *
 * ## macOS (Homebrew)
 *
 * The staging task copies binaries from a local Homebrew installation
 * (`brew install libheif`). This avoids downloading from external URLs.
 *
 * ## Manual staging
 *
 * If the automated tasks do not work for your setup, manually place binaries in:
 *     build/libheif-desktop/libheif/<platform>/bin/heif-dec[.exe]
 *     build/libheif-desktop/libheif/<platform>/bin/heif-enc[.exe]
 *     build/libheif-desktop/libheif/<platform>/lib/...
 *
 * Then run `stageLibHeifDesktopManifest` to generate the manifest file.
 *
 * Staged binaries land in `build/libheif-desktop/` and are wired in as a
 * `desktopMain` resource root by the module's build.gradle.kts, so they are
 * bundled into the desktop JAR at build time.
 */

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files

// Version read from gradle.properties -> LIBHEIF_VERSION env -> hard-coded default.
val libheifVersion: String =
    (project.findProperty("libheifVersion") as? String)
        ?: System.getenv("LIBHEIF_VERSION")
        ?: "1.19.7"

val desktopStagingDir = project.layout.buildDirectory.dir("libheif-desktop").get().asFile

/** HTTP client shared across all downloads in this build invocation. */
val httpClient: HttpClient by lazy {
    HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.ALWAYS)
        .build()
}

/**
 * Downloads [url] to [dest]. No-op when [dest] already exists and is non-empty.
 */
fun downloadLibHeif(url: String, dest: File, logger: org.gradle.api.logging.Logger) {
    if (dest.exists() && dest.length() > 0) {
        logger.lifecycle("  ↳ already cached: ${dest.name}")
        return
    }
    dest.parentFile.mkdirs()
    val tmp = File(dest.parentFile, "${dest.name}.part")
    val request = HttpRequest.newBuilder(URI.create(url))
        .header("User-Agent", "Gradle-LibHeif-Staging/1.0 (${System.getProperty("os.name")}; +https://transmute.dev)")
        .GET()
        .build()
    logger.lifecycle("  ↳ GET $url")
    val resp = httpClient.send(request, HttpResponse.BodyHandlers.ofFile(tmp.toPath()))
    check(resp.statusCode() in 200..299) {
        tmp.delete()
        "Download failed (HTTP ${resp.statusCode()}): $url"
    }
    tmp.renameTo(dest)
}

/**
 * Writes a manifest.txt listing all files (relative paths) present in [platformDir].
 */
fun writeLibHeifManifest(platformDir: File) {
    val manifest = platformDir.walk()
        .filter { it.isFile && it.name != "manifest.txt" && !it.name.startsWith(".libheif-") }
        .map { it.relativeTo(platformDir).path.replace('\\', '/') }
        .sorted()
        .joinToString("\n")
    File(platformDir, "manifest.txt").writeText(manifest)
}

// ---------------------------------------------------------------------------
// MSYS2 package helper — downloads and extracts a .pkg.tar.zst package
// ---------------------------------------------------------------------------

/**
 * MSYS2 packages that must be downloaded to get a working heif-dec.exe / heif-enc.exe.
 * The main package is `mingw-w64-ucrt-x86_64-libheif`; its runtime dependencies are
 * listed here explicitly so we bundle the required DLLs.
 *
 * Each entry is a pair of (package-name, package-version-suffix) as it appears on the
 * MSYS2 CDN. When bumping [libheifVersion], update these entries to match the versions
 * in the MSYS2 repository at https://packages.msys2.org/base/mingw-w64-libheif
 */
val msys2Mirror = "https://mirror.msys2.org/mingw/ucrt64"

/**
 * Downloads an MSYS2 .pkg.tar.zst package and extracts it into [outDir].
 * Tries multiple extraction approaches for maximum compatibility:
 * 1. `tar -xf` directly (Windows 10 1903+ with built-in zstd support in libarchive)
 * 2. `tar --use-compress-program=zstd` (requires zstd on PATH)
 * 3. `zstd -d` then `tar -xf` (requires zstd on PATH)
 */
fun downloadAndExtractMsys2Pkg(
    pkgFileName: String,
    outDir: File,
    cacheDir: File,
    logger: org.gradle.api.logging.Logger,
) {
    val pkgFile = File(cacheDir, pkgFileName)
    downloadLibHeif("$msys2Mirror/$pkgFileName", pkgFile, logger)

    outDir.mkdirs()

    // Approach 1: Direct extraction — modern Windows tar (libarchive) supports .tar.zst natively
    try {
        logger.lifecycle("  ↳ extracting (direct): ${pkgFile.name} -> $outDir")
        val result = ProcessBuilder("tar", "-xf", pkgFile.absolutePath, "-C", outDir.absolutePath)
            .redirectErrorStream(true)
            .start()
        result.inputStream.bufferedReader().readText() // drain output
        if (result.waitFor() == 0) return
        logger.lifecycle("  ↳ direct tar extraction failed (exit ${result.waitFor()}), trying zstd fallback...")
    } catch (e: Exception) {
        logger.lifecycle("  ↳ direct tar extraction not available: ${e.message}")
    }

    // Approach 2: Decompress with zstd, then extract with tar
    val tarFile = File(cacheDir, pkgFileName.removeSuffix(".zst"))
    if (!tarFile.exists() || tarFile.length() == 0L) {
        logger.lifecycle("  ↳ decompressing: ${pkgFile.name}")
        val zstdResult = ProcessBuilder("zstd", "-d", "-f", pkgFile.absolutePath, "-o", tarFile.absolutePath)
            .redirectErrorStream(true)
            .start()
        val zstdOutput = zstdResult.inputStream.bufferedReader().readText()
        check(zstdResult.waitFor() == 0) {
            "zstd decompression failed for $pkgFileName:\n$zstdOutput\n" +
                "Install zstd: winget install Facebook.zstd\n" +
                "Or use Windows 11+ where tar handles .tar.zst natively."
        }
    }

    logger.lifecycle("  ↳ extracting: ${tarFile.name} -> $outDir")
    val tarResult = ProcessBuilder("tar", "-xf", tarFile.absolutePath, "-C", outDir.absolutePath)
        .redirectErrorStream(true)
        .start()
    val tarOutput = tarResult.inputStream.bufferedReader().readText()
    check(tarResult.waitFor() == 0) {
        "tar extraction failed for ${tarFile.name}:\n$tarOutput"
    }
}

// ---------------------------------------------------------------------------
// Windows staging via MSYS2
// ---------------------------------------------------------------------------

tasks.register("stageLibHeifDesktopWindows") {
    group = "libheif"
    description = "Downloads and stages libheif Windows x86_64 binaries from MSYS2 for bundling into the desktop JAR."

    val os = System.getProperty("os.name", "").lowercase()
    val isWindows = os.startsWith("windows")
    val platformDir = File(desktopStagingDir, "libheif/windows-x86_64")
    val marker = File(platformDir, ".libheif-$libheifVersion")

    onlyIf { isWindows && !marker.exists() }

    doLast {
        // MSYS2 UCRT64 packages for libheif and its runtime dependencies.
        // The libheif package includes heif-dec.exe, heif-enc.exe, heif-convert.exe.
        // Runtime DLLs (libheif, libde265, aom, dav1d, etc.) must also be staged.
        //
        // To find the exact package filenames for a new version, check:
        //   https://packages.msys2.org/packages/mingw-w64-ucrt-x86_64-libheif
        // and its "Dependencies" (Runtime) list.
        //
        // These are the UCRT64 packages needed as of libheif 1.21.2:
        val packages = listOf(
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

        val cacheDir = File(project.rootProject.layout.buildDirectory.get().asFile, "libheif-sdk/windows-cache")
        cacheDir.mkdirs()
        val extractDir = Files.createTempDirectory("libheif-msys2-$libheifVersion").toFile()

        try {
            for (pkg in packages) {
                try {
                    downloadAndExtractMsys2Pkg(pkg, extractDir, cacheDir, logger)
                } catch (e: Exception) {
                    logger.warn("  ↳ skipping $pkg: ${e.message}")
                }
            }

            // MSYS2 ucrt64 packages extract into ucrt64/ subtree
            val binSrc = File(extractDir, "ucrt64/bin")
            val libSrc = File(extractDir, "ucrt64/lib")

            check(binSrc.isDirectory) {
                "stageLibHeifDesktopWindows: Expected ucrt64/bin/ in MSYS2 extraction.\n" +
                    "Contents of $extractDir:\n" +
                    (extractDir.listFiles()?.joinToString("\n") { "  ${it.name}" } ?: "  (empty)")
            }

            platformDir.mkdirs()
            val dstBin = File(platformDir, "bin")
            val dstLib = File(platformDir, "lib")

            // Copy heif-dec.exe, heif-enc.exe, heif-convert.exe and all DLLs
            logger.lifecycle("libheif Desktop: staging Windows binaries -> $platformDir")
            if (binSrc.isDirectory) binSrc.copyRecursively(dstBin, overwrite = true)
            if (libSrc.isDirectory) libSrc.copyRecursively(dstLib, overwrite = true)

            writeLibHeifManifest(platformDir)
            marker.writeText(libheifVersion)

            val binCount = dstBin.listFiles()?.size ?: 0
            logger.lifecycle("libheif Desktop: Windows staged — $binCount files in bin/")
        } finally {
            extractDir.deleteRecursively()
        }
    }
}

// ---------------------------------------------------------------------------
// macOS staging via Homebrew
// ---------------------------------------------------------------------------

tasks.register("stageLibHeifDesktopMacos") {
    group = "libheif"
    description = "Stages libheif macOS binaries from Homebrew for bundling into the desktop JAR."

    val os = System.getProperty("os.name", "").lowercase()
    val isMac = os.startsWith("mac") || os.contains("darwin")
    val sharedMarker = File(desktopStagingDir, ".libheif-macos-$libheifVersion")

    onlyIf { isMac && !sharedMarker.exists() }

    doLast {
        // Locate Homebrew prefix for libheif
        val brewPrefix = try {
            val proc = ProcessBuilder("brew", "--prefix", "libheif")
                .redirectErrorStream(true)
                .start()
            val output = proc.inputStream.bufferedReader().readText().trim()
            check(proc.waitFor() == 0) { "brew --prefix libheif failed" }
            output
        } catch (e: Exception) {
            error(
                "stageLibHeifDesktopMacos: Homebrew libheif not found.\n" +
                    "Install it first: brew install libheif\n" +
                    "Error: ${e.message}",
            )
        }

        val homebrewBin = File(brewPrefix, "bin")
        val homebrewLib = File(brewPrefix, "lib")

        check(homebrewBin.isDirectory) {
            "stageLibHeifDesktopMacos: No bin/ directory at $brewPrefix — is libheif installed?"
        }

        // Stage under both arch names (Homebrew on Apple Silicon is universal or native)
        for (arch in listOf("macos-x86_64", "macos-aarch64")) {
            val platformDir = File(desktopStagingDir, "libheif/$arch")
            platformDir.mkdirs()

            val dstBin = File(platformDir, "bin")
            homebrewBin.copyRecursively(dstBin, overwrite = true)
            dstBin.walkTopDown().filter { it.isFile }.forEach { it.setExecutable(true, false) }

            if (homebrewLib.isDirectory) {
                homebrewLib.copyRecursively(File(platformDir, "lib"), overwrite = true)
            }

            writeLibHeifManifest(platformDir)
            File(platformDir, ".libheif-$libheifVersion").writeText(libheifVersion)
        }

        sharedMarker.writeText(libheifVersion)
        logger.lifecycle("libheif Desktop: macOS binaries staged for x86_64 + aarch64 from Homebrew at $brewPrefix")
    }
}

// ---------------------------------------------------------------------------
// Manifest-only task for manual staging
// ---------------------------------------------------------------------------

tasks.register("stageLibHeifDesktopManifest") {
    group = "libheif"
    description = "Generates manifest.txt for manually placed libheif binaries in build/libheif-desktop/."

    doLast {
        val baseDir = File(desktopStagingDir, "libheif")
        if (!baseDir.isDirectory) {
            logger.warn(
                "No staged binaries found at $baseDir.\n" +
                    "Place binaries in build/libheif-desktop/libheif/<platform>/bin/ first.",
            )
            return@doLast
        }
        var count = 0
        baseDir.listFiles()?.filter { it.isDirectory }?.forEach { platformDir ->
            writeLibHeifManifest(platformDir)
            count++
            logger.lifecycle("  ↳ manifest written for ${platformDir.name}")
        }
        if (count == 0) {
            logger.warn("No platform directories found under $baseDir")
        }
    }
}

// ---------------------------------------------------------------------------
// Umbrella task
// ---------------------------------------------------------------------------

tasks.register("stageLibHeifDesktop") {
    group = "libheif"
    description = "Stages libheif desktop binaries for the current build platform into build/libheif-desktop/."
    dependsOn("stageLibHeifDesktopWindows", "stageLibHeifDesktopMacos")
}
