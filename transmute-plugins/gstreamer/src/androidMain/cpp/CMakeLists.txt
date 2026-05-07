cmake_minimum_required(VERSION 3.22.1)
project(gstreamer_bridge C)

# ---
# GStreamer Android SDK discovery
#
# Set GSTREAMER_ROOT_ANDROID to the top-level GStreamer SDK directory,
# e.g. /opt/gstreamer-android-universal-1.24.  Inside the SDK each
# ABI has its own subdirectory (arm64-v8a, armeabi-v7a, x86, x86_64).
# ---

if(NOT DEFINED ENV{GSTREAMER_ROOT_ANDROID})
    message(WARNING "GSTREAMER_ROOT_ANDROID not set – native build will be skipped")
    return()
endif()

set(GSTREAMER_ROOT "$ENV{GSTREAMER_ROOT_ANDROID}/${ANDROID_ABI}")

if(NOT EXISTS "${GSTREAMER_ROOT}/include/gstreamer-1.0/gst/gst.h")
    message(WARNING "GStreamer headers not found at ${GSTREAMER_ROOT} – skipping native build")
    return()
endif()

# ---
# Include paths
# ---

set(GST_INC "${GSTREAMER_ROOT}/include/gstreamer-1.0")
set(GLIB_INC "${GSTREAMER_ROOT}/include/glib-2.0")
set(GLIB_CONFIG_INC "${GSTREAMER_ROOT}/lib/glib-2.0/include")

# ---
# Find prebuilt GStreamer libraries
# ---

find_library(LIB_GST         gstreamer-1.0   PATHS "${GSTREAMER_ROOT}/lib" NO_DEFAULT_PATH)
find_library(LIB_GSTAPP      gstapp-1.0      PATHS "${GSTREAMER_ROOT}/lib" NO_DEFAULT_PATH)
find_library(LIB_GSTBASE     gstbase-1.0     PATHS "${GSTREAMER_ROOT}/lib" NO_DEFAULT_PATH)
find_library(LIB_GSTPBUTILS  gstpbutils-1.0  PATHS "${GSTREAMER_ROOT}/lib" NO_DEFAULT_PATH)
find_library(LIB_GOBJECT     gobject-2.0     PATHS "${GSTREAMER_ROOT}/lib" NO_DEFAULT_PATH)
find_library(LIB_GLIB        glib-2.0        PATHS "${GSTREAMER_ROOT}/lib" NO_DEFAULT_PATH)

# ---
# JNI bridge library
# ---

add_library(gstreamer_bridge SHARED gstreamer_bridge.c)

target_include_directories(gstreamer_bridge PRIVATE
    ${GST_INC}
    ${GLIB_INC}
    ${GLIB_CONFIG_INC}
)

target_link_libraries(gstreamer_bridge
    ${LIB_GST}
    ${LIB_GSTAPP}
    ${LIB_GSTBASE}
    ${LIB_GSTPBUTILS}
    ${LIB_GOBJECT}
    ${LIB_GLIB}
    android
    log
)
