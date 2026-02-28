package dev.transmute.gstreamer

import android.util.Log

/**
 * JNI bridge to the GStreamer C library on Android.
 *
 * The native library (`libgstreamer_bridge.so`) is loaded lazily.
 * If the library or GStreamer SDK is not bundled with the APK,
 * [available] returns `false` and all codec registrations are skipped.
 *
 * ### Embedding GStreamer in your Android app
 *
 * 1. Download the GStreamer Android SDK from
 *    `https://gstreamer.freedesktop.org/data/pkg/android/`.
 * 2. Set `GSTREAMER_ROOT_ANDROID` to the extracted SDK root.
 * 3. The CMake build in this module will build `libgstreamer_bridge.so`
 *    and link it against the GStreamer prebuilt libraries.
 */
internal object GStreamerJni {

    private const val TAG = "GStreamerJni"

    /** `true` when the native library is loaded and GStreamer is initialised. */
    val available: Boolean by lazy { tryInit() }

    // -- JNI declarations ---------------------------------------------------

    /** Initialise the GStreamer runtime. Must be called before other functions. */
    @JvmStatic
    private external fun nativeInit(): Boolean

    /** Quick liveness check.  */
    @JvmStatic
    private external fun nativeIsAvailable(): Boolean

    /** Check if a named GStreamer element factory is registered. */
    @JvmStatic
    external fun nativeHasElement(elementName: String): Boolean

    /**
     * Parse and run a GStreamer pipeline to EOS.
     *
     * @param pipelineDesc gst-launch-1.0 compatible descriptor, e.g.
     *   `"filesrc location=/tmp/in.wav ! wavparse ! … ! filesink location=/tmp/out.aac"`
     * @return `true` on successful completion, `false` on error.
     */
    @JvmStatic
    external fun nativeRunPipeline(pipelineDesc: String): Boolean

    /** Return the GStreamer version string (e.g. `"GStreamer 1.24.12"`). */
    @JvmStatic
    external fun nativeGetVersion(): String

    // -- Internal helpers ---------------------------------------------------

    private fun tryInit(): Boolean = try {
        System.loadLibrary("gstreamer_bridge")
        val ok = nativeInit()
        if (ok) Log.i(TAG, "GStreamer initialised: ${nativeGetVersion()}")
        else Log.w(TAG, "GStreamer native init returned false")
        ok
    } catch (e: UnsatisfiedLinkError) {
        Log.d(TAG, "libgstreamer_bridge.so not found – GStreamer unavailable", e)
        false
    }

    /** Check whether a specific GStreamer element/plugin is available. */
    fun hasElement(name: String): Boolean {
        if (!available) return false
        return try { nativeHasElement(name) } catch (_: Exception) { false }
    }

    /**
     * Run a GStreamer pipeline described by [args] (gst-launch-1.0 style
     * tokens). The tokens are joined into a single pipeline descriptor
     * and executed synchronously.
     */
    fun runPipeline(args: List<String>) {
        check(available) { "GStreamer is not available on this device" }
        val desc = args.joinToString(" ")
        val ok = nativeRunPipeline(desc)
        check(ok) { "GStreamer pipeline failed: $desc" }
    }
}
