@file:Suppress("unused")

package dev.transmute.filesystem

/**
 * Metadata about a file or directory.
 *
 * Returned by [TransmuteFileSystem.metadata] and
 * [TransmuteFileSystem.metadataOrNull].
 */
data class FileMetadata(
  /** File size in bytes. `0` for directories. */
  val size: Long,

  /**
   * Last modification time in epoch milliseconds, or `null` if
   * the filesystem does not support it.
   */
  val lastModifiedMillis: Long? = null,

  /**
   * Creation time in epoch milliseconds, or `null` if the filesystem
   * does not support it.
   */
  val createdMillis: Long? = null,

  /** `true` if this path points to a regular file. */
  val isRegularFile: Boolean = false,

  /** `true` if this path points to a directory. */
  val isDirectory: Boolean = false,

  /** `true` if this path is a symbolic link. */
  val isSymlink: Boolean = false,
)
