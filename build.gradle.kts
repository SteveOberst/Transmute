plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.android.library) apply false
}

import dev.transmute.build.ProjectVersion

group = "com.github.SteveOberst.Transmute"
version = ProjectVersion.resolve(rootDir) // x-release-please-version

subprojects {
    group = rootProject.group
    version = rootProject.version
}
