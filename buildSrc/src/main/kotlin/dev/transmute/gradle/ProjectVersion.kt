package dev.transmute.gradle

import java.io.File

object ProjectVersion {

  /**
   * Resolves the project version.
   *
   * Primary source: `.release-please-manifest.json` key `"."`.
   * Optional override: `TRANSMUTE_VERSION` environment variable.
   */
  fun resolve(rootDir: File): String {
    val envOverride = System.getenv("TRANSMUTE_VERSION")
      ?.trim()
      ?.takeIf { it.isNotEmpty() }

    if (envOverride != null) return envOverride

    val manifestFile = File(rootDir, ".release-please-manifest.json")
    if (!manifestFile.isFile) {
      error(
        "Missing .release-please-manifest.json at ${manifestFile.absolutePath}. " +
          "Set TRANSMUTE_VERSION to override, or restore the manifest file.",
      )
    }

    val text = manifestFile.readText(Charsets.UTF_8)
    val match = Regex("\"\\.\"\\s*:\\s*\"([^\"]+)\"").find(text)
      ?: error(
        "Unable to parse version from ${manifestFile.absolutePath}. " +
          "Expected JSON like { \".\": \"0.1.0\" }.",
      )

    return match.groupValues[1]
  }
}
