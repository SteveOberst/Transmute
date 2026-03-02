@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package dev.transmute.gstreamer

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.toKString
import platform.posix.RTLD_LAZY
import platform.posix.dlopen
import platform.posix.dlsym

/**
 * Bridge to GStreamer on iOS via Kotlin/Native cinterop.
 *
 * GStreamer.framework must be linked into the final app binary for
 * [available] to return `true`. If the framework is not present,
 * all operations gracefully report unavailability.
 *
 * ### Embedding GStreamer in your iOS app
 *
 * 1. Download the GStreamer iOS framework from
 *    `https://gstreamer.freedesktop.org/data/pkg/ios/`.
 * 2. Add `GStreamer.framework` to your Xcode project's
 *    **Frameworks, Libraries, and Embedded Content**.
 * 3. This module's cinterop binding enables Kotlin/Native access to
 *    the GStreamer C API.
 */
internal object GStreamerIosBridge {

    /** `true` when GStreamer.framework is linked and initialised. */
    val available: Boolean by lazy { tryInit() }

    @OptIn(ExperimentalForeignApi::class)
    private fun tryInit(): Boolean = try {
        // Try to find GStreamer symbols to verify framework is linked
        val handle = dlopen(null, RTLD_LAZY) ?: return false
        val gstInit = dlsym(handle, "gst_init_check") ?: return false

        // Call gst_init_check(NULL, NULL, NULL) - returns gboolean
        gst.gst_init_check(null, null, null) != 0
    } catch (_: Throwable) {
        false
    }

    /** Check whether a GStreamer element factory is registered. */
    @OptIn(ExperimentalForeignApi::class)
    fun hasElement(name: String): Boolean {
        if (!available) return false
        return try {
            val factory = gst.gst_element_factory_find(name)
            if (factory != null) {
                gst.gst_object_unref(factory)
                true
            } else false
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * Parse and run a GStreamer pipeline to EOS.
     *
     * @param pipelineDesc gst-launch-1.0 compatible descriptor
     * @return `true` on success
     */
    @OptIn(ExperimentalForeignApi::class)
    fun runPipeline(pipelineDesc: String): Boolean {
        if (!available) return false
        return try {
            memScoped {
                val error = alloc<gst.GErrorVar>()
                val pipeline = gst.gst_parse_launch(pipelineDesc, error.ptr)
                    ?: return false

                val ret = gst.gst_element_set_state(pipeline, gst.GST_STATE_PLAYING)
                if (ret == gst.GST_STATE_CHANGE_FAILURE) {
                    gst.gst_object_unref(pipeline)
                    return false
                }

                val bus = gst.gst_element_get_bus(pipeline) ?: run {
                    gst.gst_element_set_state(pipeline, gst.GST_STATE_NULL)
                    gst.gst_object_unref(pipeline)
                    return false
                }

                val msg = gst.gst_bus_timed_pop_filtered(
                    bus,
                    gst.GST_CLOCK_TIME_NONE,
                    gst.GST_MESSAGE_EOS or gst.GST_MESSAGE_ERROR,
                )

                val success = if (msg != null) {
                    val msgType = gst.GST_MESSAGE_TYPE(msg)
                    gst.gst_message_unref(msg)
                    msgType == gst.GST_MESSAGE_EOS
                } else false

                gst.gst_element_set_state(pipeline, gst.GST_STATE_NULL)
                gst.gst_object_unref(bus)
                gst.gst_object_unref(pipeline)

                success
            }
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * Run a pipeline described by [args] tokens.
     * @throws IllegalStateException if the pipeline fails.
     */
    fun runPipelineChecked(args: List<String>) {
        check(available) { "GStreamer is not available on this device" }
        val desc = args.joinToString(" ")
        check(runPipeline(desc)) { "GStreamer pipeline failed: $desc" }
    }
}
