/**
 * Optional Gradle script that downloads the GStreamer SDK for Android and/or iOS.
 *
 * Apply from the module's build.gradle.kts:
 *     apply(from = "gstreamer-sdk.gradle.kts")
 *
 * Environment variables control behaviour:
 *   - `GSTREAMER_ROOT_ANDROID` - If already set, the Android SDK download is skipped.
 *   - `GSTREAMER_ROOT_IOS`     - If already set, the iOS SDK download is skipped.
 *   - `GSTREAMER_VERSION`      - Defaults to "1.24.12".
 *
 * Downloaded SDKs are placed under `$rootDir/build/gstreamer-sdk/`.
 */

val gstVersion = System.getenv("GSTREAMER_VERSION") ?: "1.24.12"
val sdkDir = rootProject.layout.buildDirectory.dir("gstreamer-sdk").get().asFile

// ---------------------------------------------------------------------------
// Android SDK download
// ---------------------------------------------------------------------------

tasks.register("downloadGStreamerAndroid") {
    group = "gstreamer"
    description = "Downloads the GStreamer Android universal SDK."

    val envRoot = System.getenv("GSTREAMER_ROOT_ANDROID")
    val outputDir = File(sdkDir, "android")
    val marker = File(outputDir, ".gst-$gstVersion")

    onlyIf { envRoot == null && !marker.exists() }

    doLast {
        val url = "https://gstreamer.freedesktop.org/data/pkg/android/$gstVersion/" +
            "gstreamer-1.0-android-universal-$gstVersion.tar.xz"
        val archive = File(sdkDir, "gstreamer-android-$gstVersion.tar.xz")

        logger.lifecycle("Downloading GStreamer Android SDK $gstVersion …")
        ant.invokeMethod("get", mapOf("src" to url, "dest" to archive, "skipexisting" to "true"))

        logger.lifecycle("Extracting to ${outputDir.absolutePath} …")
        outputDir.mkdirs()
        exec {
            commandLine("tar", "xf", archive.absolutePath, "-C", outputDir.absolutePath)
        }
        marker.writeText(gstVersion)

        logger.lifecycle("GStreamer Android SDK ready → set GSTREAMER_ROOT_ANDROID=${outputDir.absolutePath}")
    }
}

// ---------------------------------------------------------------------------
// iOS SDK download (macOS only)
// ---------------------------------------------------------------------------

tasks.register("downloadGStreamerIos") {
    group = "gstreamer"
    description = "Downloads the GStreamer iOS framework (macOS only)."

    val envRoot = System.getenv("GSTREAMER_ROOT_IOS")
    val frameworkDir = File("/Library/Frameworks/GStreamer.framework")
    val marker = File(sdkDir, ".gst-ios-$gstVersion")

    onlyIf {
        val isMac = System.getProperty("os.name").startsWith("Mac", ignoreCase = true)
        isMac && envRoot == null && !frameworkDir.exists() && !marker.exists()
    }

    doLast {
        val url = "https://gstreamer.freedesktop.org/data/pkg/ios/$gstVersion/" +
            "gstreamer-1.0-devel-$gstVersion-ios-universal.pkg"
        val pkg = File(sdkDir, "gstreamer-ios-$gstVersion.pkg")

        logger.lifecycle("Downloading GStreamer iOS SDK $gstVersion …")
        ant.invokeMethod("get", mapOf("src" to url, "dest" to pkg, "skipexisting" to "true"))

        logger.lifecycle("Installing GStreamer.framework (requires sudo) …")
        exec {
            commandLine("sudo", "installer", "-pkg", pkg.absolutePath, "-target", "/")
        }
        marker.writeText(gstVersion)

        logger.lifecycle("GStreamer iOS framework installed → /Library/Frameworks/GStreamer.framework")
    }
}

// ---------------------------------------------------------------------------
// Convenience tasks
// ---------------------------------------------------------------------------

tasks.register("downloadGStreamerSdks") {
    group = "gstreamer"
    description = "Downloads both Android and iOS GStreamer SDKs."
    dependsOn("downloadGStreamerAndroid", "downloadGStreamerIos")
}
