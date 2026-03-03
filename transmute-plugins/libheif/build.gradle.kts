plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    `maven-publish`
}

apply(from = "libheif-sdk.gradle.kts")

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
                baseName = "transmute-libheif"
                isStatic = true
            }
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":transmute-api"))
            api(project(":transmute-image"))
            api(project(":transmute-filesystem:core"))
            api(project(":transmute-plugins:catalog"))
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }

        val androidMain by getting
        val desktopMain by getting {
            // Staged libheif binaries are bundled into the desktop JAR as classpath resources.
            // Run `./gradlew :transmute-plugins:libheif:stageLibHeifDesktop` (once per version,
            // per platform) to populate build/libheif-desktop/ before building a distribution.
            // Windows: requires vcpkg (VCPKG_ROOT or PATH). macOS: requires `brew install libheif`.
            // For local development without staging, use installFrom() or useSystemInstallation()
            // in your Transmute config to point at an existing libheif installation.
            resources.srcDir(layout.buildDirectory.dir("libheif-desktop"))
        }
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
    namespace = "dev.transmute.libheif"
    compileSdk = 35
    defaultConfig {
        minSdk = 26
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

// Wire the vcpkg/Homebrew staging task so that desktop resource processing
// automatically downloads and bundles libheif binaries into the JAR.
tasks.matching { it.name == "desktopProcessResources" }.configureEach {
    dependsOn("stageLibHeifDesktop")
}

// Ensure libheif binaries are staged before the test JVM starts.
// stageLibHeifDesktop is a no-op when already staged (marker file present).
tasks.matching { it.name == "desktopTest" }.configureEach {
    dependsOn("stageLibHeifDesktop")
}
