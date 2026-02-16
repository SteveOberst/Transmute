plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    `maven-publish`
}

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
        listOf(iosX64(), iosArm64(), iosSimulatorArm64()).forEach {
            it.binaries.framework {
                baseName = "transmute-audio"
                isStatic = true
            }
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":transmute-core"))
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }

        val androidMain by getting
        val desktopMain by getting
        val desktopTest by getting

        desktopMain.dependencies {
            // MP3 decoding (pure JVM)
            implementation("javazoom:jlayer:1.0.1")
            // MP3 encoding (pure-Java LAME port)
            implementation("de.sciss:jump3r:1.0.4")
            // FLAC decoding
            implementation("org.jflac:jflac-codec:1.5.2")
            // OGG/Vorbis decoding
            implementation("org.jcraft:jorbis:0.0.17")
        }

        androidMain.dependencies {
            // MP3 encoding via pure-Java LAME port (low-level API, no javax.sound)
            implementation("de.sciss:jump3r:1.0.4")
        }

        val androidInstrumentedTest by getting {
            dependsOn(commonTest.get())
            dependencies {
                implementation("androidx.test.ext:junit:1.1.5")
                implementation("androidx.test:runner:1.5.2")
                implementation(libs.kotlinx.coroutines.test)
            }
        }

        if (isMac) {
            val iosMain by creating {
                dependsOn(commonMain.get())
            }
            val iosX64Main by getting { dependsOn(iosMain) }
            val iosArm64Main by getting { dependsOn(iosMain) }
            val iosSimulatorArm64Main by getting { dependsOn(iosMain) }
        }
    }
}

android {
    namespace = "dev.transmute.audio"
    compileSdk = 35
    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
