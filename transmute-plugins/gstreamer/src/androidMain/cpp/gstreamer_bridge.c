/*
 * gstreamer_bridge.c — JNI bridge for GStreamer on Android.
 *
 * Provides thin JNI wrappers around the GStreamer C API so that the
 * Kotlin layer can:
 *   1. Initialise GStreamer once per process.
 *   2. Check whether a specific element/plugin is installed.
 *   3. Execute a file-based GStreamer pipeline to completion (blocking).
 *
 * The Kotlin engine classes build the pipeline description string the
 * same way the Desktop target does (filesrc ! … ! filesink) and call
 * nativeRunPipeline().
 */

#include <jni.h>
#include <android/log.h>
#include <string.h>
#include <gst/gst.h>

#define TAG "GStreamerBridge"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN,  TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

static int gst_initialised = 0;

/* ------------------------------------------------------------------ */
/* nativeInit — call once from Kotlin before any pipeline work.       */
/* Returns JNI_TRUE on success.                                       */
/* ------------------------------------------------------------------ */

JNIEXPORT jboolean JNICALL
Java_dev_transmute_gstreamer_GStreamerJni_nativeInit(JNIEnv *env, jclass clazz) {
    if (gst_initialised) return JNI_TRUE;

    GError *error = NULL;
    if (!gst_init_check(NULL, NULL, &error)) {
        if (error) {
            LOGE("gst_init_check failed: %s", error->message);
            g_error_free(error);
        }
        return JNI_FALSE;
    }
    gst_initialised = 1;
    LOGI("GStreamer initialised: %s", gst_version_string());
    return JNI_TRUE;
}

/* ------------------------------------------------------------------ */
/* nativeIsAvailable — quick liveness check.                          */
/* ------------------------------------------------------------------ */

JNIEXPORT jboolean JNICALL
Java_dev_transmute_gstreamer_GStreamerJni_nativeIsAvailable(JNIEnv *env, jclass clazz) {
    return gst_initialised ? JNI_TRUE : JNI_FALSE;
}

/* ------------------------------------------------------------------ */
/* nativeHasElement — check registry for a named element factory.     */
/* ------------------------------------------------------------------ */

JNIEXPORT jboolean JNICALL
Java_dev_transmute_gstreamer_GStreamerJni_nativeHasElement(
        JNIEnv *env, jclass clazz, jstring element_name) {
    if (!gst_initialised) return JNI_FALSE;

    const char *name = (*env)->GetStringUTFChars(env, element_name, NULL);
    if (!name) return JNI_FALSE;

    GstElementFactory *factory = gst_element_factory_find(name);
    jboolean result = (factory != NULL) ? JNI_TRUE : JNI_FALSE;
    if (factory) gst_object_unref(factory);

    (*env)->ReleaseStringUTFChars(env, element_name, name);
    return result;
}

/* ------------------------------------------------------------------ */
/* nativeRunPipeline — parse & run a pipeline to EOS.                 */
/*                                                                    */
/* @param pipeline_desc  gst-launch style descriptor, e.g.           */
/*   "filesrc location=/tmp/in.wav ! wavparse ! ... ! filesink ..."  */
/* @return JNI_TRUE on success, JNI_FALSE on error.                   */
/* ------------------------------------------------------------------ */

JNIEXPORT jboolean JNICALL
Java_dev_transmute_gstreamer_GStreamerJni_nativeRunPipeline(
        JNIEnv *env, jclass clazz, jstring pipeline_desc) {
    if (!gst_initialised) {
        LOGE("GStreamer not initialised");
        return JNI_FALSE;
    }

    const char *desc = (*env)->GetStringUTFChars(env, pipeline_desc, NULL);
    if (!desc) return JNI_FALSE;

    GError *error = NULL;
    GstElement *pipeline = gst_parse_launch(desc, &error);
    (*env)->ReleaseStringUTFChars(env, pipeline_desc, desc);

    if (!pipeline) {
        LOGE("Pipeline parse error: %s", error ? error->message : "unknown");
        if (error) g_error_free(error);
        return JNI_FALSE;
    }
    if (error) {
        /* Non-fatal warning from parse_launch */
        LOGW("Pipeline parse warning: %s", error->message);
        g_error_free(error);
    }

    /* Start the pipeline */
    GstStateChangeReturn ret = gst_element_set_state(pipeline, GST_STATE_PLAYING);
    if (ret == GST_STATE_CHANGE_FAILURE) {
        LOGE("Failed to set pipeline to PLAYING");
        gst_object_unref(pipeline);
        return JNI_FALSE;
    }

    /* Wait for EOS or ERROR */
    GstBus *bus = gst_element_get_bus(pipeline);
    GstMessage *msg = gst_bus_timed_pop_filtered(
        bus,
        GST_CLOCK_TIME_NONE,
        GST_MESSAGE_EOS | GST_MESSAGE_ERROR
    );

    jboolean success = JNI_TRUE;

    if (msg) {
        switch (GST_MESSAGE_TYPE(msg)) {
            case GST_MESSAGE_ERROR: {
                GError *err = NULL;
                gchar *debug = NULL;
                gst_message_parse_error(msg, &err, &debug);
                LOGE("Pipeline error: %s (%s)", err->message, debug ? debug : "");
                g_error_free(err);
                g_free(debug);
                success = JNI_FALSE;
                break;
            }
            case GST_MESSAGE_EOS:
                LOGI("Pipeline reached EOS");
                break;
            default:
                break;
        }
        gst_message_unref(msg);
    }

    gst_element_set_state(pipeline, GST_STATE_NULL);
    gst_object_unref(bus);
    gst_object_unref(pipeline);

    return success;
}

/* ------------------------------------------------------------------ */
/* nativeGetVersion — return the GStreamer version string.             */
/* ------------------------------------------------------------------ */

JNIEXPORT jstring JNICALL
Java_dev_transmute_gstreamer_GStreamerJni_nativeGetVersion(JNIEnv *env, jclass clazz) {
    if (!gst_initialised) return (*env)->NewStringUTF(env, "not initialised");
    const gchar *ver = gst_version_string();
    return (*env)->NewStringUTF(env, ver);
}
