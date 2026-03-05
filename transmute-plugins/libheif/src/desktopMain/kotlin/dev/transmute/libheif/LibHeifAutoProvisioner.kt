package dev.transmute.libheif

import java.io.File

/**
 * Runtime provisioner that resolves libheif CLI tools from a vcpkg installation.
 *
 * This is the last-resort fallback used when:
 *  a) The desktop JAR was built without staged binaries (i.e. running from source), and
 *  b) No system libheif installation is on PATH.
 *
 * ### How it works
 *
 * 1. Locates vcpkg via the `VCPKG_ROOT` environment variable or a PATH search.
 * 2. Checks whether libheif has already been installed in vcpkg.
 * 3. If not, runs `vcpkg install "libheif[tools,aom,dav1d,rav1e,hevc,x265]" --triplet x64-windows`.
 * 4. Copies the resulting binaries to a local cache under `~/.transmute/libheif/`.
 *
 * ### Requirements
 * - Windows (vcpkg provisioning is Windows-only; macOS/Linux use system package managers)
 * - [vcpkg](https://learn.microsoft.com/en-us/vcpkg/get_started/get-started) installed,
 *   with `VCPKG_ROOT` set or `vcpkg` on PATH
 * - Internet access (first run only; results are cached)
 *
 * ### Cache layout
 * ```
 * ~/.transmute/libheif/<version>/
 *   bin/
 *     heif-dec.exe  (or heif-convert.exe on older libheif)
 *     heif-enc.exe
 *     libheif.dll
 *     ...
 * ```
 */
internal object LibHeifAutoProvisioner {

  /** Vcpkg triplet used for Windows x86_64 builds. */
  private const val TRIPLET = "x64-windows"

  /**
   * Default vcpkg feature set for libheif.
   *
   * Feature   License             Notes
   * --------  ------------------  -----------------------------------------
   * tools     libheif (LGPL-3)    heif-dec.exe, heif-enc.exe, etc.
   * aom       BSD-2-Clause        AV1 encode / decode (AVIF)
   * dav1d     BSD-2-Clause        Fast AV1 decode (AVIF)
   * rav1e     BSD-2-Clause        AV1 encode (AVIF)
   * hevc      LGPL-3.0            HEVC/H.265 decode via libde265 (HEIC)
   * x265      GPL-2.0 / commercial HEVC/H.265 encode (HEIC) -- see note below
   *
   * LICENSE NOTE: x265 is dual-licensed GPL-2.0 / commercial.
   * Distributing a binary that includes x265 requires either GPL-2.0 compliance
   * (publishing full source) or a commercial x265 license.
   * See https://www.videolan.org/developers/x265.html
   * Override via the TRANSMUTE_LIBHEIF_VCPKG_FEATURES environment variable.
   */
  private val vcpkgFeatures: String
    get() = System.getenv("TRANSMUTE_LIBHEIF_VCPKG_FEATURES")
      ?: "tools,aom,dav1d,rav1e,hevc,x265"

  /** Version tag used for the local cache directory. */
  private const val CACHE_VERSION = "vcpkg-current"

  private val cacheDir: File by lazy {
    File(System.getProperty("user.home"), ".transmute/libheif/$CACHE_VERSION")
  }

  /**
   * Provisions libheif binaries via vcpkg, returning the `bin/` directory on success
   * or `null` on failure. Diagnostic messages are appended to [diag].
   */
  fun provision(diag: StringBuilder): File? {
    if (!isWindows()) {
      diag.appendLine(
        "[libheif] Auto-provision: vcpkg provisioning is only supported on Windows (current OS: ${System.getProperty("os.name")})",
      )
      return null
    }

    val binDir = File(cacheDir, "bin")
    val marker = File(cacheDir, ".provisioned")

    // Fast path: already provisioned
    val decoder = listOf("heif-dec.exe", "heif-convert.exe").map { File(binDir, it) }.firstOrNull { it.exists() }
    if (marker.exists() && decoder != null) {
      diag.appendLine("[libheif] Auto-provision: using cached installation at ${binDir.absolutePath}")
      return binDir
    }

    diag.appendLine("[libheif] Auto-provision: looking for vcpkg installation...")

    val vcpkgExe = findVcpkg(diag)
    if (vcpkgExe == null) {
      diag.appendLine("[libheif] Auto-provision: FAILED -- vcpkg not found.")
      diag.appendLine("")
      diag.appendLine("[libheif] To fix this, install vcpkg:")
      diag.appendLine("[libheif]   Official guide: https://learn.microsoft.com/en-us/vcpkg/get_started/get-started")
      diag.appendLine("[libheif]")
      diag.appendLine("[libheif]   Quick install (PowerShell):")
      diag.appendLine("[libheif]     git clone https://github.com/microsoft/vcpkg \$env:USERPROFILE\\vcpkg")
      diag.appendLine("[libheif]     & \"\$env:USERPROFILE\\vcpkg\\bootstrap-vcpkg.bat\"")
      diag.appendLine("[libheif]     \$env:VCPKG_ROOT = \"\$env:USERPROFILE\\vcpkg\"")
      diag.appendLine("[libheif]")
      diag.appendLine("[libheif]   After installing, set VCPKG_ROOT or add vcpkg to PATH, then restart the application.")
      return null
    }

    val vcpkgRoot = vcpkgExe.parentFile

    return try {
      doProvision(vcpkgExe, vcpkgRoot, diag)
    } catch (e: Exception) {
      diag.appendLine("[libheif] Auto-provision: FAILED -- ${e.message}")
      null
    }
  }

  private fun doProvision(vcpkgExe: File, vcpkgRoot: File, diag: StringBuilder): File? {
    val pkg = "libheif[$vcpkgFeatures]"
    diag.appendLine("[libheif] Auto-provision: running: ${vcpkgExe.absolutePath} install $pkg --triplet $TRIPLET")

    val result = ProcessBuilder(
      vcpkgExe.absolutePath,
      "install",
      pkg,
      "--triplet",
      TRIPLET,
      "--no-print-usage",
    )
      .redirectErrorStream(true)
      .start()

    val output = result.inputStream.bufferedReader().readText()
    val exitCode = result.waitFor()

    if (exitCode != 0) {
      diag.appendLine("[libheif] Auto-provision: vcpkg install failed (exit $exitCode):")
      diag.appendLine(output.take(1000))
      return null
    }

    // Locate installed tools
    val toolsDir = File(vcpkgRoot, "installed/$TRIPLET/tools/libheif")
    val binSrc = File(vcpkgRoot, "installed/$TRIPLET/bin")

    if (!toolsDir.isDirectory) {
      diag.appendLine("[libheif] Auto-provision: tools/ directory not found at $toolsDir")
      diag.appendLine("[libheif]   The vcpkg libheif port on this version may not include CLI tools.")
      diag.appendLine("[libheif]   Check: https://github.com/microsoft/vcpkg/tree/master/ports/libheif")
      return null
    }

    // Copy to cache
    cacheDir.mkdirs()
    val dstBin = File(cacheDir, "bin")
    dstBin.mkdirs()

    toolsDir.copyRecursively(dstBin, overwrite = true)
    if (binSrc.isDirectory) binSrc.copyRecursively(dstBin, overwrite = true)

    val decoderExe = listOf("heif-dec.exe", "heif-convert.exe").map { File(dstBin, it) }.firstOrNull { it.exists() }
    if (decoderExe == null) {
      diag.appendLine("[libheif] Auto-provision: heif-dec.exe / heif-convert.exe not found after install")
      return null
    }

    File(cacheDir, ".provisioned").writeText(CACHE_VERSION)

    val count = dstBin.listFiles()?.size ?: 0
    diag.appendLine("[libheif] Auto-provision: SUCCESS -- $count files cached at ${dstBin.absolutePath}")
    return dstBin
  }

  /**
   * Locates the vcpkg executable by checking VCPKG_ROOT, then PATH.
   */
  private fun findVcpkg(diag: StringBuilder): File? {
    val vcpkgRoot = System.getenv("VCPKG_ROOT")
    if (!vcpkgRoot.isNullOrBlank()) {
      val exe = File(vcpkgRoot, "vcpkg.exe")
      if (exe.isFile) {
        diag.appendLine("[libheif] Auto-provision: found vcpkg at VCPKG_ROOT: ${exe.absolutePath}")
        return exe
      }
    }

    val pathDirs = System.getenv("PATH")?.split(File.pathSeparatorChar) ?: emptyList()
    for (dir in pathDirs) {
      for (name in listOf("vcpkg.exe", "vcpkg.cmd", "vcpkg")) {
        val candidate = File(dir, name)
        if (candidate.isFile) {
          diag.appendLine("[libheif] Auto-provision: found vcpkg on PATH: ${candidate.absolutePath}")
          return candidate
        }
      }
    }

    return null
  }

  private fun isWindows(): Boolean = System.getProperty("os.name", "").lowercase().startsWith("windows")
}
