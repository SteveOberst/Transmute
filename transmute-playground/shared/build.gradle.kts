plugins {
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.kotlin.serialization)
}

kotlin {
  jvm()
  jvmToolchain(17)

  sourceSets {
    commonMain.dependencies {
      api(libs.kotlinx.serialization.core)
      api(project(":transmute-model:core"))
    }
    commonTest.dependencies {
      implementation(libs.kotlin.test)
    }
  }
}
