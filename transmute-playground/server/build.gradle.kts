plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    kotlin("jvm")
    alias(libs.plugins.kotlin.serialization)
    id("com.gradleup.shadow") version "9.0.0-beta12"
    application
}

application {
    mainClass.set("dev.transmute.playground.PlaygroundServerKt")
}

val ktorVersion = "3.1.3"

dependencies {
    // Transmute (full stack)
    implementation(project(":transmute-api"))
    implementation(project(":transmute-plugins:gstreamer"))
    implementation(project(":transmute-model:structure"))
    implementation(project(":transmute-structure"))

    // Shared models (JVM artifact)
    implementation(project(":transmute-playground:shared"))

    // Ktor Server
    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-server-websockets:$ktorVersion")
    implementation("io.ktor:ktor-server-cors:$ktorVersion")
    implementation("io.ktor:ktor-server-call-logging:$ktorVersion")
    implementation("io.ktor:ktor-server-status-pages:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")

    // Kotlin reflection (for annotation-driven transform discovery)
    implementation(kotlin("reflect"))

    // Coroutines
    implementation(libs.kotlinx.coroutines.core)

    // Logging
    implementation("ch.qos.logback:logback-classic:1.5.18")

    // Testing
    testImplementation(libs.kotlin.test)
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion")
}

kotlin {
    jvmToolchain(17)
}

tasks.shadowJar {
    archiveClassifier.set("all")
    mergeServiceFiles()
}
