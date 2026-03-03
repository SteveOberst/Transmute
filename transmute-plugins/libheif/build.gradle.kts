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

// ---------------------------------------------------------------------------
// Desktop integration tests require a working libheif installation.
// Rather than soft-skipping individual tests at runtime, we disable the
// desktopTest task entirely when libheif isn't usable on this machine.
//
// Override:
//   TRANSMUTE_LIBHEIF_TESTS=on   -> always run
//   TRANSMUTE_LIBHEIF_TESTS=off  -> always skip
// ---------------------------------------------------------------------------
val libheifTestsOverride: String? =
    (findProperty("transmute.libheif.tests") as? String)
        ?: System.getenv("TRANSMUTE_LIBHEIF_TESTS")

val libheifDesktopUsable: Boolean by lazy {
    when (libheifTestsOverride?.trim()?.lowercase()) {
        "1", "true", "on", "force", "enable", "enabled" -> return@lazy true
        "0", "false", "off", "disable", "disabled" -> return@lazy false
        else -> Unit
    }
    // Probe for a working heif-dec (or heif-convert) on PATH.
    listOf("heif-dec", "heif-convert").any { bin ->
        try {
            val proc = ProcessBuilder(bin, "--version")
                .redirectErrorStream(true)
                .start()
            proc.inputStream.bufferedReader().readText()
            proc.waitFor() == 0
        } catch (_: Exception) { false }
    }
}

tasks.matching { it.name == "desktopTest" }.configureEach {
    onlyIf {
        val usable = libheifDesktopUsable
        if (!usable) {
            logger.lifecycle("SKIP desktopTest: libheif not usable on this machine (set TRANSMUTE_LIBHEIF_TESTS=on to override)")
        }
        usable
    }
}

// When desktopTest is skipped its binary results directory won't exist,
// which causes the KMP allTests aggregate TestReport to fail.
tasks.matching { it.name == "allTests" }.configureEach {
    onlyIf { libheifDesktopUsable }
}
