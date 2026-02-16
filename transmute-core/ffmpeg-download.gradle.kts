/**
 * Gradle task that downloads platform-specific static FFmpeg builds
 * and places them under `src/desktopMain/resources/ffmpeg/<os>-<arch>/`.
 *
 * Run manually:  ./gradlew :transmute-core:downloadFfmpeg
 *
 * The CI build calls this automatically via the `processResources` dependency.
 *
 * Static builds are sourced from https://github.com/eugeneware/ffmpeg-static
 * (MIT-licensed redistributable binaries).
 */

import java.net.URL
import java.util.zip.ZipInputStream

val ffmpegVersion = "6.1.2"

// Map of platform tag → download URL
// Using ffmpeg-static GitHub releases (MIT licensed)
val platforms = mapOf(
  "windows-x64" to "https://github.com/eugeneware/ffmpeg-static/releases/download/b${ffmpegVersion}/win32-x64.gz",
  "linux-x64" to "https://github.com/eugeneware/ffmpeg-static/releases/download/b${ffmpegVersion}/linux-x64.gz",
  "macos-x64" to "https://github.com/eugeneware/ffmpeg-static/releases/download/b${ffmpegVersion}/darwin-x64.gz",
  "macos-arm64" to "https://github.com/eugeneware/ffmpeg-static/releases/download/b${ffmpegVersion}/darwin-arm64.gz",
)

tasks.register("downloadFfmpeg") {
  group = "build setup"
  description = "Downloads static FFmpeg binaries for all supported platforms"

  val outputDir = layout.projectDirectory.dir("src/desktopMain/resources/ffmpeg")

  outputs.dir(outputDir)

  doLast {
    platforms.forEach { (tag, url) ->
      val ext = if (tag.startsWith("windows")) ".exe" else ""
      val targetDir = outputDir.dir(tag).asFile
      val targetFile = File(targetDir, "ffmpeg$ext")

      if (targetFile.exists() && targetFile.length() > 0) {
        logger.lifecycle("FFmpeg binary already present for $tag, skipping download")
        return@forEach
      }

      logger.lifecycle("Downloading FFmpeg for $tag from $url ...")
      targetDir.mkdirs()

      try {
        val connection = URL(url).openConnection()
        connection.connectTimeout = 30_000
        connection.readTimeout = 120_000

        java.util.zip.GZIPInputStream(connection.getInputStream()).use { gzis ->
          targetFile.outputStream().use { out ->
            gzis.copyTo(out)
          }
        }

        // Set executable on Unix
        if (!tag.startsWith("windows")) {
          targetFile.setExecutable(true, false)
        }

        logger.lifecycle("FFmpeg for $tag downloaded (${targetFile.length() / 1024 / 1024} MB)")
      } catch (e: Exception) {
        logger.warn("Failed to download FFmpeg for $tag: ${e.message}")
        targetFile.delete()
      }
    }
  }
}
