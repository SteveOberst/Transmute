package dev.transmute.core

/**
 * Global runtime configuration for the Transmute library.
 *
 * Every property has a sensible default so the library works out of the box
 * without any configuration.  Override properties **before** performing any
 * conversions — typically during application initialisation.
 *
 * ## FFmpeg
 *
 * Desktop/JVM codecs that depend on FFmpeg (audio: AAC, M4A, OPUS, OGG,
 * FLAC encode; image: HEIF, AVIF; video: all formats) use a **bundled**
 * static FFmpeg build by default — no setup required.
 *
 * ```kotlin
 * // Point at a system-installed FFmpeg instead of the bundled one
 * TransmuteConfig.ffmpeg = FfmpegConfig.System(
 *   ffmpegPath  = "/usr/local/bin/ffmpeg",
 *   ffprobePath = "/usr/local/bin/ffprobe",
 * )
 *
 * // Disable FFmpeg entirely (FFmpeg-dependent codecs will not register)
 * TransmuteConfig.ffmpeg = FfmpegConfig.Disabled
 *
 * // Reset to bundled (default)
 * TransmuteConfig.ffmpeg = FfmpegConfig.Bundled
 * ```
 *
 * Changing [ffmpeg] at runtime is safe — the resolved binary path is
 * invalidated and re-evaluated lazily on next use.
 */
object TransmuteConfig {

  /**
   * How FFmpeg binaries are located on desktop/JVM.
   *
   * Defaults to [FfmpegConfig.Bundled] which extracts a platform-specific
   * static FFmpeg build to `~/.transmute/ffmpeg/` on first use.
   *
   * Configuration changes take effect on the **next** codec operation;
   * already-running conversions are not affected.
   *
   * @see FfmpegConfig
   */
  @Volatile
  var ffmpeg: FfmpegConfig = FfmpegConfig.Bundled
    set(value) {
      field = value
      // Invalidate cached paths so the resolver re-evaluates.
      resolvedFfmpegPath = null
      resolvedFfprobePath = null
    }

  // -- internal: cached resolution (lazily populated by FfmpegResolver) ----

  @Volatile internal var resolvedFfmpegPath: String? = null
  @Volatile internal var resolvedFfprobePath: String? = null
}

/**
 * Describes how to locate the FFmpeg and FFprobe binaries on desktop/JVM.
 *
 * This sealed hierarchy lets consumers choose between the bundled binary,
 * a system installation, or disabling FFmpeg entirely.  Use the companion
 * factory methods for a concise API or reference the subclass directly:
 *
 * ```kotlin
 * // Equivalent pairs:
 * TransmuteConfig.ffmpeg = FfmpegConfig.Bundled          // data object
 * TransmuteConfig.ffmpeg = FfmpegConfig.bundled()         // companion shortcut
 *
 * TransmuteConfig.ffmpeg = FfmpegConfig.System()          // PATH lookup
 * TransmuteConfig.ffmpeg = FfmpegConfig.system()           // companion shortcut
 *
 * TransmuteConfig.ffmpeg = FfmpegConfig.Disabled
 * TransmuteConfig.ffmpeg = FfmpegConfig.disabled()
 * ```
 *
 * FFmpeg configuration only affects desktop/JVM targets.  Android uses
 * `MediaCodec` and iOS uses `AVFoundation` — neither requires FFmpeg.
 */
sealed class FfmpegConfig {

  /**
   * Use the FFmpeg binary bundled inside the library (default).
   *
   * On first access the library extracts a platform-specific static FFmpeg
   * build from classpath resources to `~/.transmute/ffmpeg/<platform>/` and
   * re-uses it across sessions.
   *
   * Supported host platforms: Windows x64, Linux x64, macOS x64, macOS ARM64.
   * If no bundled binary is available for the current platform the resolver
   * falls back to a PATH lookup automatically.
   */
  data object Bundled : FfmpegConfig()

  /**
   * Use an FFmpeg installation on the system.
   *
   * Pass explicit paths to point at a specific installation, or leave the
   * defaults to search the system `PATH`.
   *
   * @param ffmpegPath  Absolute path to the `ffmpeg` binary, or `"ffmpeg"` to
   *                    search the system PATH.
   * @param ffprobePath Absolute path to the `ffprobe` binary, or `"ffprobe"` to
   *                    search the system PATH.
   */
  data class System(
    val ffmpegPath: String = "ffmpeg",
    val ffprobePath: String = "ffprobe",
  ) : FfmpegConfig()

  /**
   * Disable FFmpeg entirely.
   *
   * Codecs that depend on FFmpeg will not register, and attempting to use
   * them will throw an [IllegalStateException].
   */
  data object Disabled : FfmpegConfig()

  companion object {
    /** Use the bundled FFmpeg (default). */
    fun bundled(): FfmpegConfig = Bundled

    /** Use system-installed FFmpeg (from PATH or at explicit paths). */
    fun system(
      ffmpegPath: String = "ffmpeg",
      ffprobePath: String = "ffprobe",
    ): FfmpegConfig = System(ffmpegPath, ffprobePath)

    /** Disable FFmpeg entirely. */
    fun disabled(): FfmpegConfig = Disabled
  }
}
