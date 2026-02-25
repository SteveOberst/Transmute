package dev.transmute.filesystem

/**
 * Thrown when a file or directory is not found.
 */
class FileNotFoundException(
    message: String,
    val path: TPath? = null,
) : RuntimeException(message)

/**
 * Thrown when an operation expects a directory but the path is not one.
 */
class NotDirectoryException(
    message: String,
    val path: TPath? = null,
) : RuntimeException(message)

/**
 * Thrown when a file already exists and the operation does not allow overwriting.
 */
class FileAlreadyExistsException(
    message: String,
    val path: TPath? = null,
) : RuntimeException(message)
