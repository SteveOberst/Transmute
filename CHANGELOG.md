# Changelog

## [0.1.1](https://github.com/SteveOberst/Transmute/compare/v0.1.0...v0.1.1) (2026-02-15)


### Bug Fixes

* add execute permission to gradlew ([b8a76a4](https://github.com/SteveOberst/Transmute/commit/b8a76a40c961f08c3433456d096c81debeab31d1))
* configure release-please tag format and enable workflow permissions ([0ea6cfc](https://github.com/SteveOberst/Transmute/commit/0ea6cfc33f9460cfdd813afc6b1bd27dfea1dadb))
* remove invalid schema from release-please manifest ([2fa0952](https://github.com/SteveOberst/Transmute/commit/2fa09527aef366733a8782ecbbe4f2bbf04168a9))

## [0.1.0](https://github.com/SteveOberst/Transmute/releases/tag/v0.1.0) (2026-02-15)

### Features

* Initial extraction of media conversion library from ShrinkIt
* **transmute-core:** Pipeline infrastructure, IR types, metadata policies, conversion context
* **transmute-audio:** Audio decoders, encoders, and metadata transforms
* **transmute-image:** Image codecs, format detection, transforms (crop, rotate, scale)
* **transmute-video:** Video decoders, encoders, and metadata transforms

### Platform Support

* Android (minSdk 26)
* JVM/Desktop
* iOS (arm64, x64, simulatorArm64)
