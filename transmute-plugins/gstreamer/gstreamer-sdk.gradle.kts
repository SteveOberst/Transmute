/**
 * Optional Gradle script that downloads the GStreamer SDK for all platforms.
 *
 * Apply from the module's build.gradle.kts:
 *     apply(from = "gstreamer-sdk.gradle.kts")
 *
 * Version is controlled by `gstreamerVersion` in gradle.properties (preferred) or
 * the `GSTREAMER_VERSION` environment variable. Binaries are downloaded directly from
 * the freedesktop.org CDN file tree - direct file URLs are served without the JS
 * bot-protection that guards the HTML directory listings.
 *
 * Binary layout served at:
 *   Windows : https://gstreamer.freedesktop.org/data/pkg/windows/{v}/msvc/
 *   macOS   : https://gstreamer.freedesktop.org/data/pkg/osx/{v}/
 *   Android : https://gstreamer.freedesktop.org/data/pkg/android/{v}/
 *   iOS     : https://gstreamer.freedesktop.org/data/pkg/ios/{v}/
 *
 * Downloaded SDKs are cached in `$rootDir/build/gstreamer-sdk/`.
 * Desktop staged binaries land in `build/gstreamer-desktop/` and are wired in as a
 * `desktopMain` resource root by the module's build.gradle.kts so they are bundled
 * into the desktop JAR at build time.
 */

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.security.MessageDigest

// Version read from gradle.properties (gstreamerVersion) -> GSTREAMER_VERSION env -> hard-coded default.
// Keep gradle.properties as the canonical source; update it when a new CDN release is available.
val gstVersion: String =
  (project.findProperty("gstreamerVersion") as? String)
    ?: System.getenv("GSTREAMER_VERSION")
    ?: "1.26.4"
val sdkDir = rootProject.layout.buildDirectory.dir("gstreamer-sdk").get().asFile

/** HTTP client shared across all downloads in this build invocation. */
val httpClient: HttpClient by lazy {
  HttpClient.newBuilder()
    .followRedirects(HttpClient.Redirect.ALWAYS)
    .build()
}

/**
 * Downloads [url] to [dest] using Java's HttpClient.
 * The freedesktop.org CDN serves actual binary files without bot-protection;
 * only the HTML directory listing pages are gated.
 * Call is a no-op when [dest] already exists and is non-empty (acts as a cache).
 */
fun downloadGst(url: String, dest: File, logger: org.gradle.api.logging.Logger) {
  if (dest.exists() && dest.length() > 0) {
    logger.lifecycle("  -> already cached: ${dest.name}")
    return
  }
  dest.parentFile.mkdirs()
  val tmp = File(dest.parentFile, "${dest.name}.part")
  val request = HttpRequest.newBuilder(URI.create(url))
    .header("User-Agent", "Gradle-GStreamer-Staging/1.0 (${System.getProperty("os.name")}; +https://transmute.dev)")
    .GET()
    .build()
  logger.lifecycle("  -> GET $url")
  val resp = httpClient.send(request, HttpResponse.BodyHandlers.ofFile(tmp.toPath()))
  check(resp.statusCode() in 200..299) {
    tmp.delete()
    "Download failed (HTTP ${resp.statusCode()}): $url"
  }
  tmp.renameTo(dest)
}

/**
 * Verifies the SHA-256 digest of [file] against [expectedHex] (case-insensitive).
 * [expectedHex] may be just the hex string or the full sha256sum line
 * (`<hex>  <filename>` format - leading hex token is extracted).
 */
fun verifyChecksum(file: File, expectedHex: String, logger: org.gradle.api.logging.Logger) {
  val expected = expectedHex.trim().split(Regex("\\s+")).first().lowercase()
  val digest = MessageDigest.getInstance("SHA-256")
  file.inputStream().buffered(65_536).use { ins ->
    val buf = ByteArray(65_536)
    var n: Int
    while (ins.read(buf).also { n = it } != -1) digest.update(buf, 0, n)
  }
  val actual = digest.digest().joinToString("") { "%02x".format(it) }
  check(actual == expected) {
    "SHA-256 mismatch for ${file.name}\n  expected: $expected\n  actual:   $actual"
  }
  logger.lifecycle("  -> checksum OK ($expected)")
}

// ---------------------------------------------------------------------------
// Android SDK download
// ---------------------------------------------------------------------------

tasks.register("downloadGStreamerAndroid") {
  group = "gstreamer"
  description = "Downloads the GStreamer Android universal SDK."

  val envRoot = System.getenv("GSTREAMER_ROOT_ANDROID")
  val outputDir = File(sdkDir, "android")
  val marker = File(outputDir, ".gst-$gstVersion")

  onlyIf { envRoot == null && !marker.exists() }

  doLast {
    val url = "https://gstreamer.freedesktop.org/data/pkg/android/$gstVersion/" +
      "gstreamer-1.0-android-universal-$gstVersion.tar.xz"
    val archive = File(sdkDir, "gstreamer-android-$gstVersion.tar.xz")

    logger.lifecycle("Downloading GStreamer Android SDK $gstVersion ...")
    downloadGst(url, archive, logger)

    logger.lifecycle("Extracting to ${outputDir.absolutePath} ...")
    outputDir.mkdirs()
    exec {
      commandLine("tar", "xf", archive.absolutePath, "-C", outputDir.absolutePath)
    }
    marker.writeText(gstVersion)

    logger.lifecycle("GStreamer Android SDK ready -> set GSTREAMER_ROOT_ANDROID=${outputDir.absolutePath}")
  }
}

// ---------------------------------------------------------------------------
// iOS SDK download (macOS only)
// ---------------------------------------------------------------------------

tasks.register("downloadGStreamerIos") {
  group = "gstreamer"
  description = "Downloads the GStreamer iOS framework (macOS only)."

  val envRoot = System.getenv("GSTREAMER_ROOT_IOS")
  val frameworkDir = File("/Library/Frameworks/GStreamer.framework")
  val marker = File(sdkDir, ".gst-ios-$gstVersion")

  onlyIf {
    val isMac = System.getProperty("os.name").startsWith("Mac", ignoreCase = true)
    isMac && envRoot == null && !frameworkDir.exists() && !marker.exists()
  }

  doLast {
    val url = "https://gstreamer.freedesktop.org/data/pkg/ios/$gstVersion/" +
      "gstreamer-1.0-devel-$gstVersion-ios-universal.pkg"
    val pkg = File(sdkDir, "gstreamer-ios-$gstVersion.pkg")

    logger.lifecycle("Downloading GStreamer iOS SDK $gstVersion ...")
    downloadGst(url, pkg, logger)

    logger.lifecycle("Installing GStreamer.framework (requires sudo) ...")
    exec {
      commandLine("sudo", "installer", "-pkg", pkg.absolutePath, "-target", "/")
    }
    marker.writeText(gstVersion)

    logger.lifecycle("GStreamer iOS framework installed -> /Library/Frameworks/GStreamer.framework")
  }
}

// ---------------------------------------------------------------------------
// Convenience tasks
// ---------------------------------------------------------------------------

tasks.register("downloadGStreamerSdks") {
  group = "gstreamer"
  description = "Downloads both Android and iOS GStreamer SDKs."
  dependsOn("downloadGStreamerAndroid", "downloadGStreamerIos")
}

// ---------------------------------------------------------------------------
// Desktop SDK staging - bundles GStreamer binaries into the desktop JAR
// ---------------------------------------------------------------------------

val desktopStagingDir = project.layout.buildDirectory.dir("gstreamer-desktop").get().asFile

/**
 * Writes a manifest.txt listing all files (relative paths) present in [platformDir],
 * then writes a version marker so the task is skipped on subsequent builds.
 */
fun writeDesktopManifest(platformDir: File) {
  val manifest = platformDir.walk()
    .filter { it.isFile && it.name != "manifest.txt" && !it.name.startsWith(".gst-") }
    .map { it.relativeTo(platformDir).path.replace('\\', '/') }
    .sorted()
    .joinToString("\n")
  File(platformDir, "manifest.txt").writeText(manifest)
}

fun extractMacosPkgPayload(payload: File, destinationDir: File) {
  val payloadPath = payload.absolutePath.replace("'", "'\\''")
  val gzipMagic = byteArrayOf(0x1f, 0x8b.toByte())
  val isGzip = payload.inputStream().use { input ->
    val header = ByteArray(2)
    input.read(header) == header.size && header.contentEquals(gzipMagic)
  }
  val payloadReader = if (isGzip) "gunzip -c '$payloadPath'" else "cat '$payloadPath'"

  exec {
    commandLine("sh", "-c", "$payloadReader | cpio -idum")
    workingDir = destinationDir
  }
}

tasks.register("stageGStreamerDesktopWindows") {
  group = "gstreamer"
  description = "Downloads and stages GStreamer Windows x86_64 binaries for bundling into the desktop JAR."

  val os = System.getProperty("os.name", "").lowercase()
  val isWindows = os.startsWith("windows")
  val platformDir = File(desktopStagingDir, "gstreamer/windows-x86_64")
  val marker = File(platformDir, ".gst-$gstVersion")

  onlyIf { isWindows && !marker.exists() }

  doLast {
    // Direct binary URL on the freedesktop.org CDN.
    // The bot-protection only guards HTML directory listing pages, not the binary files.
    val msiName = "gstreamer-1.0-msvc-x86_64-$gstVersion.msi"
    val msiUrl = "https://gstreamer.freedesktop.org/data/pkg/windows/$gstVersion/msvc/$msiName"
    val sha256Url = "$msiUrl.sha256sum"

    // Cache the MSI in sdkDir so re-runs and re-stagings don't re-download.
    val cacheDir = File(sdkDir, "windows-cache").also { it.mkdirs() }
    val msiFile = File(cacheDir, msiName)
    val sha256File = File(cacheDir, "$msiName.sha256sum")

    logger.lifecycle("GStreamer Desktop: downloading Windows $gstVersion MSI...")
    downloadGst(msiUrl, msiFile, logger)
    downloadGst(sha256Url, sha256File, logger)
    verifyChecksum(msiFile, sha256File.readText(), logger)

    val tmpDir = Files.createTempDirectory("gst-msi-extract-$gstVersion").toFile()
    try {

      val extractDir = File(tmpDir, "extracted")
      extractDir.mkdirs()
      logger.lifecycle("GStreamer Desktop: extracting MSI (administrative install, no elevation required)...")
      exec {
        commandLine(
          "msiexec",
          "/a",
          msiFile.absolutePath,
          "TARGETDIR=${extractDir.absolutePath}",
          "/qn",
        )
      }

      val gstRoot = listOf(
        File(extractDir, "PFiles64/gstreamer/1.0/msvc_x86_64"),
        File(extractDir, "gstreamer/1.0/msvc_x86_64"),
        File(extractDir, "Program Files/gstreamer/1.0/msvc_x86_64"),
      ).firstOrNull { it.isDirectory && File(it, "bin/gst-launch-1.0.exe").exists() }
        ?: error(
          "stageGStreamerDesktopWindows: gst-launch-1.0.exe not found under $extractDir\n" +
            "Searched: PFiles64/gstreamer/1.0/msvc_x86_64, gstreamer/1.0/msvc_x86_64, Program Files/...\n" +
            "Actual contents of $extractDir:\n" +
            extractDir.listFiles()?.joinToString("\n") { "  ${it.name}" },
        )

      platformDir.mkdirs()
      logger.lifecycle("GStreamer Desktop: staging Windows binaries -> $platformDir")
      for (sub in listOf("bin", "lib")) {
        val src = File(gstRoot, sub)
        if (src.isDirectory) src.copyRecursively(File(platformDir, sub), overwrite = true)
      }

      writeDesktopManifest(platformDir)
      marker.writeText(gstVersion)
      logger.lifecycle(
        "GStreamer Desktop: Windows staged -- " +
          "${File(platformDir, "bin").listFiles()?.size ?: 0} files in bin/",
      )
    } finally {
      tmpDir.deleteRecursively()
    }
  }
}

tasks.register("stageGStreamerDesktopMacos") {
  group = "gstreamer"
  description = "Downloads and stages GStreamer macOS universal binaries for bundling into the desktop JAR."

  val os = System.getProperty("os.name", "").lowercase()
  val isMac = os.startsWith("mac") || os.contains("darwin")
  // shared marker so we only download the universal PKG once for both arches
  val sharedMarker = File(desktopStagingDir, ".gst-macos-$gstVersion")

  onlyIf { isMac && !sharedMarker.exists() }

  doLast {
    // Direct binary URL on the freedesktop.org CDN (no bot-protection on binary files).
    val pkgName = "gstreamer-1.0-$gstVersion-universal.pkg"
    val pkgUrl = "https://gstreamer.freedesktop.org/data/pkg/osx/$gstVersion/$pkgName"
    val sha256Url = "$pkgUrl.sha256sum"

    // Cache the PKG in sdkDir so re-runs don't re-download.
    val cacheDir = File(sdkDir, "macos-cache").also { it.mkdirs() }
    val pkgFile = File(cacheDir, pkgName)
    val sha256File = File(cacheDir, "$pkgName.sha256sum")

    val tmpDir = Files.createTempDirectory("gst-pkg-extract-$gstVersion").toFile()
    try {
      logger.lifecycle("GStreamer Desktop: downloading macOS $gstVersion universal PKG...")
      downloadGst(pkgUrl, pkgFile, logger)
      downloadGst(sha256Url, sha256File, logger)
      verifyChecksum(pkgFile, sha256File.readText(), logger)

      val expandDir = File(tmpDir, "pkg-expanded")
      expandDir.mkdirs()
      logger.lifecycle("GStreamer Desktop: expanding PKG with xar...")
      exec { commandLine("xar", "-xf", pkgFile.absolutePath, "-C", expandDir.absolutePath) }

      val payload = expandDir.walk()
        .filter { it.name == "Payload" && it.isFile }
        .firstOrNull() ?: error("stageGStreamerDesktopMacos: Payload not found in $expandDir")

      val cpioDir = File(tmpDir, "payload")
      cpioDir.mkdirs()
      logger.lifecycle("GStreamer Desktop: extracting cpio Payload...")
      extractMacosPkgPayload(payload, cpioDir)

      val fwBase = cpioDir.walk()
        .filter { it.isDirectory && it.name == "GStreamer.framework" }
        .firstOrNull() ?: error("stageGStreamerDesktopMacos: GStreamer.framework not found in $cpioDir")

      val commandsDir = File(fwBase, "Commands")
      val libDir = File(fwBase, "Versions/Current/lib")

      // The universal binary works on both x86_64 and aarch64; stage under both so the
      // extractor can find it regardless of which arch the JVM reports at runtime.
      for (arch in listOf("macos-x86_64", "macos-aarch64")) {
        val platformDir = File(desktopStagingDir, "gstreamer/$arch")
        platformDir.mkdirs()
        if (commandsDir.isDirectory) {
          val dstBin = File(platformDir, "bin")
          commandsDir.copyRecursively(dstBin, overwrite = true)
          dstBin.walkTopDown().filter { it.isFile }.forEach { it.setExecutable(true, false) }
        }
        if (libDir.isDirectory) {
          libDir.copyRecursively(File(platformDir, "lib"), overwrite = true)
        }
        writeDesktopManifest(platformDir)
        File(platformDir, ".gst-$gstVersion").writeText(gstVersion)
      }

      sharedMarker.writeText(gstVersion)
      logger.lifecycle("GStreamer Desktop: macOS universal binaries staged for x86_64 + aarch64")
    } finally {
      tmpDir.deleteRecursively()
    }
  }
}

tasks.register("stageGStreamerDesktop") {
  group = "gstreamer"
  description = "Stages GStreamer desktop binaries for the current build platform into build/gstreamer-desktop/."
  dependsOn("stageGStreamerDesktopWindows", "stageGStreamerDesktopMacos")
}
