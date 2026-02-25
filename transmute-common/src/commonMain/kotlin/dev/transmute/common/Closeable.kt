package dev.transmute.common

/**
 * Simple multiplatform close contract.
 *
 * This intentionally does not depend on platform types like `java.io.Closeable`.
 */
interface Closeable {
  /** Release any held resources. Must be idempotent. */
  fun close()
}
