pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Transmute"

include(
    ":transmute-api",
    ":transmute-common",
    ":transmute-codec",
    ":transmute-model",
    ":transmute-model:core",
    ":transmute-model:identify",
    ":transmute-model:structure",
    ":transmute-model:view",
    ":transmute-model:stream",
    ":transmute-model:metadata",
    ":transmute-model:diagnostics",
    ":transmute-filesystem",
    ":transmute-filesystem:core",
    ":transmute-filesystem:okio",
    ":transmute-structure",
    ":transmute-audio",
    ":transmute-video",
    ":transmute-image",
    ":transmute-gstreamer",
)
