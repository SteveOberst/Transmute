# Logging

Transmute uses a structured logging API via `TransmuteLogging`.

By default, logging is set to `WARN` level.

## Configure global logging

```kotlin
// imports omitted

// Silence all logging
dev.transmute.core.TransmuteLogging.configure(dev.transmute.core.LogLevel.OFF)

// Only warnings and errors
dev.transmute.core.TransmuteLogging.configure(dev.transmute.core.LogLevel.WARN)

// Debug-level (verbose)
dev.transmute.core.TransmuteLogging.configure(dev.transmute.core.LogLevel.DEBUG)

// Supply a custom logger backend
dev.transmute.core.TransmuteLogging.configure(dev.transmute.core.LogLevel.INFO, myLoggerBackend)
```

## Per-transmuter override

Transmuters support a per-instance logger override:

```kotlin
// imports omitted

val t =
  Transmute.image {
    logger(myLogger)
    scale(maxWidth = 800, maxHeight = 600)
  }
```

