package dev.transmute.gstreamer

import dev.transmute.filesystem.TPath

/**
 * Describes how GStreamer binaries are located at runtime.
 *
 * The default is [Bundled], which uses the GStreamer runtime shipped with
 * Transmute. Users who prefer their own system-wide installation can use
 * [Custom] or [System].
 *
 * ```kotlin
 * gstreamer {
 *     // Default - uses the bundled GStreamer runtime (no install required):
 *     // installation is Bundled by default
 *
 *     // Use a specific GStreamer installation on disk:
 *     installFrom(TPath.of("/opt/gstreamer"))
 *
 *     // Use whatever GStreamer is on the system PATH:
 *     useSystemInstallation()
 * }
 * ```
 */
sealed interface GStreamerInstallation {

    /**
     * Use the GStreamer runtime bundled with Transmute.
     *
     * On Desktop/JVM the bundled binaries are extracted from JAR resources
     * to a cache directory on first use. On Android and iOS the native
     * GStreamer framework is linked at build time.
     *
     * This is the default - users do not need to install GStreamer separately.
     */
    data object Bundled : GStreamerInstallation

    /**
     * Use a pre-existing GStreamer installation at the given [home] directory.
     *
     * The resolver will look for `<home>/bin/gst-launch-1.0` (with `.exe`
     * suffix on Windows). Additional [searchPaths] are checked if the main
     * home directory does not contain the expected binaries.
     *
     * @param home Root directory of the GStreamer installation.
     * @param searchPaths Additional directories to search for GStreamer binaries.
     */
    data class Custom(
        val home: TPath,
        val searchPaths: List<TPath> = emptyList(),
    ) : GStreamerInstallation

    /**
     * Locate GStreamer via the system PATH and platform-specific default
     * locations (e.g. Windows registry paths, Homebrew on macOS).
     *
     * This mode does **not** use any bundled binaries. If GStreamer is not
     * installed on the system, codecs that require it will be unavailable.
     */
    data object System : GStreamerInstallation
}
