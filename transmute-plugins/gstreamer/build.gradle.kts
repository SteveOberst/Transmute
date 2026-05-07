plugins {
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.android.library)
  `maven-publish`
}

apply(from = "gstreamer-sdk.gradle.kts")

// -- Optional GStreamer SDK locations -----------------------------------------
val gstreamerAndroidRoot: String? = System.getenv("GSTREAMER_ROOT_ANDROID")
val gstreamerIosFramework = listOfNotNull(
  System.getenv("GSTREAMER_ROOT_IOS")?.let(::file),
  rootProject.layout.buildDirectory.dir("gstreamer-sdk/ios/GStreamer.framework").get().asFile,
  file("/Library/Frameworks/GStreamer.framework"),
).firstOrNull { it.exists() } ?: file("/Library/Frameworks/GStreamer.framework")

kotlin {
  val isMac = System.getProperty("os.name").startsWith("Mac", ignoreCase = true)

  androidTarget {
    compilerOptions {
      jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
    publishLibraryVariants("release")
  }

  jvm("desktop") {
    compilerOptions {
      jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }

    compilations {
      val test by getting
      val integrationTest by creating {
        associateWith(test)
      }
    }

    testRuns.create("integrationTest") {
      setExecutionSourceFrom(compilations["integrationTest"])
      executionTask.configure {
        description = "Runs the desktop integration tests."
        group = org.gradle.language.base.plugins.LifecycleBasePlugin.VERIFICATION_GROUP
        shouldRunAfter(testRuns["test"].executionTask)
      }
    }
  }

  if (isMac) {
    listOf(iosX64(), iosArm64(), iosSimulatorArm64()).forEach { target ->
      target.binaries.framework {
        baseName = "transmute-gstreamer"
        isStatic = true
      }
      // Set up GStreamer cinterop when the framework is present
      if (gstreamerIosFramework.exists()) {
        val headersDir = File(gstreamerIosFramework, "Headers")
        target.binaries.all {
          linkerOpts(
            "-F${gstreamerIosFramework.parentFile.absolutePath}",
            "-framework",
            "GStreamer",
          )
        }
        target.compilations["main"].cinterops.create("gstreamer") {
          defFile(project.file("src/nativeInterop/cinterop/gstreamer.def"))
          compilerOpts(
            "-I${headersDir.absolutePath}",
            "-I${File(headersDir, "gstreamer-1.0").absolutePath}",
            "-I${File(headersDir, "glib-2.0").absolutePath}",
          )
        }
      }
    }
  }

  sourceSets {
    commonMain.dependencies {
      api(project(":transmute-api"))
      api(project(":transmute-audio"))
      api(project(":transmute-image"))
      api(project(":transmute-video"))
      api(project(":transmute-filesystem:core"))
      api(project(":transmute-plugins:catalog"))
      implementation(libs.kotlinx.coroutines.core)
    }
    commonTest.dependencies {
      implementation(libs.kotlin.test)
      implementation(libs.kotlinx.coroutines.test)
    }

    val androidMain by getting
    val androidInstrumentedTest by getting {
      dependencies {
        implementation(libs.kotlin.test)
        implementation("androidx.test.ext:junit:1.1.5")
        implementation("androidx.test:runner:1.5.2")
        implementation(libs.kotlinx.coroutines.test)
      }
    }

    val desktopMain by getting {
      // Staged GStreamer binaries are bundled into the desktop JAR as classpath resources.
      // Run `./gradlew stageGStreamerDesktop` (once per version, per platform) to populate
      // build/gstreamer-desktop/ before building a distribution.
      resources.srcDir(layout.buildDirectory.dir("gstreamer-desktop"))
    }
    val desktopTest by getting

    val desktopIntegrationTest by getting

    if (isMac) {
      val iosMain by creating {
        dependsOn(commonMain.get())
      }
      val iosX64Main by getting { dependsOn(iosMain) }
      val iosArm64Main by getting { dependsOn(iosMain) }
      val iosSimulatorArm64Main by getting { dependsOn(iosMain) }

      val iosTest by creating {
        dependsOn(commonTest.get())
      }
      val iosX64Test by getting { dependsOn(iosTest) }
      val iosArm64Test by getting { dependsOn(iosTest) }
      val iosSimulatorArm64Test by getting { dependsOn(iosTest) }
    }
  }
}

tasks.register("integrationTest") {
  group = org.gradle.language.base.plugins.LifecycleBasePlugin.VERIFICATION_GROUP
  description = "Runs desktop integration tests for this module."
  dependsOn("desktopIntegrationTest")
}

android {
  namespace = "dev.transmute.gstreamer"
  compileSdk = 35
  defaultConfig {
    minSdk = 26
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    // Build native JNI bridge only when the GStreamer Android SDK is present
    if (gstreamerAndroidRoot != null) {
      externalNativeBuild {
        cmake {
          arguments("-DGSTREAMER_ROOT_ANDROID=$gstreamerAndroidRoot")
        }
      }
      ndk {
        abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
      }
    }
  }
  if (gstreamerAndroidRoot != null) {
    externalNativeBuild {
      cmake {
        path = file("src/androidMain/cpp/CMakeLists.txt")
      }
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
}

tasks.withType<org.gradle.api.tasks.compile.JavaCompile>().configureEach {
  sourceCompatibility = "17"
  targetCompatibility = "17"
}

// Ensure GStreamer binaries are staged before desktop resources are processed.
// stageGStreamerDesktop is a no-op on Linux (where bundling is not supported)
// and is skipped when the marker file already exists (i.e. already staged).
// NOTE: KMP names this task processDesktopMainResources (process<Target>MainResources).
tasks.matching { it.name == "processDesktopMainResources" }.configureEach {
  dependsOn("stageGStreamerDesktop")
}

// Ensure GStreamer binaries are staged before the test JVM starts.
// stageGStreamerDesktop is a no-op on Linux (where bundling is not supported)
// and is skipped when the marker file already exists (i.e. already staged).
tasks.matching { it.name == "desktopIntegrationTest" }.configureEach {
  dependsOn("stageGStreamerDesktop")
}
