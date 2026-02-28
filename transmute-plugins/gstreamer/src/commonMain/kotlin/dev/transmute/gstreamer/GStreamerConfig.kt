package dev.transmute.gstreamer

import dev.transmute.common.MediaDomain
import dev.transmute.filesystem.TPath

/**
 * Configuration for the optional GStreamer codec integration.
 *
 * Controls which media domains (audio, video, image) should have
 * GStreamer-backed codecs registered and how GStreamer is located on
 * the system. All domains are enabled by default; the bundled GStreamer
 * runtime is used unless overridden.
 *
 * ```kotlin
 * val ctx = TransmuteContext {
 *     gstreamer {
 *         domains(MediaDomain.AUDIO or MediaDomain.VIDEO)
 *         installFrom(TPath.of("/opt/gstreamer"))
 *         timeout(60_000L)
 *     }
 * }
 * ```
 */
class GStreamerConfig(
    /** Which media domains have GStreamer-backed codecs registered. */
    val domains: MediaDomain = MediaDomain.ALL,
    /** How GStreamer binaries are located — bundled, custom path, or system. */
    val installation: GStreamerInstallation = GStreamerInstallation.Bundled,
    /** Subprocess timeout in milliseconds (default: 30 000). */
    val timeoutMs: Long = 30_000L,
) {
    /** DSL builder for [GStreamerConfig]. */
    class Builder internal constructor() {
        private var _domains: MediaDomain = MediaDomain.ALL
        private var _installation: GStreamerInstallation = GStreamerInstallation.Bundled
        private var _timeoutMs: Long = 30_000L

        /**
         * Set the enabled media domains via a [MediaDomain] bitmask.
         *
         * ```kotlin
         * domains(MediaDomain.AUDIO or MediaDomain.IMAGE)
         * ```
         */
        fun domains(mask: MediaDomain) { _domains = mask }

        /**
         * Use a pre-existing GStreamer installation at [home].
         *
         * The resolver looks for `<home>/bin/gst-launch-1.0`.
         */
        fun installFrom(home: TPath) {
            _installation = GStreamerInstallation.Custom(home)
        }

        /**
         * Use a pre-existing GStreamer installation at [home] with
         * additional [searchPaths] for binaries.
         */
        fun installFrom(home: TPath, searchPaths: List<TPath>) {
            _installation = GStreamerInstallation.Custom(home, searchPaths)
        }

        /**
         * Locate GStreamer via the system PATH and platform defaults.
         *
         * Does not use bundled binaries. If GStreamer is not installed
         * on the system, codecs that require it will be unavailable.
         */
        fun useSystemInstallation() {
            _installation = GStreamerInstallation.System
        }

        /** Set the installation mode directly. */
        fun installation(mode: GStreamerInstallation) {
            _installation = mode
        }

        /** Set the subprocess timeout in milliseconds. Use `0` to disable. */
        fun timeout(ms: Long) { _timeoutMs = ms }

        fun build() = GStreamerConfig(
            domains = _domains,
            installation = _installation,
            timeoutMs = _timeoutMs,
        )
    }

    override fun toString(): String =
        "GStreamerConfig(domains=$domains, " +
            "installation=$installation, " +
            "timeoutMs=$timeoutMs)"
}
