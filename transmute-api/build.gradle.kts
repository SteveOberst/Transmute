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
    listOf(iosX64(), iosArm64(), iosSimulatorArm64()).forEach {
      it.binaries.framework {
        baseName = "transmute-api"
        isStatic = true
      }
    }
  }

  sourceSets {
    commonMain.dependencies {
      api(project(":transmute-codec"))
      api(project(":transmute-audio"))
      api(project(":transmute-image"))
      api(project(":transmute-video"))
      api(project(":transmute-model:structure"))
      api(project(":transmute-model:metadata"))
      api(project(":transmute-structure"))
      implementation(libs.kotlinx.coroutines.core)
    }
    commonTest.dependencies {
      implementation(libs.kotlin.test)
      implementation(libs.kotlinx.coroutines.test)
    }

    // JVM-only helpers for Java-friendly IO/Blocking APIs
    val desktopMain by getting
    val desktopTest by getting {
      dependencies {
        implementation(libs.kotlin.test)
        implementation(libs.kotlinx.coroutines.test)
      }
    }
    val desktopIntegrationTest by getting {
      dependencies {
        implementation(libs.kotlin.test)
        implementation(libs.kotlinx.coroutines.test)
      }
    }
    val androidMain by getting
    desktopMain.dependencies {
      implementation(libs.kotlinx.coroutines.core)
    }
    androidMain.dependencies {
      implementation(libs.kotlinx.coroutines.core)
    }
  }
}
tasks.register("integrationTest") {
  group = org.gradle.language.base.plugins.LifecycleBasePlugin.VERIFICATION_GROUP
  description = "Runs desktop integration tests for this module."
  dependsOn("desktopIntegrationTest")
}

android {
  namespace = "dev.transmute.api"
  compileSdk = 35
  defaultConfig { minSdk = 26 }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
}
