package dev.transmute.filesystem

/**
 * Controls how a file is opened for writing.
 */
enum class WriteMode {
  /** Create a new file. Fails if the file already exists. */
  Create,

  /** Overwrite the file if it exists; create it if not. */
  Overwrite,

  /** Append to the file if it exists; create it if not. */
  Append,
}
