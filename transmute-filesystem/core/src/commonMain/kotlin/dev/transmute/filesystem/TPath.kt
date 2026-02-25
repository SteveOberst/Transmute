@file:Suppress("unused")

package dev.transmute.filesystem

/**
 * Cross-platform path representation.
 *
 * A [TPath] is a sequence of [segments] with an optional [root] prefix
 * (e.g. `"/"` on Unix, `"C:\\"` on Windows). Paths are manipulation-only —
 * they carry no reference to any specific filesystem.
 *
 * ```kotlin
 * val config = TPath("/etc") / "transmute" / "config.toml"
 * println(config)           // /etc/transmute/config.toml
 * println(config.name)      // config.toml
 * println(config.extension) // toml
 * println(config.parent)    // /etc/transmute
 * ```
 */
data class TPath(
    /** Path segments in order. */
    val segments: List<String>,
    /**
     * Filesystem root, if this path is absolute.
     *
     * - Unix: `"/"`
     * - Windows: `"C:\\"` or similar drive letter prefix
     * - `null` for relative paths
     */
    val root: String? = null,
) {
    /** Whether this path is absolute (has a [root]). */
    val isAbsolute: Boolean get() = root != null

    /** The final segment of the path, or empty string for empty paths. */
    val name: String get() = segments.lastOrNull() ?: ""

    /** The file extension (after the last `.`), or empty string. */
    val extension: String
        get() {
            val n = name
            val dot = n.lastIndexOf('.')
            return if (dot > 0) n.substring(dot + 1) else ""
        }

    /** The stem (name without extension), or the full name if no extension. */
    val stem: String
        get() {
            val n = name
            val dot = n.lastIndexOf('.')
            return if (dot > 0) n.substring(0, dot) else n
        }

    /** The parent path (all segments except the last), or `null` for roots / empty paths. */
    val parent: TPath?
        get() = if (segments.size > 1) {
            TPath(segments.dropLast(1), root)
        } else if (segments.size == 1 && root != null) {
            TPath(emptyList(), root)
        } else {
            null
        }

    /**
     * Append a child segment.
     *
     * ```kotlin
     * val child = TPath("/home") / "user" / "docs"
     * ```
     */
    operator fun div(child: String): TPath {
        require(child.isNotEmpty()) { "Child segment must not be empty" }
        return TPath(segments + child, root)
    }

    /**
     * Resolve [other] relative to this path.
     *
     * If [other] is absolute, it is returned as-is.
     * Otherwise its segments are appended to this path.
     */
    fun resolve(other: TPath): TPath =
        if (other.isAbsolute) other
        else TPath(segments + other.segments, root)

    /**
     * Normalize the path by resolving `.` and `..` segments.
     */
    fun normalize(): TPath {
        val result = mutableListOf<String>()
        for (seg in segments) {
            when (seg) {
                "." -> { /* skip */ }
                ".." -> if (result.isNotEmpty() && result.last() != "..") result.removeLast()
                        else if (root == null) result.add(seg)
                else -> result.add(seg)
            }
        }
        return TPath(result, root)
    }

    /**
     * String representation using `/` as separator.
     *
     * Absolute paths are prefixed with their [root]; relative paths start directly
     * with the first segment.
     */
    override fun toString(): String {
        val prefix = root ?: ""
        return if (prefix.endsWith("/") || prefix.endsWith("\\")) {
            prefix + segments.joinToString("/")
        } else if (prefix.isNotEmpty()) {
            prefix + "/" + segments.joinToString("/")
        } else {
            segments.joinToString("/")
        }
    }

    companion object {
        /** Parse a string path into a [TPath]. */
        fun of(path: String): TPath {
            if (path.isEmpty()) return TPath(emptyList())

            // Detect root
            val (root, rest) = when {
                // Unix absolute
                path.startsWith("/") -> "/" to path.removePrefix("/")
                // Windows drive letter (e.g. "C:\..." or "C:/...")
                path.length >= 3 && path[1] == ':' && (path[2] == '/' || path[2] == '\\') ->
                    path.substring(0, 3) to path.substring(3)
                else -> null to path
            }

            val segments = rest.split('/', '\\').filter { it.isNotEmpty() }
            return TPath(segments, root)
        }
    }
}
