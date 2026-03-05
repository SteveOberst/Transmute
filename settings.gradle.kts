pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    @Suppress("UnstableApiUsage")
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
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
    ":transmute-model:metadata",
    ":transmute-filesystem",
    ":transmute-filesystem:core",
    ":transmute-filesystem:okio",
    ":transmute-structure",
    ":transmute-audio",
    ":transmute-video",
    ":transmute-image",
    ":transmute-testing",
    ":transmute-plugins",
    ":transmute-plugins:catalog",
    ":transmute-plugins:gstreamer",
    ":transmute-plugins:libheif",
    ":transmute-playground",
    ":transmute-playground:shared",
    ":transmute-playground:server",
)
