/**
 * Gradle script that stages libheif CLI tools for desktop distributions.
 *
 * Apply from the module's build.gradle.kts:
 *     apply(from = "libheif-sdk.gradle.kts")
 *
 * Version is controlled by `libheifVersion` in gradle.properties (preferred) or
 * the `LIBHEIF_VERSION` environment variable.
 *
 * ## Windows (vcpkg)
 *
 * The staging task delegates to vcpkg, Microsoft's open-source C/C++ package
 * manager, to install libheif and its codec plugins:
 *
 *     vcpkg install "libheif[tools,aom,dav1d,rav1e,hevc]" --triplet x64-windows
 *
 * Prerequisites:
 *   - vcpkg must be installed and either VCPKG_ROOT must be set or vcpkg must
 *     be on your PATH.  Install guide:
 *     https://learn.microsoft.com/en-us/vcpkg/get_started/get-started
 *
 * ### Licensing note for Windows
 * The `x265` feature enables HEIC/HEIF *encoding* via x265, which is
 * dual-licensed GPL-2.0 / commercial (https://www.videolan.org/developers/x265.html).
 * Bundling x265 means the combined libheif binary is subject to GPL-2.0 unless
 * you hold a commercial x265 license.  To avoid GPL, remove `x265` from the
 * `transmute.libheif.vcpkgFeatures` Gradle property and omit HEIC encoding.
 * HEIF/AVIF *decoding* and AVIF *encoding* (via aom/rav1e) are unaffected
 * and remain under permissive licenses (LGPL / BSD).
 *
 * ## macOS (Homebrew)
 *
 * The staging task copies binaries from a local Homebrew installation
 * (`brew install libheif`). This avoids any network access at Gradle time.
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

import dev.transmute.gradle.requireVcpkg
import dev.transmute.gradle.vcpkgRootFrom

// Version read from gradle.properties -> LIBHEIF_VERSION env -> hard-coded default.
val libheifVersion: String =
  (project.findProperty("libheifVersion") as? String)
    ?: System.getenv("LIBHEIF_VERSION")
    ?: "1.19.7"

val desktopStagingDir = project.layout.buildDirectory.dir("libheif-desktop").get().asFile

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

fun hasLibHeifDecoder(binDir: File): Boolean =
  listOf("heif-dec.exe", "heif-convert.exe").any { File(binDir, it).isFile }

fun stageLibHeifWindowsHome(homeDir: File, platformDir: File): Boolean {
  val srcBin = File(homeDir, "bin")
  if (!srcBin.isDirectory || !hasLibHeifDecoder(srcBin)) return false

  val dstBin = File(platformDir, "bin")
  val dstLib = File(platformDir, "lib")
  dstBin.mkdirs()
  srcBin.copyRecursively(dstBin, overwrite = true)

  val srcLib = File(homeDir, "lib")
  if (srcLib.isDirectory) {
    srcLib.copyRecursively(dstLib, overwrite = true)
  }

  return true
}

fun stageLibHeifFromVcpkgInstall(vcpkgInstalledDir: File, platformDir: File): Boolean {
  val toolsDir = File(vcpkgInstalledDir, "tools/libheif")
  val binDir = File(vcpkgInstalledDir, "bin")
  val libDir = File(vcpkgInstalledDir, "lib")

  val dstBin = File(platformDir, "bin")
  val dstLib = File(platformDir, "lib")
  dstBin.mkdirs()

  if (toolsDir.isDirectory) {
    toolsDir.copyRecursively(dstBin, overwrite = true)
  }
  if (binDir.isDirectory) {
    binDir.copyRecursively(dstBin, overwrite = true)
  }
  if (libDir.isDirectory) {
    libDir.copyRecursively(dstLib, overwrite = true)
  }

  return hasLibHeifDecoder(dstBin)
}

// ---------------------------------------------------------------------------
// Windows staging via vcpkg
// ---------------------------------------------------------------------------

tasks.register("stageLibHeifDesktopWindows") {
  group = "libheif"
  description = "Stages libheif Windows x86_64 binaries from an existing local installation for bundling into the desktop JAR."

  val os = System.getProperty("os.name", "").lowercase()
  val isWindows = os.startsWith("windows")
  val platformDir = File(desktopStagingDir, "libheif/windows-x86_64")
  val marker = File(platformDir, ".libheif-$libheifVersion")

  onlyIf { isWindows && !marker.exists() }

  doLast {
    val triplet = "x64-windows"
    platformDir.mkdirs()

    val configuredHome = (project.findProperty("transmute.libheif.windowsHome") as? String)
      ?: System.getenv("TRANSMUTE_LIBHEIF_WINDOWS_HOME")
    val msys2Root = System.getenv("MSYS2_ROOT")
    val windowsHomes = buildList {
      configuredHome?.let(::add)
      msys2Root?.let { add(File(it, "mingw64").absolutePath) }
      add("C:/msys64/mingw64")
      add("C:/msys64/ucrt64")
      add("C:/msys64/clang64")
    }
      .distinct()
      .map(::File)

    val stagedFromHome = windowsHomes.firstOrNull { homeDir ->
      homeDir.isDirectory && stageLibHeifWindowsHome(homeDir, platformDir)
    }

    if (stagedFromHome != null) {
      logger.lifecycle("libheif Desktop: Windows binaries staged from ${stagedFromHome.absolutePath}")
    } else {
      val vcpkgExe = runCatching { requireVcpkg(logger) }.getOrNull()
      val vcpkgInstalledDir = vcpkgExe?.let { File(vcpkgRootFrom(it), "installed/$triplet") }
      val stagedFromVcpkg = vcpkgInstalledDir?.takeIf { it.isDirectory }?.let { installDir ->
        stageLibHeifFromVcpkgInstall(installDir, platformDir)
      } == true

      check(stagedFromVcpkg) {
        """
              stageLibHeifDesktopWindows: No supported libheif CLI installation found.
              Preferred source for CI and local staging: MSYS2 with mingw-w64-x86_64-libheif installed.
              Option 1: set TRANSMUTE_LIBHEIF_WINDOWS_HOME to a home containing bin/ and lib/.
              Option 2: install MSYS2 libheif under C:/msys64/mingw64.
              Option 3: provide a vcpkg installation that already contains heif-dec.exe / heif-convert.exe.
        """.trimIndent()
      }

      logger.lifecycle("libheif Desktop: Windows binaries staged from vcpkg at $vcpkgInstalledDir")
    }

    writeLibHeifManifest(platformDir)
    marker.writeText(libheifVersion)

    val dstBin = File(platformDir, "bin")

    val exeCount = dstBin.listFiles { f -> f.extension.equals("exe", ignoreCase = true) }?.size ?: 0
    val dllCount = dstBin.listFiles { f -> f.extension.equals("dll", ignoreCase = true) }?.size ?: 0
    logger.lifecycle("libheif Desktop: Windows staged -- $exeCount exe(s) + $dllCount DLL(s) in bin/")
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
      "stageLibHeifDesktopMacos: No bin/ directory at $brewPrefix -- is libheif installed?"
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
      logger.lifecycle("  -> manifest written for ${platformDir.name}")
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
