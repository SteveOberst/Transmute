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

| Tool        | Minimum Version         | Notes                                                                              |
|-------------|-------------------------|------------------------------------------------------------------------------------|
| JDK         | 17                      | Temurin or any standard JDK                                                        |
| Android SDK | API 26+ (compileSdk 35) | Required for Android targets                                                       |
| Kotlin      | 2.2.21                  | Managed by Gradle version catalog                                                  |
| FFmpeg      | 6.x                     | Optional - bundled by default; needed if running desktop integration tests locally |

### Clone & Build

```bash
git clone https://github.com/SteveOberst/Transmute.git
cd Transmute

# Full build (excludes iOS unless on macOS)
./gradlew build

# Desktop-only compile check
./gradlew :transmute-core:compileKotlinDesktop \
          :transmute-image:compileKotlinDesktop \
          :transmute-audio:compileKotlinDesktop \
          :transmute-video:compileKotlinDesktop

# Run all desktop tests
./gradlew desktopTest
```

### FFmpeg

The library **bundles** a static FFmpeg build by default (`FfmpegConfig.Bundled`).
If you need FFmpeg on PATH for local development or prefer to use a custom build:

```bash
# macOS
brew install ffmpeg

# Ubuntu/Debian
sudo apt install ffmpeg

# Windows (scoop)
scoop install ffmpeg
```

You can tell Transmute to use your system FFmpeg for tests:

```kotlin
TransmuteConfig.ffmpeg = FfmpegConfig.system()            // PATH lookup
TransmuteConfig.ffmpeg = FfmpegConfig.system("/usr/local/bin/ffmpeg")  // explicit
```

---

## Project Structure

```
Transmute/
├-- transmute-api/        # Public facade (Transmute.kt, Transformers.kt, *TransmuterExt.kt)
├-- transmute-core/       # Codec/Transform base types, IR types, TransmuteConfig
├-- transmute-audio/      # Audio codecs + transforms
├-- transmute-image/      # Image codecs + transforms
├-- transmute-video/      # Video codecs + transforms
├-- gradle/
│   └-- libs.versions.toml  # Version catalog
├-- .github/workflows/
│   ├-- ci.yml            # Unit tests on every push + PR
│   ├-- integration.yml   # Full integration tests (Android emulator, iOS sim, desktop)
│   └-- release.yml       # release-please + publish (gates on integration tests)
├-- release-please-config.json
└-- .release-please-manifest.json
```

Each media module follows the same pattern:

```
transmute-<domain>/
└-- src/
    ├-- commonMain/       # Cross-platform types, IR, format detection, pure-Kotlin codecs
    ├-- commonTest/       # Tests for the above
    ├-- desktopMain/      # JVM/Desktop codecs (ImageIO, FFmpeg, JLayer, etc.)
    ├-- desktopTest/      # Desktop integration tests (roundtrip encode → decode)
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
| `feat`            | New feature                  | Minor (0.x → 0.x+1)     |
| `fix`             | Bug fix                      | Patch (0.1.x → 0.1.x+1) |
| `docs`            | Documentation only           | -                       |
| `test`            | Adding/updating tests        | -                       |
| `refactor`        | Code change (no feature/fix) | -                       |
| `chore`           | Build/CI/tooling             | -                       |
| `perf`            | Performance improvement      | -                       |
| `BREAKING CHANGE` | Breaking API change          | Major (0.x → 1.0)       |

### Scopes

Use the module name as the scope:

```
feat(audio): add ALAC codec for iOS
fix(image): correct EXIF rotation for HEIF on Android
test(video): add MKV roundtrip test for desktop
docs: update platform support table in README
chore(ci): add FFmpeg to CI runner
```

### Examples

```bash
# Feature: new codec
git commit -m "feat(image): add AVIF encode support for desktop via FFmpeg"

# Bug fix
git commit -m "fix(audio): fix MP3 decode crash on zero-length input"

# Test
git commit -m "test(video): add Android instrumented tests for MP4 roundtrip"

# Breaking change
git commit -m "feat(core)!: rename ConversionContext to TransmuteContext

BREAKING CHANGE: ConversionContext was replaced by TransmuteContext.
Resolved input formats now flow through `Decoded<F, IR>` instead of context fields."
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

### Version Policy

- Pre-1.0: `feat` bumps patch, breaking changes bump minor
- Post-1.0: Standard semver (`feat` → minor, `fix` → patch, breaking → major)
- Current version is tracked in `.release-please-manifest.json`

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

### 1. Check the Format Enum

Open `transmute-core/.../MediaFormat.kt` and check whether the format
already exists in the corresponding enum (`ImageFormat`, `AudioFormat`,
`VideoFormat`). If not, add it:

```kotlin
enum class AudioFormat : MediaFormat {
  WAV, MP3, FLAC, OGG, AAC, M4A, OPUS,
  ALAC,  // ← new entry
  UNKNOWN;
}
```

### 2. Implement Sniff-Based Detection

Format detection is **sniff-based**: the domain `FormatDetector` iterates all
registered decoders/codecs and calls `sniff(data)`.

When adding a codec, implement `sniff()` so it returns the format when the input
is recognized, or `null` otherwise.

**Sniff conventions:**
- Fast, side-effect free, and tolerant of short inputs
- Prefer conservative matching (avoid false positives)
- Never throw - return `null` when unsure

```kotlin
override fun sniff(data: ByteArray): AudioFormat? {
  if (data.size < 4) return null
  // ... check container/header signature ...
  return AudioFormat.ALAC
}
```

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
  override val decodableFormats = setOf(AudioFormat.ALAC)
  override val encodableFormats = setOf(AudioFormat.ALAC)

  override fun sniff(data: ByteArray): AudioFormat? {
    // Return AudioFormat.ALAC if magic bytes match, null otherwise
  }

  override suspend fun decode(source: ByteArray, options: AudioDecodeOptions, context: TransmuteContext): AudioIR {
    // Decode raw bytes → AudioIR
  }

  override suspend fun encode(ir: AudioIR, format: AudioFormat, options: AudioEncodeOptions, context: TransmuteContext): ByteArray {
    // Encode AudioIR → raw bytes
  }
}
```

### 4. Register the Codec

In the platform's registration file (e.g. `PlatformAudioCodecs.desktop.kt`),
register the new codec so it's automatically discovered:

```kotlin
// In PlatformAudioCodecs.desktop.kt
actual fun registerPlatformAudioCodecs() {
  AudioRegistries.register(JvmAlacCodec())
  // ... existing registrations ...
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
    val ctx = AudioTestHelpers.testContext()
    val codec = JvmAlacCodec()

    val encoded = codec.encode(original, ctx)
    assertTrue(encoded.isNotEmpty())

    val decoded = codec.decode(encoded, ctx)
    assertEquals(44100, decoded.sampleRate)
    assertTrue(decoded.samples.data.isNotEmpty())
  }
}
```

**Test conventions:**
- Place tests in the matching test source set (`desktopTest`, `androidInstrumentedTest`, etc.)
- Use the `TestHelpers` classes for synthetic fixtures (`AudioTestHelpers`, `ImageTestHelpers`, `VideoTestHelpers`)
- Skip gracefully when optional dependencies (e.g. FFmpeg) aren't available
- Test encode → decode roundtrip with dimension/sample-rate/channel preservation
- For lossy codecs, assert with reasonable tolerance (don't compare pixel-exact)

### 6. Update Documentation

1. **README.md** - Update the platform support table with the new format
2. **README.md** - Add the new integration test to the test tables
3. Release notes are handled automatically by release-please

### Codec Checklist

- [ ] Format enum entry exists in `MediaFormat.kt`
- [ ] `sniff()` implemented (and covered by tests)
- [ ] Codec implementation (implements `Codec`, `Decoder`, or `Encoder`)
- [ ] Codec registered in the platform registration file
- [ ] Integration test with roundtrip encode → decode
- [ ] README platform support table updated
- [ ] README test table updated
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

  override suspend fun apply(ir: ImageIR, context: TransmuteContext): ImageIR {
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
fun ImageTransmuter.sepia(intensity: Float = 1.0f): ImageTransmuter = apply {
  pipeline.add(ImageSepiaTransform(intensity))
}
```

> **Why extension functions?**  
> Keeping convenience methods as extension functions in dedicated files
> (`ImageTransmuterExt.kt`, `AudioTransmuterExt.kt`, `VideoTransmuterExt.kt`)
> keeps the Transmuter classes lean and avoids modifying core infrastructure
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
| `desktopTest`             | JVM               | `./gradlew desktopTest`           | JVM codecs (ImageIO, JLayer, FFmpeg), JVM-specific integration |
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

**Graceful skip when optional dependency is unavailable:**

```kotlin
private inline fun requireFfmpeg(block: () -> Unit) {
  if (!FfmpegResolver.available) {
    println("SKIPPED - FFmpeg not available")
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
| **Integration Tests** | `integration.yml` | PRs to `main`, releases, manual | Android emulator tests (Linux), iOS simulator tests (macOS), desktop tests w/ FFmpeg (macOS) |
| **Release**           | `release.yml`     | Push to `main`                  | release-please PR → integration gate → publish artifacts                                     |

**Unit Tests** run on every commit to give fast feedback. They cover all
pure-Kotlin and JVM desktop codecs.

**Integration Tests** run the full platform matrix before a PR can merge.
This includes:
- Android instrumented tests via `reactivecircus/android-emulator-runner`
  (API 30, `connectedAndroidTest` for image/audio/video)
- iOS simulator tests via `iosSimulatorArm64Test` on macOS
- Desktop tests with FFmpeg installed on macOS

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
