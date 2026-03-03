package dev.transmute.libheif

import dev.transmute.filesystem.TPath

/**
 * Describes how libheif CLI tools are located at runtime.
 *
 * The default is [Bundled], which uses the libheif tools shipped with
 * Transmute. Users who prefer their own system-wide installation can use
 * [Custom] or [System].
 *
 * ```kotlin
 * libheif {
 *     // Default — uses the bundled libheif tools (no install required):
 *     // installation is Bundled by default
 *
 *     // Use a specific libheif installation on disk:
 *     installFrom(TPath.of("/opt/libheif"))
 *
 *     // Use whatever libheif is on the system PATH:
 *     useSystemInstallation()
 * }
 * ```
 */
sealed interface LibHeifInstallation {

    /**
     * Use the libheif runtime bundled with Transmute.
     *
     * On Desktop/JVM the bundled binaries are extracted from JAR resources
     * to a cache directory on first use. On Android and iOS this mode is
     * a no-op because those platforms support HEIF/AVIF natively.
     *
     * This is the default — users do not need to install libheif separately.
     */
    data object Bundled : LibHeifInstallation

    /**
     * Use a pre-existing libheif installation at the given [home] directory.
     *
     * The resolver will look for `<home>/bin/heif-dec` (or `heif-convert`)
     * and `<home>/bin/heif-enc` (with `.exe` suffix on Windows). Additional
     * [searchPaths] are checked if the main home directory does not contain
     * the expected binaries.
     *
     * @param home Root directory of the libheif installation.
     * @param searchPaths Additional directories to search for libheif binaries.
     */
    data class Custom(
        val home: TPath,
        val searchPaths: List<TPath> = emptyList(),
    ) : LibHeifInstallation

    /**
     * Locate libheif via the system PATH and platform-specific default
     * locations.
     *
     * This mode does **not** use any bundled binaries. If libheif tools are
     * not installed on the system, HEIF/HEIC/AVIF codecs will be unavailable.
     */
    data object System : LibHeifInstallation
}
