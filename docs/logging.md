# Logging

Transmute uses a two-layer logging system:

1. **Global configuration** via `TransmuteLogging` — controls what gets emitted library-wide.
2. **Per-operation overrides** — attach a custom logger to individual transmuter builds or via `TransmuteContext`.

## Log levels

```kotlin
enum class LogLevel {
    DEBUG,  // Codec internals, buffer sizes, stage timing
    INFO,   // Pipeline progress, format detection results
    WARN,   // Codec fallback, missing encoder (default minimum)
    ERROR,  // Errors that may still allow the pipeline to complete
    OFF,    // Silence all output
}
```

## Global configuration

The default level is `WARN`. All output goes to standard output.

```kotlin
// Change level (console output)
TransmuteLogging.configure(LogLevel.INFO)

// Silence everything
TransmuteLogging.configure(LogLevel.OFF)

// Reset to default (WARN, PrintLogger)
TransmuteLogging.reset()
```

### Custom backend

```kotlin
TransmuteLogging.configure(
    level = LogLevel.DEBUG,
    output = object : TransmuteLogger {
        override fun debug(message: String) = myLogger.debug("transmute", message)
        override fun info(message: String)  = myLogger.info("transmute", message)
        override fun warn(message: String)  = myLogger.warn("transmute", message)
        override fun error(message: String, throwable: Throwable?) =
            myLogger.error("transmute", message, throwable)
    }
)
```

`TransmuteLogger` is the backend interface. The built-in `PrintLogger` writes `[transmute:<LEVEL>] <message>` to stdout.

## Per-operation overrides

Attach a logger directly to a transmuter builder:

```kotlin
Transmute.image {
    logger(TransmuteLogging.printLogger(LogLevel.DEBUG))
    scale(800, 600)
}.transmute(source)
```

`TransmuteLogging.printLogger(level)` creates an isolated `PrintLogger` without affecting the global level.

## TransmuteContext (recommended)

`TransmuteContext` bundles a logger with decode and encode options, enabling cleaner reuse across multiple operations:

```kotlin
val debugCtx = TransmuteContext {
    logger = TransmuteLogging.printLogger(LogLevel.DEBUG)
}

// Reuse across multiple transmuters
val scaler = Transmute.image {
    context(debugCtx)
    scale(800, 600)
}

val converter = Transmute.audio {
    context(debugCtx)
    normalize()
}
```

## Plugin loggers

Plugins receive a `PluginLogger` in `TransmuteScope` that is automatically tagged with the plugin key:

```kotlin
override fun install(scope: TransmuteScope, config: C) {
    scope.logger.info("MyPlugin installed, config=$config")
    scope.logger.warn("Native library not found, disabling hardware acceleration")
}
```
