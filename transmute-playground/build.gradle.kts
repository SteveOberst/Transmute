// Umbrella module for the Transmute Playground.
// Sub-modules: shared, server, web (Next.js — not a Gradle module)

/* ── Dev tasks: start frontend & backend for local development ───────────── */

val isWindows = org.gradle.internal.os.OperatingSystem.current().isWindows
val npmCmd = if (isWindows) "npm.cmd" else "npm"

tasks.register<Exec>("frontendInstall") {
    group = "playground"
    description = "Install npm dependencies for the web frontend"
    workingDir = file("web")
    commandLine(npmCmd, "install")
    inputs.file("web/package.json")
    outputs.dir("web/node_modules")
}

tasks.register<Exec>("frontendDev") {
    group = "playground"
    description = "Start the Next.js dev server on port 3000"
    dependsOn("frontendInstall")
    workingDir = file("web")
    commandLine(npmCmd, "run", "dev")
}

tasks.register<Exec>("frontendBuild") {
    group = "playground"
    description = "Build the Next.js static export"
    dependsOn("frontendInstall")
    workingDir = file("web")
    commandLine(npmCmd, "run", "build")
}

tasks.register("dev") {
    group = "playground"
    description = "Start Next.js dev server and Ktor backend together"
    dependsOn("frontendInstall")

    doLast {
        val webDir = file("web")

        /** Gracefully terminate a process tree: SIGTERM → 3 s wait → SIGKILL */
        fun destroyTree(process: Process) {
            // Collect descendants eagerly before signalling the root
            val children = process.toHandle().descendants()
                .collect(java.util.stream.Collectors.toList())
            children.forEach { it.destroy() }
            process.destroy()
            val deadline = System.currentTimeMillis() + 3_000L
            while ((children.any { it.isAlive } || process.isAlive) &&
                   System.currentTimeMillis() < deadline) {
                Thread.sleep(100)
            }
            children.filter { it.isAlive }.forEach { it.destroyForcibly() }
            if (process.isAlive) process.destroyForcibly()
        }

        // Start Next.js dev server (port 3000)
        val frontend = ProcessBuilder(npmCmd, "run", "dev")
            .directory(webDir)
            .redirectErrorStream(true)
            .start()

        // Start Ktor server via Gradle, passing the current build JDK so the
        // server runtime JVM matches the compile-time toolchain and avoids
        // UnsupportedClassVersionError when loading shared-module classes.
        val gradlew = if (org.gradle.internal.os.OperatingSystem.current().isWindows) "gradlew.bat" else "gradlew"
        val javaHome = System.getProperty("java.home")
        val backendCmd = buildList {
            add(file("../$gradlew").absolutePath)
            add(":transmute-playground:server:run")
            add("--no-daemon")
            if (javaHome != null) add("-Dorg.gradle.java.home=$javaHome")
        }
        val backend = ProcessBuilder(backendCmd)
            .directory(rootDir)
            .redirectErrorStream(true)
            .start()

        // Stream output with prefixes
        Thread { frontend.inputStream.bufferedReader().forEachLine { println("[frontend] $it") } }
            .apply { isDaemon = true; start() }
        Thread { backend.inputStream.bufferedReader().forEachLine { println("[server]   $it") } }
            .apply { isDaemon = true; start() }

            println()
            println("  ▸ Frontend: http://localhost:3000")
            println("  ▸ Backend:  http://localhost:8080")
            println("  ▸ Press Ctrl+C to stop both")
            println()

        Runtime.getRuntime().addShutdownHook(Thread {
            println("\n  ▸ Shutting down…")
            destroyTree(frontend)
            destroyTree(backend)
        })

        // Block until either process exits; shutdown hook handles cleanup of the other
        val latch = java.util.concurrent.CountDownLatch(1)
        listOf(frontend, backend).forEach { proc ->
            Thread { proc.waitFor(); latch.countDown() }.apply { isDaemon = true; start() }
        }
        latch.await()
    }
}
