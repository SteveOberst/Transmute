plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    `maven-publish`
}

// -- Optional GStreamer SDK locations -----------------------------------------
val gstreamerAndroidRoot: String? = System.getenv("GSTREAMER_ROOT_ANDROID")
val gstreamerIosFramework = file("/Library/Frameworks/GStreamer.framework")

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
    }

    if (isMac) {
        listOf(iosX64(), iosArm64(), iosSimulatorArm64()).forEach { target ->
            target.binaries.framework {
                baseName = "transmute-gstreamer"
                isStatic = true
            }
            // Set up GStreamer cinterop when the framework is present
            if (gstreamerIosFramework.exists()) {
                target.compilations["main"].cinterops.create("gstreamer") {
                    defFile(project.file("src/nativeInterop/cinterop/gstreamer.def"))
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

        val desktopMain by getting
        val desktopTest by getting

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
