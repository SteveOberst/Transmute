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
    listOf(iosX64(), iosArm64(), iosSimulatorArm64()).forEach { target ->
      target.binaries.framework {
        baseName = "transmute-plugins-catalog"
        isStatic = true
      }
    }
  }

  sourceSets {
    commonMain.dependencies {
      // Re-export all first-party plugins - catalog consumers get everything
      // with a single dependency on this module.
      api(project(":transmute-api"))
    }
    commonTest.dependencies {
      implementation(libs.kotlin.test)
    }
  }
}

android {
  namespace = "dev.transmute.plugins.catalog"
  compileSdk = 35
  defaultConfig {
    minSdk = 21
  }
}
