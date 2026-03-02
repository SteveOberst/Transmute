# GStreamer Integration

The `transmute-gstreamer` module extends Transmute with
[GStreamer](https://gstreamer.freedesktop.org/)-backed codecs, filling
platform gaps that native engines cannot cover. GStreamer is **bundled by
default** — no separate installation is required.

## What GStreamer Enables

| Category | Formats added                                         |
|----------|-------------------------------------------------------|
| Audio    | AAC, M4A, Opus (full codec); FLAC & OGG/Vorbis encode |
| Image    | HEIF, HEIC, AVIF decode/encode                        |
| Video    | MP4, MOV, WebM, AVI, MKV decode/encode                |

## Installation

### 1. Add the Gradle dependency

```kotlin
// build.gradle.kts
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("com.github.SteveOberst.Transmute:transmute-gstreamer:<version>")
        }
    }
}
```

### 2. Install the plugin

GStreamer codecs are enabled via the plugin system. Create a `Transmute` instance
with the `GStreamer` plugin installed:

```kotlin
// All features (audio, video, image) are enabled by default
val transmute = Transmute {
    plugins {
        install(GStreamer)
    }
}

// Or selectively disable features:
val slim = Transmute {
    plugins {
        install(GStreamer) {
            disable(GStreamerFeature.LegacyAvi)      // skip AVI container
            disable(GStreamerFeature.ImageEncoding)  // skip HEIF/AVIF encoding
        }
    }
}

// Use the instance
val heif = transmute.image {
    scale(maxWidth = 512, maxHeight = 512)
}.transmute(inputBytes.asBytes())
```

The bundled GStreamer runtime is extracted automatically on first use.
See [plugins.md](plugins.md) for the full plugin system documentation.

## Installation Modes

By default, the bundled GStreamer is used. You can override this behavior:

```kotlin
install(GStreamer) {
    // Default - uses the bundled GStreamer runtime (no action needed)

    // Use a custom GStreamer installation at a specific path:
    // installFrom(TPath.of("/opt/gstreamer"))

    // Use whatever GStreamer is on the system PATH:
    // useSystemInstallation()
}
```

## Platform Setup

### Desktop (JVM)

GStreamer is invoked as a `gst-launch-1.0` subprocess. By default, the
bundled runtime is extracted to `~/.transmute/gstreamer/<version>/` on
first use.

When using `useSystemInstallation()` or as a fallback, GStreamer is located by:

1. Searching `PATH` for `gst-launch-1.0`.
2. *(Windows only)* Checking common install paths:
   - `%GSTREAMER_1_0_ROOT_MSVC_X86_64%\bin`
   - `%GSTREAMER_1_0_ROOT_X86_64%\bin`
   - `C:\gstreamer\1.0\msvc_x86_64\bin`
   - `C:\gstreamer\1.0\x86_64\bin`

### Android

On Android, GStreamer is invoked via JNI (`libgstreamer_bridge.so`).
Set the `GSTREAMER_ROOT_ANDROID` environment variable to the GStreamer
Android SDK root before building.

### iOS

On iOS, GStreamer is invoked via cinterop (`GStreamer.framework`).
Install the framework at `/Library/Frameworks/GStreamer.framework`.

## Explicit Registration

If you prefer fine-grained control outside the plugin system, you can
install GStreamer codecs into specific registries directly:

```kotlin
// Install only audio codecs
GStreamerCodecInstaller.installAudioCodecs(
    AudioRegistries.decoders,
    AudioRegistries.encoders,
)

// Install only image codecs
GStreamerCodecInstaller.installImageCodecs(
    ImageRegistries.decoders,
    ImageRegistries.encoders,
)

// Install only video codecs
GStreamerCodecInstaller.installVideoCodecs(
    VideoRegistries.decoders,
    VideoRegistries.encoders,
)
```

## How It Works

### Codec Execution

| Platform | Mechanism                          |
|----------|------------------------------------|
| Desktop  | `gst-launch-1.0` subprocess        |
| Android  | JNI via `libgstreamer_bridge.so`   |
| iOS      | cinterop via `GStreamer.framework` |

## Checking Availability

```kotlin
if (GStreamerCodecInstaller.available) {
    println("GStreamer is installed and ready")
}
```

## Troubleshooting

Enable debug logging on the GStreamer plugin to see detailed resolver diagnostics
(paths searched, what was found, fallback order):

```kotlin
install(GStreamer) {
    configure {
        logging {
            level(LogLevel.DEBUG)
        }
    }
}
```

| Issue                                          | Solution                                                  |
|------------------------------------------------|-----------------------------------------------------------|
| `GStreamerCodecInstaller.available` is `false` | Enable `DEBUG` logging and check resolver output          |
| HEIF/AVIF encoding fails                       | Install `gstreamer1.0-plugins-bad` (provides x265enc)     |
| Windows: GStreamer not detected                | Set `GSTREAMER_1_0_ROOT_MSVC_X86_64` or add `bin` to PATH |
| Android: native lib not found                  | Set `GSTREAMER_ROOT_ANDROID` before Gradle build          |
| iOS: framework not found                       | Install `GStreamer.framework` to `/Library/Frameworks/`   |
