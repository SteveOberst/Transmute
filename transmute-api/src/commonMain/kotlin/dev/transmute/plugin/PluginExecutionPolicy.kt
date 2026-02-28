package dev.transmute.plugin

/**
 * Declares the concurrency model a plugin prefers for its codec operations.
 *
 * Many native codec libraries (e.g. FFmpeg, GStreamer, VideoToolbox) use thread-local
 * or global state that makes them unsafe to call from multiple threads simultaneously.
 * Plugins implement [PluginExecutionPolicy] to tell the Transmute runtime how to
 * schedule operations on their behalf:
 *
 * ```kotlin
 * object MyPlugin : TransmutePlugin<MyConfig>, PluginExecutionPolicy {
 *     override val key = pluginId("com.example.my-plugin")
 *
 *     // This plugin wraps a native library that is not thread-safe.
 *     override val executionPolicy: ExecutionPolicy = ExecutionPolicy.SingleThreaded
 * }
 * ```
 *
 * The Transmute runtime is not required to enforce this policy itself — it is
 * informational and intended for dispatchers (e.g. a server-side coroutine dispatcher)
 * that wrap codec calls.
 */
interface PluginExecutionPolicy {
    /**
     * The execution policy this plugin requires.
     *
     * Defaults to [ExecutionPolicy.Default] (no constraints).
     */
    val executionPolicy: ExecutionPolicy get() = ExecutionPolicy.Default
}

/**
 * Describes the concurrency constraints a plugin has on its codec operations.
 */
sealed class ExecutionPolicy {

    /**
     * No special constraints — the runtime may call codec operations from any
     * thread or coroutine context it chooses.
     */
    data object Default : ExecutionPolicy()

    /**
     * The plugin's codec operations **must** run sequentially on a single thread.
     *
     * Use this for native libraries that maintain thread-local or global mutable state.
     */
    data object SingleThreaded : ExecutionPolicy()

    /**
     * The plugin's codec operations may run concurrently, but no more than [limit]
     * operations at the same time.
     *
     * @param limit Maximum number of simultaneous codec operations. Must be ≥ 1.
     */
    data class MaxParallelism(val limit: Int) : ExecutionPolicy() {
        init { require(limit >= 1) { "MaxParallelism limit must be ≥ 1, got $limit" } }
    }
}
