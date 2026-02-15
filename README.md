# Transmute

A Kotlin Multiplatform media conversion library providing modular, type-safe APIs for audio, video, and image processing.

[![](https://jitpack.io/v/SteveOberst/Transmute.svg)](https://jitpack.io/#SteveOberst/Transmute)

## Modules

| Module | Description | Dependencies |
|--------|-------------|--------------|
| `transmute-core` | Core pipeline, IR types, metadata policies, conversion context | None |
| `transmute-audio` | Audio decoders, encoders, and transforms | `transmute-core` |
| `transmute-image` | Image decoders, encoders, format detection, transforms (crop, rotate, scale) | `transmute-core` |
| `transmute-video` | Video decoders, encoders, and transforms | `transmute-core`, `transmute-audio`, `transmute-image` |

## Installation

Add JitPack repository to your `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositories {
        maven("https://jitpack.io")
    }
}
```

Add specific module dependencies in your `build.gradle.kts`:

```kotlin
dependencies {
    // Core only
    implementation("com.github.SteveOberst.Transmute:transmute-core:<version>")
    
    // Audio processing
    implementation("com.github.SteveOberst.Transmute:transmute-audio:<version>")
    
    // Image processing
    implementation("com.github.SteveOberst.Transmute:transmute-image:<version>")
    
    // Video processing (includes audio and image dependencies)
    implementation("com.github.SteveOberst.Transmute:transmute-video:<version>")
}
```

Replace `<version>` with a release tag or commit hash.

## Platform Support

- **Android** (minSdk 26)
- **JVM/Desktop**
- **iOS** (arm64, x64, simulatorArm64)

## Package Structure

All packages use the `dev.transmute.*` namespace:

- `dev.transmute.core` - Core types and pipeline
- `dev.transmute.core.pipeline` - Pipeline infrastructure
- `dev.transmute.audio` - Audio processing
- `dev.transmute.image` - Image processing
- `dev.transmute.image.codecs` - Image codec implementations
- `dev.transmute.image.transform` - Image transformations
- `dev.transmute.video` - Video processing

## Contributing

This project uses [Conventional Commits](https://www.conventionalcommits.org/) and [release-please](https://github.com/googleapis/release-please) for automated versioning and releases.

### Commit Message Format

```
<type>(<scope>): <description>

[optional body]

[optional footer(s)]
```

**Types:**
- `feat`: New feature (bumps minor version)
- `fix`: Bug fix (bumps patch version)
- `docs`: Documentation only
- `chore`: Maintenance tasks
- `refactor`: Code change that neither fixes a bug nor adds a feature
- `test`: Adding or updating tests
- `BREAKING CHANGE`: Breaking API changes (bumps major version)

### Release Process

1. Make changes using conventional commits
2. Merge to `main` branch
3. Release-please automatically creates/updates a release PR
4. Merging the release PR triggers:
   - GitHub Release with changelog
   - JitPack builds the release tag automatically

## License

MIT License
