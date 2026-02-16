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
    ":transmute-core",
    ":transmute-audio",
    ":transmute-video",
    ":transmute-image",
)
