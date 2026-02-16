package dev.transmute.core

import java.io.File
import java.io.FileOutputStream

/**
 * JVM/Desktop FFmpeg binary resolver.
 *
 * Depending on [TransmuteConfig.ffmpeg] this will:
 *
 * - **Bundled** — extract the platform-appropriate FFmpeg/FFprobe static binary
 *   from classpath resources to a cache directory and return paths to them.
 * - **System** — return the user-specified paths (or just `"ffmpeg"` / `"ffprobe"`
 *   for PATH lookup).
 * - **Disabled** — mark FFmpeg as unavailable.
 *
 * The bundled binaries are stored in the classpath under
 * `ffmpeg/<os>-<arch>/ffmpeg[.exe]` and `ffmpeg/<os>-<arch>/ffprobe[.exe]`.
 *
 * If no bundled binary is available for the current platform, the resolver
 * falls back to PATH lookup automatically and logs a warning.
 */
object FfmpegResolver {

  /** `true` when a usable FFmpeg binary has been resolved. */
  val available: Boolean by lazy { resolve(); _available }

  /** Resolved path to the `ffmpeg` binary (or just `"ffmpeg"` for PATH). */
  val ffmpegPath: String
    get() {
      resolve()
      return TransmuteConfig.resolvedFfmpegPath ?: "ffmpeg"
    }

  /** Resolved path to the `ffprobe` binary (or just `"ffprobe"` for PATH). */
  val ffprobePath: String
    get() {
      resolve()
      return TransmuteConfig.resolvedFfprobePath ?: "ffprobe"
    }

  @Volatile
  private var _available = false

  @Volatile
  private var resolved = false

  private val lock = Any()

  /**
   * Force re-resolution. Called automatically when [TransmuteConfig.ffmpeg]
   * changes or on first access.
   */
  fun resolve() {
    // Fast path — already resolved and config hasn't changed.
    if (resolved && TransmuteConfig.resolvedFfmpegPath != null) return

    synchronized(lock) {
      if (resolved && TransmuteConfig.resolvedFfmpegPath != null) return

      when (val cfg = TransmuteConfig.ffmpeg) {
        is FfmpegConfig.Disabled -> {
          _available = false
          TransmuteConfig.resolvedFfmpegPath = null
          TransmuteConfig.resolvedFfprobePath = null
        }

        is FfmpegConfig.System -> {
          TransmuteConfig.resolvedFfmpegPath = cfg.ffmpegPath
          TransmuteConfig.resolvedFfprobePath = cfg.ffprobePath
          _available = testBinary(cfg.ffmpegPath)
        }

        is FfmpegConfig.Bundled -> {
          val extracted = extractBundled()
          if (extracted != null) {
            TransmuteConfig.resolvedFfmpegPath = extracted.first
            TransmuteConfig.resolvedFfprobePath = extracted.second
            _available = testBinary(extracted.first)
          } else {
            // Fallback: try system PATH
            TransmuteConfig.resolvedFfmpegPath = "ffmpeg"
            TransmuteConfig.resolvedFfprobePath = "ffprobe"
            _available = testBinary("ffmpeg")
          }
        }
      }
      resolved = true
    }
  }

  // -----------------------------------------------------------------------
  // Bundled binary extraction
  // -----------------------------------------------------------------------

  private fun extractBundled(): Pair<String, String>? {
    val osArch = detectPlatformTag() ?: return null
    val ext = if (osArch.startsWith("windows")) ".exe" else ""

    val ffmpegResource = "ffmpeg/$osArch/ffmpeg$ext"
    val ffprobeResource = "ffmpeg/$osArch/ffprobe$ext"

    val cacheDir = cacheDirectory(osArch)

    val ffmpegFile = File(cacheDir, "ffmpeg$ext")
    val ffprobeFile = File(cacheDir, "ffprobe$ext")

    extractResource(ffmpegResource, ffmpegFile)
    extractResource(ffprobeResource, ffprobeFile)

    if (!ffmpegFile.exists()) return null

    return ffmpegFile.absolutePath to ffprobeFile.absolutePath
  }

  private fun extractResource(resourcePath: String, target: File) {
    if (target.exists() && target.length() > 0) return // already extracted

    val stream = javaClass.classLoader?.getResourceAsStream(resourcePath)
      ?: FfmpegResolver::class.java.getResourceAsStream("/$resourcePath")
      ?: return // resource not found — skip silently

    target.parentFile?.mkdirs()
    FileOutputStream(target).use { out ->
      stream.use { it.copyTo(out) }
    }
    if (!System.getProperty("os.name", "").startsWith("Windows", ignoreCase = true)) {
      target.setExecutable(true, false)
    }
  }

  private fun cacheDirectory(osArch: String): File {
    val userHome = System.getProperty("user.home", ".")
    val cacheBase = File(userHome, ".transmute/ffmpeg/$osArch")
    cacheBase.mkdirs()
    return cacheBase
  }

  /**
   * Returns a platform tag like `windows-x64`, `linux-x64`, `macos-x64`,
   * `macos-arm64` or `null` if the platform is unsupported.
   */
  private fun detectPlatformTag(): String? {
    val osName = System.getProperty("os.name", "").lowercase()
    val arch = System.getProperty("os.arch", "").lowercase()

    val os = when {
      osName.contains("win") -> "windows"
      osName.contains("mac") || osName.contains("darwin") -> "macos"
      osName.contains("linux") || osName.contains("nux") -> "linux"
      else -> return null
    }

    val cpu = when {
      arch == "aarch64" || arch == "arm64" -> "arm64"
      arch.contains("64") -> "x64"
      else -> return null
    }

    return "$os-$cpu"
  }

  // -----------------------------------------------------------------------
  // Probing
  // -----------------------------------------------------------------------

  private fun testBinary(path: String): Boolean = try {
    val p = ProcessBuilder(path, "-version")
      .redirectErrorStream(true)
      .start()
    p.inputStream.bufferedReader().readText() // drain
    p.waitFor() == 0
  } catch (_: Exception) {
    false
  }
}
