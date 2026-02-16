plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.android.library) apply false
}

group = "com.github.SteveOberst.Transmute"
version = "0.1.2" // x-release-please-version

subprojects {
    group = rootProject.group
    version = rootProject.version
}
