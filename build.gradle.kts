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
