# Changelog

## [0.1.2](https://github.com/SteveOberst/Transmute/compare/v0.1.1...v0.1.2) (2026-02-16)


### Features

* implement ImageResizeTransform, rework TransmuteConfig API, update docs ([392d2b1](https://github.com/SteveOberst/Transmute/commit/392d2b163324381985e09760f1078dce5abd8bd3))


### Bug Fixes

* create iosMain source set instead of getting it ([c03033a](https://github.com/SteveOberst/Transmute/commit/c03033a6fb9f0bfac78a54e20206e6d3b80a0d69))
* exclude transmute-api from iOS tests (depends on transmute-video) ([923292a](https://github.com/SteveOberst/Transmute/commit/923292a7b287d0e425d2f55d0441fa7d6c0729fd))
* hard-fail all tests, fix video mux buffer bug, fix LAME VBR NPE ([47ce393](https://github.com/SteveOberst/Transmute/commit/47ce39308449806132c6256709825a11719db375))
* increase Android timeout to 45min, defend video audio+mux test against emulator failures ([e89e146](https://github.com/SteveOberst/Transmute/commit/e89e1468223ed6746c1c75ea4457852dc568b16c))
* remove inPremultiplied=false from Android decoder, add pixel diagnostics, defend MP3 test against LAME NPE, add --continue to Gradle ([7487ac9](https://github.com/SteveOberst/Transmute/commit/7487ac9fbc9ad3593961e1a09f0060413de291f9))
* repair syntax error in AndroidAudioCodecTest (missing allCodecsReportCorrectFormats) ([9e17b4e](https://github.com/SteveOberst/Transmute/commit/9e17b4e45562c783632ba281879a5c0887847528))
* resolve CI failures across all 3 integration jobs ([1be1b50](https://github.com/SteveOberst/Transmute/commit/1be1b50cd94d1b6d2f1f803082c99997e616ddcd))
* skip transmute-video iOS tests (pre-existing broken AVFoundation interop) ([dd9458d](https://github.com/SteveOberst/Transmute/commit/dd9458d48f1a3733a200e4bea34ac79644b44a63))
* wire androidInstrumentedTest to commonTest, re-enable Android CI, add coverage ([c03d1a6](https://github.com/SteveOberst/Transmute/commit/c03d1a698b7a92e8412b35f9d7e673ccd4b99d29))

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
