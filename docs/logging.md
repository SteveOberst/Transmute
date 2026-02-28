# Logging

Transmute provides logging at two levels:

1. **Per-plugin loggers** — each plugin gets its own `PluginLogger` tagged with the plugin's key
2. **Global logging** — the legacy `TransmuteLogging` singleton for system-wide defaults

## Log Levels

| Level   | Description                                                     |
|---------|-----------------------------------------------------------------|
| `OFF`   | Silence all logging                                             |
| `ERROR` | Only errors (codec failures, I/O exceptions)                    |
| `WARN`  | Warnings + errors (default)                                     |
| `INFO`  | Informational messages (format detection, codec selection)       |
| `DEBUG` | Verbose output (pipeline steps, GStreamer subprocess commands)   |

## Per-Plugin Logging (recommended)

Every plugin installed via the `Transmute { }` builder automatically receives a
`PluginLogger` scoped to the plugin's key. Configure it with the `configure { }` block:

```kotlin
val transmute = Transmute {
    plugins {
        install(GStreamer) {
            configure {
                logging {
                    // Set minimum log level for this plugin
                    level(LogLevel.DEBUG)

                    // Optional: custom backend (default: PrintLogger)
                    backend(myCustomLogger)
                }
            }
        }
    }
}
```

Inside a plugin's `install()` method, the scoped logger is available on the `TransmuteScope`:

```kotlin
override fun install(scope: TransmuteScope, config: MyConfig) {
    scope.logger.info("Registering codecs")   // Output: [my-plugin] Registering codecs
    scope.logger.debug("Verbose details...")   // Only shown if level <= DEBUG
}
```

### How it works

| Class | Purpose |
|-------|---------|
| `PluginLogger` | Per-plugin `TransmuteLogger` that prefixes messages with `[pluginId]` and filters by level |
| `PluginLoggerConfig` | DSL block for `level()` and `backend()` |
| `PluginConfigure` | Container for cross-cutting plugin concerns (currently: logging) |
| `HasPluginConfigure` | Marker interface — implement in your config class to enable `configure { }` |

### Making your plugin support `configure { }`

```kotlin
class MyPluginConfig : HasPluginConfigure {
    override val pluginConfigure = PluginConfigure()

    fun configure(block: PluginConfigure.() -> Unit) {
        pluginConfigure.apply(block)
    }

    // ... your other config methods
}
```

## Global Logging (legacy)

The `TransmuteLogging` singleton sets system-wide defaults. It is still used as
the fallback logger for code paths outside the plugin system.

```kotlin
import dev.transmute.common.TransmuteLogging
import dev.transmute.common.LogLevel

// Silence all logging
TransmuteLogging.configure(LogLevel.OFF)

// Only warnings and errors (default)
TransmuteLogging.configure(LogLevel.WARN)

// Debug-level (verbose)
TransmuteLogging.configure(LogLevel.DEBUG)

// Supply a custom logger backend
TransmuteLogging.configure(LogLevel.INFO, myLoggerBackend)
```

## Per-transmuter override

Transmuters support a per-instance logger override:

```kotlin
val t = Transmute.image {
    logger(myLogger)
    scale(maxWidth = 800, maxHeight = 600)
}
```

This is useful for isolating verbose logging to a specific operation without changing the global level.

## Instance-based API

When using the instance-based `Transmute { }` factory, each plugin gets its own
scoped logger. You can additionally override the logger in downstream transmuters:

```kotlin
val transmute = Transmute {
    plugins {
        install(GStreamer) {
            configure {
                logging { level(LogLevel.DEBUG) }
            }
        }
    }
}

val t = transmute.image {
    logger(myLogger)
    scale(maxWidth = 1920, maxHeight = 1080)
}
```

See [plugins.md](plugins.md) for the full instance-based API documentation.

