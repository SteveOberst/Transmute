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
 *     vcpkg install "libheif[tools,aom,dav1d,rav1e,hevc,x265]" --triplet x64-windows
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

// ---------------------------------------------------------------------------
// Windows staging via vcpkg
// ---------------------------------------------------------------------------

tasks.register("stageLibHeifDesktopWindows") {
  group = "libheif"
  description = "Stages libheif Windows x86_64 binaries via vcpkg for bundling into the desktop JAR."

  val os = System.getProperty("os.name", "").lowercase()
  val isWindows = os.startsWith("windows")
  val platformDir = File(desktopStagingDir, "libheif/windows-x86_64")
  val marker = File(platformDir, ".libheif-$libheifVersion")

  onlyIf { isWindows && !marker.exists() }

  doLast {
    // Locate vcpkg -- fails with an actionable message if not installed.
    val vcpkgExe = requireVcpkg(logger)
    val vcpkgRoot = vcpkgRootFrom(vcpkgExe)
    val triplet = "x64-windows"

    // vcpkg feature flags for libheif.
    //
    // Feature   License            Notes
    // --------  -----------------  ---------------------------------
    // tools     libheif (LGPL-3)   heif-dec.exe, heif-enc.exe, etc.
    // aom       BSD-2-Clause       AV1 encode / decode (AVIF)
    // dav1d     BSD-2-Clause       Fast AV1 decode (AVIF)
    // rav1e     BSD-2-Clause       AV1 encode (AVIF)
    // hevc      LGPL-3.0           HEVC/H.265 decode via libde265 (HEIC)
    // x265      GPL-2.0 / comm.    HEVC/H.265 encode (HEIC) <-- see note
    //
    // LICENSE NOTE: x265 is dual-licensed GPL-2.0 / commercial.
    // Distributing a binary that includes x265 requires GPL-2.0 compliance
    // (publish full source) or a commercial x265 license.
    // Reference: https://www.videolan.org/developers/x265.html
    // To opt out, remove "x265" from transmute.libheif.vcpkgFeatures in
    // gradle.properties.  HEIC decoding and AVIF support remain LGPL/BSD.
    val features = (project.findProperty("transmute.libheif.vcpkgFeatures") as? String)
      ?: "tools,aom,dav1d,rav1e,hevc,x265"
    val pkg = "libheif[$features]"

    logger.lifecycle("libheif Desktop: installing $pkg --triplet $triplet via vcpkg...")
    exec {
      commandLine(
        vcpkgExe.absolutePath,
        "install",
        pkg,
        "--triplet",
        triplet,
        "--no-print-usage",
      )
    }

    // vcpkg installs CLI tools to: installed/<triplet>/tools/libheif/
    // Shared DLLs land in:         installed/<triplet>/bin/
    val toolsDir = File(vcpkgRoot, "installed/$triplet/tools/libheif")
    val binDir = File(vcpkgRoot, "installed/$triplet/bin")
    val libDir = File(vcpkgRoot, "installed/$triplet/lib")

    check(toolsDir.isDirectory) {
      """
            stageLibHeifDesktopWindows: CLI tools directory not found at $toolsDir.
            The libheif vcpkg port may not support the 'tools' feature on this version.
            Check: https://github.com/microsoft/vcpkg/tree/master/ports/libheif
            Alternatively, install libheif manually and use installFrom() in your Transmute config.
            vcpkg installed root: ${File(vcpkgRoot, "installed/$triplet")}
      """.trimIndent()
    }

    val decoderExe = listOf("heif-dec.exe", "heif-convert.exe")
      .map { File(toolsDir, it) }
      .firstOrNull { it.exists() }
    check(decoderExe != null) {
      "stageLibHeifDesktopWindows: neither heif-dec.exe nor heif-convert.exe found under $toolsDir"
    }

    platformDir.mkdirs()
    val dstBin = File(platformDir, "bin")
    val dstLib = File(platformDir, "lib")
    dstBin.mkdirs()

    // Copy CLI executables from the vcpkg tools directory
    toolsDir.copyRecursively(dstBin, overwrite = true)

    // Copy runtime DLLs needed by the tools at runtime
    if (binDir.isDirectory) binDir.copyRecursively(dstBin, overwrite = true)
    if (libDir.isDirectory) libDir.copyRecursively(dstLib, overwrite = true)

    writeLibHeifManifest(platformDir)
    marker.writeText(libheifVersion)

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
