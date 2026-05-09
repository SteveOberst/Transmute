cmake_minimum_required(VERSION 3.22.1)
project(gstreamer_bridge C)

# ---
# GStreamer Android SDK discovery
#
# Set GSTREAMER_ROOT_ANDROID to the top-level GStreamer SDK directory,
# e.g. /opt/gstreamer-android-universal-1.24.  Inside the SDK each
# ABI has its own subdirectory. The SDK uses arm64/armv7/x86/x86_64,
# which differs from the Android NDK ABI names for ARM targets.
# ---

if(DEFINED GSTREAMER_ROOT_ANDROID AND NOT GSTREAMER_ROOT_ANDROID STREQUAL "")
    set(GSTREAMER_ANDROID_SDK_ROOT "${GSTREAMER_ROOT_ANDROID}")
elseif(DEFINED ENV{GSTREAMER_ROOT_ANDROID} AND NOT "$ENV{GSTREAMER_ROOT_ANDROID}" STREQUAL "")
    set(GSTREAMER_ANDROID_SDK_ROOT "$ENV{GSTREAMER_ROOT_ANDROID}")
else()
    message(WARNING "GSTREAMER_ROOT_ANDROID not set – native build will be skipped")
    return()
endif()

if(ANDROID_ABI STREQUAL "arm64-v8a")
    set(GSTREAMER_ANDROID_ABI_DIR "arm64")
elseif(ANDROID_ABI STREQUAL "armeabi-v7a")
    set(GSTREAMER_ANDROID_ABI_DIR "armv7")
else()
    set(GSTREAMER_ANDROID_ABI_DIR "${ANDROID_ABI}")
endif()

set(GSTREAMER_ROOT "${GSTREAMER_ANDROID_SDK_ROOT}/${GSTREAMER_ANDROID_ABI_DIR}")

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

function(resolve_gstreamer_library out_var library_name)
    set(static_library "${GSTREAMER_ROOT}/lib/lib${library_name}.a")
    set(shared_library "${GSTREAMER_ROOT}/lib/lib${library_name}.so")

    if(EXISTS "${static_library}")
        set(${out_var} "${static_library}" PARENT_SCOPE)
    elseif(EXISTS "${shared_library}")
        set(${out_var} "${shared_library}" PARENT_SCOPE)
    else()
        set(${out_var} "${out_var}-NOTFOUND" PARENT_SCOPE)
    endif()
endfunction()

resolve_gstreamer_library(LIB_GST        "gstreamer-1.0")
resolve_gstreamer_library(LIB_GSTAPP     "gstapp-1.0")
resolve_gstreamer_library(LIB_GSTBASE    "gstbase-1.0")
resolve_gstreamer_library(LIB_GSTPBUTILS "gstpbutils-1.0")
resolve_gstreamer_library(LIB_GOBJECT    "gobject-2.0")
resolve_gstreamer_library(LIB_GLIB       "glib-2.0")
resolve_gstreamer_library(LIB_GMODULE    "gmodule-2.0")
resolve_gstreamer_library(LIB_INTL       "intl")
resolve_gstreamer_library(LIB_PCRE2      "pcre2-8")
resolve_gstreamer_library(LIB_ICONV      "iconv")
resolve_gstreamer_library(LIB_FFI        "ffi")

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
    ${LIB_GMODULE}
    ${LIB_INTL}
    ${LIB_PCRE2}
    ${LIB_ICONV}
    ${LIB_FFI}
    android
    log
)
