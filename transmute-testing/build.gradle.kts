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
        baseName = "transmute-testing"
        isStatic = true
      }
    }
  }

  sourceSets {
    commonMain.dependencies {
      api(project(":transmute-common"))
      api(project(":transmute-audio"))
      api(project(":transmute-image"))
      api(project(":transmute-video"))
      implementation(libs.kotlinx.coroutines.core)
    }
    commonTest.dependencies {
      implementation(libs.kotlin.test)
      implementation(libs.kotlinx.coroutines.test)
    }

    val desktopMain by getting
    val desktopTest by getting

    if (isMac) {
      val iosMain by creating { dependsOn(commonMain.get()) }
      val iosX64Main by getting { dependsOn(iosMain) }
      val iosArm64Main by getting { dependsOn(iosMain) }
      val iosSimulatorArm64Main by getting { dependsOn(iosMain) }
    }
  }
}

android {
  namespace = "dev.transmute.testing"
  compileSdk = 35
  defaultConfig { minSdk = 26 }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
}
