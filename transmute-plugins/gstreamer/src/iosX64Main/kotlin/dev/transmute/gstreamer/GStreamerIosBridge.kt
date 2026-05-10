@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package dev.transmute.gstreamer

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import platform.posix.RTLD_LAZY
import platform.posix.dlopen
import platform.posix.dlsym

internal actual object GStreamerIosBridge {
  actual val available: Boolean by lazy { tryInit() }

  @OptIn(ExperimentalForeignApi::class)
  private fun tryInit(): Boolean = try {
    val handle = dlopen(null, RTLD_LAZY) ?: return false
    dlsym(handle, "gst_init_check") ?: return false
    gst.gst_init_check(null, null, null) != 0
  } catch (_: Throwable) {
    false
  }

  @OptIn(ExperimentalForeignApi::class)
  actual fun hasElement(name: String): Boolean {
    if (!available) return false
    return try {
      val factory = gst.gst_element_factory_find(name)
      if (factory != null) {
        gst.gst_object_unref(factory)
        true
      } else {
        false
      }
    } catch (_: Throwable) {
      false
    }
  }

  @OptIn(ExperimentalForeignApi::class)
  actual fun runPipeline(pipelineDesc: String): Boolean {
    if (!available) return false
    return try {
      memScoped {
        val pipeline = gst.gst_parse_launch(pipelineDesc, null) ?: return false

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
          val msgType = msg.pointed.type
          gst.gst_message_unref(msg)
          msgType == gst.GST_MESSAGE_EOS
        } else {
          false
        }

        gst.gst_element_set_state(pipeline, gst.GST_STATE_NULL)
        gst.gst_object_unref(bus)
        gst.gst_object_unref(pipeline)
        success
      }
    } catch (_: Throwable) {
      false
    }
  }

  actual fun runPipelineChecked(args: List<String>) {
    check(available) { "GStreamer is not available on this device" }
    val desc = args.joinToString(" ")
    check(runPipeline(desc)) { "GStreamer pipeline failed: $desc" }
  }
}