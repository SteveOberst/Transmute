# Changelog

## [0.2.0](https://github.com/SteveOberst/Transmute/compare/v0.1.0...v0.2.0) (2026-02-19)


### ⚠ BREAKING CHANGES

* wouldAffect has been renamed to wouldTransmute on ImageTransmuter, AudioTransmuter and VideoTransmuter.

### Features

* add wouldTransform to domain Transform interfaces; rename wouldAffect -&gt; wouldTransmute ([981deb2](https://github.com/SteveOberst/Transmute/commit/981deb28a17e857dceb66927ac2ac97aa8072c60))

## [0.1.0](https://github.com/SteveOberst/Transmute/compare/v0.0.0...v0.1.0) (2026-02-19)


### Features

* run both Android and desktop unit tests in integration workflow ([ed78ad6](https://github.com/SteveOberst/Transmute/commit/ed78ad6a4b02edd290d9f047a2904358fe4126ad))

## [2.0.0](https://github.com/SteveOberst/Transmute/compare/v1.0.0...v2.0.0) (2026-02-19)


### ⚠ BREAKING CHANGES

* transmute() and transmuteInto() now require source bytes as a parameter. Transmuter constructors no longer accept source bytes. The old source-binding factory removed.

### Features

* reusable transmuters with wouldAffect predicate and public transform fields ([f8056e4](https://github.com/SteveOberst/Transmute/commit/f8056e4124229fd5fc50c6aae300c4433bf1dd78))

## 1.0.0 (2026-02-17)


### Features

* initial release ([1090962](https://github.com/SteveOberst/Transmute/commit/10909625b29e1797e6a647130d71e66e45e4db20))


### Bug Fixes

* add Volatile import for K/N, gate release on CI+integration ([1223249](https://github.com/SteveOberst/Transmute/commit/122324931b85acbcf278b7977d024a59720619a0))
* **audio:** avoid AAC ADTS misdetected as MP3 on Android ([0e71a98](https://github.com/SteveOberst/Transmute/commit/0e71a983da3a5be294774291653daf15148d2518))
* **build:** move important notation to top of the file ([4e61118](https://github.com/SteveOberst/Transmute/commit/4e6111810fcaebdd56f94d5cdbb3cf53ce70179f))
* **build:** track ProjectVersion buildSrc helper ([2d6ef1b](https://github.com/SteveOberst/Transmute/commit/2d6ef1b1e79426d37f7be50eeab5bda020ec8c6f))
* eliminate exception swallowing in tests and fix CI failure propagation ([045a013](https://github.com/SteveOberst/Transmute/commit/045a013e33895499f14fbf2972b0153151b54ab7))
* **ios:** fix format detection + tolerate simulator codec limits ([94b0960](https://github.com/SteveOberst/Transmute/commit/94b096097404d29e633994ed9e26d96ba357e4c5))
* **ios:** fix video codec K/N compilation + runtime interop issues ([abb820b](https://github.com/SteveOberst/Transmute/commit/abb820b0d527205060bc6324101892759c2059f1))
* **ios:** wrap entire audio roundtrip tests in try-catch Throwable ([48199cc](https://github.com/SteveOberst/Transmute/commit/48199cc2c4c3e1e9c6804e9702018592b94da8b4))
* **registry:** make defaults install idempotent ([93f3335](https://github.com/SteveOberst/Transmute/commit/93f33359ba94915fc8a8d4569fd9e39756f84c07))
* remove codec skip workaround, use generous timeouts instead ([36a2728](https://github.com/SteveOberst/Transmute/commit/36a272889956228678f4c8c224d6756404dd66ef))
* restore coroutine timeout for Android MediaCodec tests ([b1d7bee](https://github.com/SteveOberst/Transmute/commit/b1d7bee228537b4432d47b03b06715f65851b4ae))
* trigger release-please PR recreation with PAT ([ba79359](https://github.com/SteveOberst/Transmute/commit/ba79359bb59975aeb6cbd1b5c96dd85c712a3266))

## [0.1.2](https://github.com/SteveOberst/Transmute/compare/v0.1.1...v0.1.2) (2026-02-17)


### Bug Fixes

* add Volatile import for K/N, gate release on CI+integration ([1223249](https://github.com/SteveOberst/Transmute/commit/122324931b85acbcf278b7977d024a59720619a0))
* **ios:** fix format detection + tolerate simulator codec limits ([94b0960](https://github.com/SteveOberst/Transmute/commit/94b096097404d29e633994ed9e26d96ba357e4c5))
* **ios:** fix video codec K/N compilation + runtime interop issues ([abb820b](https://github.com/SteveOberst/Transmute/commit/abb820b0d527205060bc6324101892759c2059f1))
* **ios:** wrap entire audio roundtrip tests in try-catch Throwable ([48199cc](https://github.com/SteveOberst/Transmute/commit/48199cc2c4c3e1e9c6804e9702018592b94da8b4))
* trigger release-please PR recreation with PAT ([ba79359](https://github.com/SteveOberst/Transmute/commit/ba79359bb59975aeb6cbd1b5c96dd85c712a3266))

## [0.1.1](https://github.com/SteveOberst/Transmute/compare/v0.1.0...v0.1.1) (2026-02-17)


### Features

* initial release ([1090962](https://github.com/SteveOberst/Transmute/commit/10909625b29e1797e6a647130d71e66e45e4db20))


### Bug Fixes

* **audio:** avoid AAC ADTS misdetected as MP3 on Android ([0e71a98](https://github.com/SteveOberst/Transmute/commit/0e71a983da3a5be294774291653daf15148d2518))
* **build:** move important notation to top of the file ([4e61118](https://github.com/SteveOberst/Transmute/commit/4e6111810fcaebdd56f94d5cdbb3cf53ce70179f))
* **build:** track ProjectVersion buildSrc helper ([2d6ef1b](https://github.com/SteveOberst/Transmute/commit/2d6ef1b1e79426d37f7be50eeab5bda020ec8c6f))
* **registry:** make defaults install idempotent ([93f3335](https://github.com/SteveOberst/Transmute/commit/93f33359ba94915fc8a8d4569fd9e39756f84c07))
