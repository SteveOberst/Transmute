import dev.transmute.gradle.ProjectVersion
import org.gradle.api.publish.PublishingExtension

plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.android.library) apply false
}

group = "com.github.SteveOberst.Transmute"
version = ProjectVersion.resolve(rootDir) // x-release-please-version

subprojects {
    group = rootProject.group
    version = rootProject.version

    // Wire GitHub Packages as the publish target for all modules that apply maven-publish.
    // Credentials come from GITHUB_USERNAME / GITHUB_TOKEN environment variables.
    // For local development set these in your shell or in ~/.gradle/gradle.properties as
    // gpr.user / gpr.key, then reference them below.
    afterEvaluate {
        extensions.findByType<PublishingExtension>()?.apply {
            repositories {
                maven {
                    name = "GitHubPackages"
                    url = uri("https://maven.pkg.github.com/SteveOberst/Transmute")
                    credentials {
                        username = System.getenv("GITHUB_USERNAME")
                            ?: (project.findProperty("gpr.user") as? String)
                        password = System.getenv("GITHUB_TOKEN")
                            ?: (project.findProperty("gpr.key") as? String)
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Convenience tasks for building/testing without the transmute-plugins subproject.
//
// Useful on machines where GStreamer or libheif are not installed.
//
//   ./gradlew coreBuild   -- compiles + assembles every non-plugin module
//   ./gradlew coreTests   -- runs desktopTest for every non-plugin module
// ---------------------------------------------------------------------------

val coreSubprojects: List<Project> by lazy {
    subprojects.filter { !it.path.startsWith(":transmute-plugins") }
}

tasks.register("coreBuild") {
    group = "build"
    description = "Builds all modules except transmute-plugins (no GStreamer/libheif required)."
    dependsOn(coreSubprojects.map { "${it.path}:assemble" })
}

tasks.register("coreTests") {
    group = "verification"
    description = "Runs desktopTest for all modules except transmute-plugins (no GStreamer/libheif required)."
    dependsOn(coreSubprojects.map { "${it.path}:desktopTest" })
}
