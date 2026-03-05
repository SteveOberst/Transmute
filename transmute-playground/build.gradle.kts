// Umbrella module for the Transmute Playground.
// Sub-modules: shared, server, web (Next.js - not a Gradle module)

/* -- Dev tasks: start frontend & backend for local development ------------- */

import org.gradle.api.tasks.SourceSetContainer
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainService

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
  dependsOn(":transmute-playground:server:classes")

  doLast {
    val webDir = file("web")

    /**
     * Kill any process currently listening on [port].
     * Called before launching child processes so stale leftovers from a
     * previous forcibly-killed run don't block the new one.
     */
    fun killPort(port: Int) {
      try {
        if (isWindows) {
          // netstat -ano columns: Proto  LocalAddr  ForeignAddr  State  PID
          val netstat = ProcessBuilder("netstat", "-ano")
            .redirectErrorStream(true).start()
          val lines = netstat.inputStream.bufferedReader().readLines()
          netstat.waitFor()
          val portRegex = Regex("\\bLISTENING\\b")
          val addrRegex = Regex("\\:${port}\\b")
          val pids = lines
            .filter { addrRegex.containsMatchIn(it) && portRegex.containsMatchIn(it) }
            .mapNotNull { line ->
              line.trimEnd().split("\\s+".toRegex()).lastOrNull()?.toLongOrNull()
            }
            .distinct()
          for (pid in pids) {
            println("  > Killing stale process on port $port (PID $pid)")
            ProcessBuilder("taskkill", "/F", "/T", "/PID", pid.toString())
              .redirectErrorStream(true).start().waitFor()
          }
        } else {
          val lsof = ProcessBuilder("lsof", "-ti", "tcp:$port")
            .redirectErrorStream(true).start()
          val pids = lsof.inputStream.bufferedReader().readLines()
            .mapNotNull { it.trim().toLongOrNull() }
          lsof.waitFor()
          for (pid in pids) {
            println("  > Killing stale process on port $port (PID $pid)")
            ProcessHandle.of(pid).ifPresent { it.destroyForcibly() }
          }
        }
      } catch (_: Exception) {
        // Best-effort - don't fail the task if port cleanup errors
      }
    }

    /** Gracefully terminate a process tree: SIGTERM -> 3 s wait -> SIGKILL */
    fun destroyTree(process: Process) {
      if (isWindows) {
        // On Windows, npm.cmd/cmd.exe can spawn grandchildren that ProcessHandle descendants
        // doesn't always catch reliably; taskkill /T is the most dependable option.
        val pid = runCatching { process.pid() }.getOrNull()
        if (pid != null) {
          runCatching {
            ProcessBuilder("taskkill", "/F", "/T", "/PID", pid.toString())
              .redirectErrorStream(true)
              .start()
              .waitFor()
          }
        } else {
          runCatching { process.destroyForcibly() }
        }
        return
      }

      // Collect descendants eagerly before signalling the root
      val children = process.toHandle().descendants()
        .collect(java.util.stream.Collectors.toList())
      children.forEach { it.destroy() }
      process.destroy()
      val deadline = System.currentTimeMillis() + 3_000L
      while ((children.any { it.isAlive } || process.isAlive) &&
        System.currentTimeMillis() < deadline
      ) {
        Thread.sleep(100)
      }
      children.filter { it.isAlive }.forEach { it.destroyForcibly() }
      if (process.isAlive) process.destroyForcibly()
    }

    fun streamPrefixed(prefix: String, process: Process) {
      Thread {
        process.inputStream.bufferedReader().forEachLine { println("$prefix$it") }
      }.apply {
        isDaemon = true
        start()
      }
    }

    // Kill any stale port occupants left over from a previous forcibly-stopped run
    killPort(3000)
    killPort(8080)
    if (isWindows) Thread.sleep(500) // give Windows a moment to release the ports

    // Start both processes. IMPORTANT: do not spawn a nested Gradle build (gradlew ...:server:run),
    // because that process can outlive this build when the dev task is cancelled.

    val frontend = ProcessBuilder(npmCmd, "run", "dev")
      .directory(webDir)
      .redirectErrorStream(true)
      .start()

    val serverProject = project(":transmute-playground:server")
    val sourceSets = serverProject.extensions.getByType(SourceSetContainer::class.java)
    val mainSourceSet = sourceSets.getByName("main")
    val runtimeClasspath = mainSourceSet.runtimeClasspath

    val toolchains = serverProject.extensions.getByType(JavaToolchainService::class.java)
    val javaLauncher = toolchains.launcherFor {
      languageVersion.set(JavaLanguageVersion.of(17))
    }.get()
    val javaExe = javaLauncher.executablePath.asFile.absolutePath
    val mainClass = "dev.transmute.playground.PlaygroundServerKt"

    val backend = ProcessBuilder(
      javaExe,
      "-cp",
      runtimeClasspath.asPath,
      mainClass,
    )
      .directory(rootDir)
      .redirectErrorStream(true)
      .start()

    streamPrefixed("[frontend] ", frontend)
    streamPrefixed("[server]   ", backend)

    println()
    println("  > Frontend: http://localhost:3000")
    println("  > Backend:  http://localhost:8080")
    println("  > Press Ctrl+C to stop both")
    println()

    // Block until either process exits.
    // Use try/finally so Gradle cancellation (Ctrl+C / IDE stop) reliably cleans up child processes.
    val latch = java.util.concurrent.CountDownLatch(1)
    listOf(frontend, backend).forEach { proc ->
      Thread {
        proc.waitFor()
        latch.countDown()
      }.apply {
        isDaemon = true
        start()
      }
    }

    try {
      latch.await()
    } catch (e: InterruptedException) {
      Thread.currentThread().interrupt()
    } finally {
      println("\n  > Shutting down...")
      runCatching { destroyTree(frontend) }
      runCatching { destroyTree(backend) }
    }
  }
}
