plugins {
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.android.library)
  `maven-publish`
}

// Unique group to avoid GAV collision with :transmute-filesystem:core
group = "${rootProject.group}.model"

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
        baseName = "transmute-model-core"
        isStatic = true
      }
    }
  }

  sourceSets {
    commonMain.dependencies {
      api(libs.kotlinx.serialization.core)
      api(libs.kotlinx.serialization.json)
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
    }
  }
}

android {
  namespace = "dev.transmute.model.core"
  compileSdk = 35
  defaultConfig { minSdk = 26 }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
}
