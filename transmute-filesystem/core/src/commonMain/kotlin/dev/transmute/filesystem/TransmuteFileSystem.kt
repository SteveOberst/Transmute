@file:Suppress("unused")

package dev.transmute.filesystem

/**
 * Cross-platform filesystem abstraction with pluggable backends.
 *
 * Provides the core set of operations needed by Transmute for reading,
 * writing, and inspecting files and directories. Implementations map
 * these operations to a concrete filesystem (Okio, JVM NIO, platform
 * native, in-memory, etc.).
 *
 * ## Usage
 *
 * ```kotlin
 * fun processFile(fs: TransmuteFileSystem, path: TPath) {
 *     val meta = fs.metadata(path)
 *     if (!meta.isRegularFile) error("Not a file: $path")
 *
 *     val bytes = fs.read(path)
 *     // ... process bytes ...
 *
 *     fs.write(path.parent!! / "output.bin", processedBytes)
 * }
 * ```
 *
 * ## Design principles
 *
 * - **No platform types leak.** All parameters and return types are from
 *   the `dev.transmute.filesystem` package.
 * - **Simple by default, powerful when needed.** Bulk [read]/[write] for
 *   common cases; [openRead]/[openWrite] for streaming or random access.
 * - **Backend-agnostic.** Consumers program against this interface.
 *   The concrete backend (e.g. Okio) is selected at the composition root.
 */
interface TransmuteFileSystem {

  // -- Metadata ---

  /**
   * Check whether [path] exists.
   */
  fun exists(path: TPath): Boolean

  /**
   * Retrieve metadata for [path].
   *
   * @throws FileNotFoundException if the path does not exist.
   */
  fun metadata(path: TPath): FileMetadata

  /**
   * Retrieve metadata for [path], or `null` if it does not exist.
   */
  fun metadataOrNull(path: TPath): FileMetadata?

  // -- Bulk read / write ---

  /**
   * Read the entire file at [path] into a [ByteArray].
   *
   * @throws FileNotFoundException if the path does not exist.
   */
  fun read(path: TPath): ByteArray

  /**
   * Write [data] to [path] according to [mode].
   */
  fun write(path: TPath, data: ByteArray, mode: WriteMode = WriteMode.Overwrite)

  // -- Streaming / random-access ---

  /**
   * Open [path] for random-access reading.
   *
   * The caller is responsible for closing the returned [ReadHandle].
   *
   * @throws FileNotFoundException if the path does not exist.
   */
  fun openRead(path: TPath): ReadHandle

  /**
   * Open [path] for writing.
   *
   * The caller is responsible for closing the returned [WriteHandle].
   */
  fun openWrite(path: TPath, mode: WriteMode = WriteMode.Overwrite): WriteHandle

  // -- Directory operations ---

  /**
   * List the immediate children of the directory at [path].
   *
   * @throws FileNotFoundException if the path does not exist.
   * @throws NotDirectoryException if the path is not a directory.
   */
  fun list(path: TPath): List<TPath>

  /**
   * Create a directory at [path].
   *
   * When [recursive] is `true`, all missing parent directories are
   * created as well (like `mkdir -p`).
   */
  fun createDirectory(path: TPath, recursive: Boolean = false)

  // -- Delete ---

  /**
   * Delete the file or directory at [path].
   *
   * When [recursive] is `true` and [path] is a directory, all
   * contents are deleted first.
   */
  fun delete(path: TPath, recursive: Boolean = false)

  // -- Copy / Move ---

  /**
   * Copy [source] to [target].
   *
   * If [overwrite] is `false` and [target] already exists, the
   * operation fails.
   */
  fun copy(source: TPath, target: TPath, overwrite: Boolean = false)

  /**
   * Move (rename) [source] to [target].
   *
   * If [overwrite] is `false` and [target] already exists, the
   * operation fails.
   */
  fun move(source: TPath, target: TPath, overwrite: Boolean = false)
}
