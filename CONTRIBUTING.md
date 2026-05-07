    # Contributing to Transmute

Thank you for your interest in contributing to Transmute! This guide covers everything
you need to know - from setting up your environment to adding new codecs and submitting
a pull request.

## Table of Contents

- [Development Setup](#development-setup)
- [Project Structure](#project-structure)
- [Git Conventions](#git-conventions)
- [Releases](#releases)
- [Adding a New Codec](#adding-a-new-codec)
- [Adding a New Transform](#adding-a-new-transform)
- [Testing](#testing)
- [Code Style](#code-style)

---

## Development Setup

### Prerequisites

| Tool        | Minimum Version         | Notes                                                                |
|-------------|-------------------------|----------------------------------------------------------------------|
| JDK         | 17                      | Temurin or any standard JDK                                          |
| Android SDK | API 26+ (compileSdk 35) | Required for Android targets                                         |
| Kotlin      | 2.2.21                  | Managed by Gradle version catalog                                    |
| GStreamer   | 1.x (bundled)           | Bundled by default; AAC/M4A/Opus/HEIF/AVIF/video codecs on Desktop   |

### Clone & Build

```bash
git clone https://github.com/SteveOberst/Transmute.git
cd Transmute

# Full build (excludes iOS unless on macOS)
./gradlew build

# Desktop-only compile check
./gradlew :transmute-image:compileKotlinDesktop \
          :transmute-audio:compileKotlinDesktop \
          :transmute-video:compileKotlinDesktop \
          :transmute-structure:compileKotlinDesktop

# Run all desktop tests for the published modules
# (use coreTests instead of root-level desktopTest, which pulls in optional
#  staging tasks for plugin SDKs that may not be available locally)
./gradlew coreTests
```

### GStreamer (Bundled by Default)

The `transmute-plugins:gstreamer` module ships a bundled
[GStreamer](https://gstreamer.freedesktop.org/) runtime that is extracted
automatically on first use. Without GStreamer, Desktop supports WAV, MP3,
FLAC decode, OGG decode, BMP, PNG, JPEG, WebP, GIF, and TIFF natively.

Enable GStreamer codecs via the plugin system:

```kotlin
// All features (audio, video) are enabled by default
val transmute = transmute {
    plugins {
        install(GStreamer)
    }
}

// Or selectively disable features you don't need:
val slim = transmute {
    plugins {
        install(GStreamer) {
            disable(GStreamerFeature.LegacyAvi)      // skip legacy AVI
        }
    }
}
```

For HEIF/HEIC/AVIF image codecs on Desktop, use the `transmute-plugins:libheif` plugin.

To use a custom (pre-existing) GStreamer installation instead of the bundled one:

```kotlin
install(GStreamer) {
    installFrom(TPath.of("/opt/gstreamer"))
}
```

---

## Project Structure

```
Transmute/
├── transmute-api/         # Public facade (Transmute.kt, Transformers.kt, *TransmuterExt.kt)
├── transmute-common/      # Shared utilities, PipelineContext, logging
├── transmute-codec/       # Codec infrastructure — registry, encode/decode handler base
├── transmute-model/       # Umbrella for model sub-modules (core, identify, structure, metadata)
├── transmute-structure/   # Structure readers — parse raw bytes into typed MediaStructure
├── transmute-filesystem/  # Cross-platform filesystem abstraction (core, okio)
├── transmute-audio/       # Audio codecs + transforms
├── transmute-image/       # Image codecs + transforms
├── transmute-video/       # Video codecs + transforms
├── transmute-plugins/     # Official plugin modules
│   ├── gstreamer/         # GStreamer-backed codecs (video, AAC, Opus, …)
│   └── libheif/           # libheif-backed HEIF/HEIC/AVIF (Desktop only)
├── gradle/
│   └── libs.versions.toml  # Version catalog
├── .github/workflows/
│   ├── desktop.yml       # Desktop unit + integration tests on every push + PR
│   ├── android.yml       # Android instrumented test matrix
│   ├── ios.yml           # iOS simulator tests
│   ├── playground.yml    # Playground build / smoke check
│   └── release.yml       # release-please + publish (gated on tests)
├── release-please-config.json
└── .release-please-manifest.json
```

Each media module follows the same pattern:

```
transmute-<domain>/
└-- src/
    ├-- commonMain/       # Cross-platform types, IR, format detection, pure-Kotlin codecs
    ├-- commonTest/       # Tests for the above
    ├-- desktopMain/      # JVM/Desktop codecs (ImageIO, JLayer, etc.)
    ├-- desktopTest/      # Desktop integration tests (roundtrip encode -> decode)
    ├-- androidMain/      # Android codecs (BitmapFactory, MediaCodec, etc.)
    ├-- androidInstrumentedTest/  # Android instrumented tests (requires device/emulator)
    ├-- iosMain/          # iOS codecs (CoreGraphics, AVFoundation)
    └-- iosTest/          # iOS tests (requires macOS + simulator)
```

---

## Git Conventions

This project uses [Conventional Commits](https://www.conventionalcommits.org/)
which drive automatic changelog generation and semantic versioning via
[release-please](https://github.com/googleapis/release-please).

### Commit Format

```
<type>(<scope>): <subject>

[optional body]

[optional footer(s)]
```

### Types

| Type              | Purpose                      | Version Bump            |
|-------------------|------------------------------|-------------------------|
| `feat`            | New feature                  | Minor (0.x -> 0.x+1)     |
| `fix`             | Bug fix                      | Patch (0.1.x -> 0.1.x+1) |
| `docs`            | Documentation only           | -                       |
| `test`            | Adding/updating tests        | -                       |
| `refactor`        | Code change (no feature/fix) | -                       |
| `chore`           | Build/CI/tooling             | -                       |
| `perf`            | Performance improvement      | -                       |
| `BREAKING CHANGE` | Breaking API change          | Major (0.x -> 1.0)       |

### Scopes

Use the module name as the scope:

```
feat(audio): add ALAC codec for iOS
fix(image): correct EXIF rotation for HEIF on Android
test(video): add MKV roundtrip test for desktop
docs: update platform support table in README
chore(ci): add GStreamer to CI runner
```

### Examples

```bash
# Feature: new codec
git commit -m "feat(image): add AVIF encode support for desktop via GStreamer"

# Bug fix
git commit -m "fix(audio): fix MP3 decode crash on zero-length input"

# Test
git commit -m "test(video): add Android instrumented tests for MP4 roundtrip"

# Breaking change
git commit -m "feat(api)!: stage DSL for decode/encode

BREAKING CHANGE: Builder-level encodeOptions/decodeOptions were removed. Use decode { options(...) } / encode { options(...) } blocks instead."
```

---

## Releases

### How It Works

1. Push commits to `main` using conventional commit format.
2. **release-please** automatically creates or updates a Release PR with:
    - Bumped version number (based on commit types)
    - Auto-generated release notes (from conventional commits)
    - Updated version metadata (see `.release-please-manifest.json`)
3. When the Release PR is merged:
    - A GitHub Release is created from the Release PR notes
    - JitPack automatically builds the release tag
4. Use the manual pre-release workflow for `-alpha`, `-beta`, or `-rc`
   versions without mutating the release-please manifest or stable changelog line.

### Version Policy

- Pre-1.0: `feat` bumps patch, breaking changes bump minor
- Post-1.0: Standard semver (`feat` -> minor, `fix` -> patch, breaking -> major)
- Current version is tracked in `.release-please-manifest.json`
- Pre-release versions are published manually via `TRANSMUTE_VERSION`
  overrides and should include a suffix such as `-beta.1` or `-rc.1`.

### JitPack

Consumers can depend on any release tag or branch via JitPack:

```kotlin
implementation("com.github.SteveOberst.Transmute:transmute-api:<version>")
```

---

## Adding a New Codec

This is the most common contribution. Follow these steps to add a codec
properly - the process is the same regardless of media domain (image, audio,
video).

### 1. Add the Format type

Formats are typed singleton objects (not enums). Add the new format to the corresponding domain format type:

- Images: `transmute-image/.../ImageFormat.kt`
- Audio: `transmute-audio/.../AudioFormat.kt`
- Video: `transmute-video/.../VideoFormat.kt`

Also add it to the domain’s `Format.all` set so registries and docs can enumerate supported formats.



```kotlin
// transmute-audio/src/commonMain/kotlin/dev/transmute/audio/AudioFormat.kt
sealed interface AudioFormat : MediaFormat {
  // ...
  data object Alac : AudioFormat { override val mimeType: String = "audio/alac"; override val extension: String = "m4a" }

  companion object {
    val all: Set<AudioFormat> = setOf(Mp3, Aac, Wav, Ogg, Flac, M4a, Opus, Alac)
  }
}
```

### 2. Format Detection

Format detection is handled by the built-in `FormatDetector` objects
(`ImageFormatDetector`, `AudioFormatDetector`, `VideoFormatDetector`) which use
magic-byte checks to identify file types. Codecs do **not** need to implement
format detection themselves.

### 3. Implement the Codec

Create a new file in the appropriate platform source set:

| Target        | Source Set    | Example Path                                                             |
|---------------|---------------|--------------------------------------------------------------------------|
| All platforms | `commonMain`  | `transmute-audio/src/commonMain/.../codecs/AlacCodec.kt`                 |
| Desktop/JVM   | `desktopMain` | `transmute-audio/src/desktopMain/.../codecs/jvm/JvmAlacCodec.kt`         |
| Android       | `androidMain` | `transmute-audio/src/androidMain/.../codecs/android/AndroidAlacCodec.kt` |
| iOS           | `iosMain`     | `transmute-audio/src/iosMain/.../codecs/ios/IosAlacCodec.kt`             |

Implement the unified `Codec` interface (or `Decoder`/`Encoder` if the
codec only works in one direction):



```kotlin
internal class JvmAlacCodec : AudioCodec {
  override val decodableFormats = setOf(AudioFormat.Alac)
  override val encodableFormats = setOf(AudioFormat.Alac)

  override suspend fun decode(
    source: TSource,
    params: Params,
    context: PipelineContext,
  ): AudioIR = TODO("decode")

  override suspend fun encode(
    ir: AudioIR,
    format: AudioFormat,
    params: Params,
    context: PipelineContext,
  ): Bytes = TODO("encode")
}
```

### 4. Register the Codec

In the platform's registration file (e.g. `PlatformAudioCodecs.desktop.kt`),
register the new codec so it's automatically discovered:

```kotlin
// transmute-audio/src/desktopMain/.../PlatformAudioCodecs.desktop.kt
actual fun installPlatformAudioCodecs(
  decoders: MutableAudioDecoderRegistry,
  encoders: MutableAudioEncoderRegistry,
) {
  val alac = JvmAlacCodec()
  decoders.register(alac)
  encoders.register(alac)
}
```

### 5. Add Integration Tests

Create a roundtrip test in the corresponding test source set:

```kotlin
class JvmAlacCodecTest {
  @Test
  fun alacRoundTripPreservesSampleRate() = runTest {
    val original = AudioTestHelpers.sineWave(
      frequency = 440f, durationMs = 300, sampleRate = 44100,
    )
    val codec = JvmAlacCodec()
    val ctx = AudioTestHelpers.testContext()

    val encoded = codec.encode(original, AudioFormat.Alac, Params.Empty, ctx)
    assertTrue(encoded.data.isNotEmpty())

    val decoded = codec.decode(encoded, Params.Empty, ctx)
    assertEquals(44100, decoded.sampleRate)
    assertTrue(decoded.samples.data.isNotEmpty())
  }
}
```

**Test conventions:**
- Place tests in the matching test source set (`desktopTest`, `androidInstrumentedTest`, etc.)
- Use the `TestHelpers` classes for synthetic fixtures (`AudioTestHelpers`, `ImageTestHelpers`, `VideoTestHelpers`)
- Skip gracefully when optional dependencies (e.g. GStreamer) aren't available
- Test encode -> decode roundtrip with dimension/sample-rate/channel preservation
- For lossy codecs, assert with reasonable tolerance (don't compare pixel-exact)

### 6. Update Documentation

1. **docs/codecs/** - Add/update the format page with platform support + usage examples
2. **README.md** - Mention new format support if it’s user-visible
3. Release notes are handled automatically by release-please

### Codec Checklist

- [ ] Format object exists in the domain `*Format.kt` file and is included in `*Format.all`
- [ ] Format detection supported in the domain `FormatDetector` (magic-byte check)
- [ ] Codec implementation (implements `Codec`, `*Codec`, `*Decoder`, or `*Encoder`)
- [ ] Codec registered in the platform registration file
- [ ] Integration test with roundtrip encode -> decode
- [ ] Docs updated (`docs/codecs/`, README if needed)
- [ ] Commit message follows `feat(<module>): add <FORMAT> codec for <platform>`

---

## Adding a New Transform

Transforms are stateless operations on intermediate representations (IRs).
They live in `commonMain` and work on all platforms.

### 1. Implement the Transform

```kotlin
class ImageSepiaTransform(
  private val intensity: Float = 1.0f,
) : Transform<ImageIR> {
  override val id = TransformId("image.sepia")

  override suspend fun apply(ir: ImageIR, context: PipelineContext): ImageIR {
    // Apply sepia filter to pixels
    return ir.copy(/* modified pixel buffer */)
  }
}
```

### 2. Add Factory Method

In `Transformers.kt`, add a factory method to the corresponding factory object:

```kotlin
object ImageTransforms {
  // ... existing transforms ...
  fun sepia(intensity: Float = 1.0f) = ImageSepiaTransform(intensity)
}
```

### 3. Add Transmuter Extension Function

In the corresponding `*TransmuterExt.kt` file (e.g. `ImageTransmuterExt.kt`),
add an extension function so DSL callers get an ergonomic shortcut:

```kotlin
// In transmute-api/.../ImageTransmuterExt.kt
fun <T : TransformConfigurable<ImageIR>> T.sepia(intensity: Float = 1.0f): T = apply {
  transform { add(ImageSepiaTransform(intensity)) }
}
```

> **Why extension functions?**  
> Keeping convenience methods as extension functions in dedicated files
> (`ImageTransmuterExt.kt`, `AudioTransmuterExt.kt`, `VideoTransmuterExt.kt`)
> keeps the builder classes lean and avoids modifying core infrastructure
> every time a new transform is added.

### 4. Test

```kotlin
class ImageSepiaTransformTest {
  @Test
  fun sepiaPreservesDimensions() = runTest {
    val original = ImageTestHelpers.solidColor(64, 64)
    val transform = ImageSepiaTransform(1.0f)
    val result = transform.apply(original, ImageTestHelpers.testContext())
    assertEquals(64, result.width)
    assertEquals(64, result.height)
  }
}
```

---

## Testing

### Test Source Sets

| Source Set                | Runs On           | Command                           | What It Tests                                                  |
|---------------------------|-------------------|-----------------------------------|----------------------------------------------------------------|
| `commonTest`              | All targets       | `./gradlew allTests`              | Pure-Kotlin codecs (WAV, BMP), transforms, format detection    |
| `desktopTest`             | JVM               | `./gradlew coreTests` (aggregate) or `./gradlew :<module>:desktopTest` | JVM codecs (ImageIO, JLayer), JVM-specific integration         |
| `androidInstrumentedTest` | Device/Emulator   | `./gradlew connectedAndroidTest`  | Android codecs (BitmapFactory, MediaCodec)                     |
| `iosTest`                 | macOS + Simulator | `./gradlew iosSimulatorArm64Test` | iOS codecs (CoreGraphics, AVFoundation)                        |

### Running Tests

```bash
# All tests (desktop + common)
./gradlew check

# Specific module
./gradlew :transmute-image:desktopTest
./gradlew :transmute-audio:desktopTest
./gradlew :transmute-video:desktopTest

# Specific test class
./gradlew :transmute-image:desktopTest \
  --tests "dev.transmute.image.codecs.jvm.JvmImageCodecTest"

# Android instrumented tests (requires device/emulator)
./gradlew :transmute-image:connectedAndroidTest
./gradlew :transmute-audio:connectedAndroidTest
./gradlew :transmute-video:connectedAndroidTest
```

### Test Patterns

**Synthetic fixtures (no golden files):**

Tests generate deterministic synthetic data using the `TestHelpers` classes
instead of relying on checked-in test files:

```kotlin
// Image: solid color, checkerboard, gradient
ImageTestHelpers.solidColor(64, 48, r = 200, g = 100, b = 50)
ImageTestHelpers.checkerboard(32, 32)
ImageTestHelpers.horizontalGradient(64, 32)

// Audio: sine wave, silence
AudioTestHelpers.sineWave(frequency = 440f, durationMs = 300, sampleRate = 44100)
AudioTestHelpers.silence(durationMs = 1000)

// Video: synthetic gradient frames with optional audio
VideoTestHelpers.syntheticVideo(width = 64, height = 48, durationMs = 300)
```

**Optional external real-media corpus:**

When a change needs real files with messy metadata, odd timestamps, or
container edge cases, use the opt-in external corpus harness instead of
checking binaries into the repo.

Configuration:

```bash
# PowerShell / cmd via Gradle property
./gradlew :transmute-testing:desktopTest -Ptransmute.testMediaDir=C:/media/transmute-corpus

# or environment variable
set TRANSMUTE_TEST_MEDIA_DIR=C:/media/transmute-corpus
./gradlew :transmute-testing:desktopTest
```

Expected manifest: `fixtures.tsv` at the corpus root, with tab-separated columns:

```text
id    relativePath    domain    format    tags    notes    expectations
cover-jpeg    images/cover.jpg    image    jpeg    smoke,metadata,structure    Embedded EXIF sample    detect=jpeg;domain=image;structure=true;metadata.min=1
sample-mov    video/sample.mov    video    mov    smoke,structure    Variable frame timing sample    detect=mov;domain=video;structure=true
```

Supported tag conventions used by the current harness:
- `smoke` or `detect`: assert format detection matches the manifest format label
- `structure`: assert `transmute.inspect.structure(...)` returns a non-null structure
- `metadata`: assert `transmute.inspect.metadata(...)` returns at least one metadata entry

Optional expectation tokens in the seventh column allow stricter per-fixture
assertions without hard-coding them in test code:
- `detect=<format>`: assert `transmute.inspect.detectFormat(...)` matches the token
- `domain=image|audio|video|other`: assert the inspected media domain
- `structure=true|false`: assert whether decoded structure is available
- `rawStructure=true|false`: assert whether raw structure is available
- `metadata.min=<count>`: assert at least `<count>` metadata entries are produced

Keep the corpus out of the normal checkout and curate it separately; the tests
gracefully no-op when no external corpus is configured.

**Manual release dry run:**

Use `.github/workflows/release-dry-run.yml` before the first public tag, or
after changing release wiring. Supply a `ref` in the Actions UI; the workflow
stages the desktop GStreamer/libheif payloads on Windows and macOS, downloads
the GStreamer iOS SDK on macOS, runs `publishToMavenLocal`, and uploads the
resulting `~/.m2/repository/dev/transmute` tree as an artifact for inspection.
Optionally provide `version` to validate a planned pre-release version override.

**Manual pre-release publish:**

Use `.github/workflows/prerelease.yml` to publish a semantic pre-release such
as `0.5.0-beta.1` or `0.5.0-rc.1`. The workflow validates that the version has a
pre-release suffix, refuses to reuse an existing tag, stages desktop native
payloads, publishes artifacts using `TRANSMUTE_VERSION`, and creates a GitHub
pre-release tag `v<version>`.

**Release history reset:**

If you need a true fresh start, wipe GitHub releases and tags, reset
`.release-please-manifest.json` to `0.0.0`, and clear `CHANGELOG.md` before
cutting the next pre-release or stable release.

**Graceful skip when optional dependency is unavailable:**

```kotlin
private inline fun requireGStreamer(block: () -> Unit) {
  if (!GStreamerCodecInstaller.available) {
    println("SKIPPED - GStreamer not available")
    return
  }
  block()
}
```

**Lossy codec tolerance:**

```kotlin
// Image: peak pixel difference
val diff = ImageTestHelpers.peakDifference(original, decoded)
assertTrue(diff < 10, "Peak diff $diff should be < 10 for solid color JPEG")

// Image: mean absolute error
val mae = ImageTestHelpers.meanAbsoluteError(original, decoded)
assertTrue(mae < 5.0, "MAE $mae should be < 5")

// Audio: just verify samples are non-empty and sample rate is preserved
assertEquals(44100, decoded.sampleRate)
assertTrue(decoded.samples.data.isNotEmpty())
```

### CI / CD

The project has three GitHub Actions workflows:

| Workflow              | File              | Triggers On                     | What It Runs                                                                                 |
|-----------------------|-------------------|---------------------------------|----------------------------------------------------------------------------------------------|
| **Unit Tests**        | `ci.yml`          | Every push & PR                 | `commonTest` + `desktopTest` on Ubuntu (fast)                                                |
| **Integration Tests** | `integration.yml` | PRs to `main`, releases, manual | Android emulator tests (Linux), iOS simulator tests (macOS), desktop tests w/ GStreamer (Linux) |
| **Release**           | `release.yml`     | Push to `main`                  | release-please PR -> integration gate -> publish artifacts                                     |

**Unit Tests** run on every commit to give fast feedback. They cover all
pure-Kotlin and JVM desktop codecs.

**Integration Tests** run the full platform matrix before a PR can merge.
This includes:
- Android instrumented tests via `reactivecircus/android-emulator-runner`
  (API 30, `connectedAndroidTest` for image/audio/video)
- iOS simulator tests via `iosSimulatorArm64Test` on macOS
- Desktop tests with GStreamer installed on Linux

**Releases** are managed by release-please. When a release is created,
the integration test workflow runs first as a gate - artifacts are only
published if all integration tests pass.

---

## Code Style

- **Kotlin** - follow standard Kotlin coding conventions
- **Visibility** - codec implementation classes are `internal`; only the
  registration function and the `Codec`/`Decoder`/`Encoder` interfaces
  are public
- **Naming** - platform codecs are prefixed: `Jvm*`, `Android*`, `Ios*`
- **Error handling** - throw `IllegalStateException` or
  `IllegalArgumentException` for invariant violations; let codec errors
  propagate naturally
- **Coroutines** - all `decode`/`encode` functions are `suspend`;
  use `withContext(Dispatchers.IO)` for blocking I/O on JVM
- **No golden files** - generate test fixtures in code via `TestHelpers`

---

## Pull Request Process

1. Fork the repo and create a feature branch:
   ```bash
   git checkout -b feat/alac-codec
   ```
2. Make changes following the guidelines above
3. Ensure all tests pass: `./gradlew check`
4. Commit using conventional commits
5. Open a PR against `main`
6. CI will run automatically - fix any failures
7. A maintainer will review and merge

Thank you for contributing!
