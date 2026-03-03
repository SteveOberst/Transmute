package dev.transmute.gradle

import org.gradle.api.GradleException
import org.gradle.api.logging.Logger
import java.io.File

// ---------------------------------------------------------------------------
// vcpkg detection utilities
// ---------------------------------------------------------------------------
// Used by modules that provision native libraries via vcpkg at build time.
// Only the Gradle classloader needs these; no Kotlin Multiplatform targets.
// ---------------------------------------------------------------------------

/**
 * Locates the vcpkg executable.
 *
 * Resolution order:
 *  1. `VCPKG_ROOT` environment variable (most reliable -- set by vcpkg bootstraps)
 *  2. `PATH` search
 *
 * @return the absolute [File] pointing to the vcpkg executable, or `null` if not found.
 */
fun findVcpkg(): File? {
    val isWindows = System.getProperty("os.name", "").lowercase().startsWith("windows")

    // 1. VCPKG_ROOT env var
    val vcpkgRoot = System.getenv("VCPKG_ROOT")
    if (!vcpkgRoot.isNullOrBlank()) {
        val names = if (isWindows) listOf("vcpkg.exe") else listOf("vcpkg")
        for (name in names) {
            val candidate = File(vcpkgRoot, name)
            if (candidate.isFile) return candidate
        }
    }

    // 2. PATH search
    val pathDirs = System.getenv("PATH")?.split(File.pathSeparatorChar) ?: emptyList()
    val names = if (isWindows) listOf("vcpkg.exe", "vcpkg.cmd", "vcpkg") else listOf("vcpkg")
    for (dir in pathDirs) {
        for (name in names) {
            val candidate = File(dir, name)
            if (candidate.isFile) return candidate
        }
    }

    return null
}

/**
 * Returns the vcpkg root directory inferred from the [vcpkgExe] path.
 *
 * vcpkg is always located directly inside its root directory, so the root is
 * simply the parent of the executable.
 */
fun vcpkgRootFrom(vcpkgExe: File): File = vcpkgExe.parentFile

/**
 * Returns `true` if [pkg] is already installed under [vcpkgRoot] for [triplet].
 *
 * Checks for the presence of the `usage` or at least one header/DLL under
 * `installed/<triplet>/` -- a lightweight proxy that avoids running `vcpkg list`.
 */
fun isVcpkgPackageInstalled(vcpkgRoot: File, pkg: String, triplet: String): Boolean {
    // vcpkg places a per-package "usage" file at installed/<triplet>/share/<pkg>/usage
    val usageFile = File(vcpkgRoot, "installed/$triplet/share/${pkg.substringBefore('[')}/usage")
    return usageFile.exists()
}

/**
 * Asserts that vcpkg is installed and on the machine, throwing a [GradleException]
 * with step-by-step installation instructions if it is not found.
 *
 * @param logger optional Gradle logger; if supplied, a warning is printed before throwing.
 * @return the resolved vcpkg executable [File].
 */
fun requireVcpkg(logger: Logger? = null): File {
    val exe = findVcpkg()
    if (exe != null) return exe

    val message = """
        vcpkg not found.

        vcpkg is Microsoft's open-source C/C++ package manager and is required
        to provision native libraries (e.g. libheif) for this project.

        How to install vcpkg
        --------------------
        Official guide: https://learn.microsoft.com/en-us/vcpkg/get_started/get-started

        Quick install (Windows -- PowerShell):
            git clone https://github.com/microsoft/vcpkg "${'$'}env:USERPROFILE\vcpkg"
            & "${'$'}env:USERPROFILE\vcpkg\bootstrap-vcpkg.bat"
            # Then either:
            #   Set-Item Env:VCPKG_ROOT "${'$'}env:USERPROFILE\vcpkg"  (current session)
            #   [System.Environment]::SetEnvironmentVariable('VCPKG_ROOT', "${'$'}env:USERPROFILE\vcpkg", 'User')
            #   Add %USERPROFILE%\vcpkg to your PATH (for 'vcpkg' to resolve without VCPKG_ROOT)

        Quick install (macOS/Linux):
            git clone https://github.com/microsoft/vcpkg ~/vcpkg
            ~/vcpkg/bootstrap-vcpkg.sh
            export VCPKG_ROOT=~/vcpkg        # add to ~/.bashrc or ~/.zshrc for persistence
            export PATH="${'$'}PATH:~/vcpkg"

        After installing, either set the VCPKG_ROOT environment variable to your
        vcpkg directory OR add vcpkg to your PATH, then re-run the Gradle task.
    """.trimIndent()

    logger?.error(message)
    throw GradleException(message)
}
