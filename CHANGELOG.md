# Changelog

## [0.1.5-beta.0](https://github.com/SteveOberst/Transmute/compare/v0.1.4-beta.0...v0.1.5-beta.0) (2026-05-10)


### Bug Fixes

* correct ios gstreamer bridge initialization ([2c38b0e](https://github.com/SteveOberst/Transmute/commit/2c38b0e9b3f83361dbde164f0ddf307b69e3829d))

## [0.1.4-beta.0](https://github.com/SteveOberst/Transmute/compare/v0.1.3-beta.0...v0.1.4-beta.0) (2026-05-10)


### Bug Fixes

* unblock prerelease publication ([54885ed](https://github.com/SteveOberst/Transmute/commit/54885ed494e54a0a0a3138095d2965bd313e298a))

## [0.1.3-beta.0](https://github.com/SteveOberst/Transmute/compare/v0.1.2-beta.0...v0.1.3-beta.0) (2026-05-10)


### Bug Fixes

* normalize JitPack publication coordinates ([c66a816](https://github.com/SteveOberst/Transmute/commit/c66a8160e1ced5c7d31c59924d521f9701ef45dc))

## [0.1.2-beta.0](https://github.com/SteveOberst/Transmute/compare/v0.1.1-beta.0...v0.1.2-beta.0) (2026-05-09)


### Bug Fixes

* trigger prerelease after publish workflow repair ([5ba0138](https://github.com/SteveOberst/Transmute/commit/5ba013818feab5d3ce1d6786681163cf6e414a02))

## [0.1.1-beta.0](https://github.com/SteveOberst/Transmute/compare/v0.1.0-beta.0...v0.1.1-beta.0) (2026-05-09)


### Bug Fixes

* use multiplatform-safe helpers in transmute-testing ([6516b4d](https://github.com/SteveOberst/Transmute/commit/6516b4d3e3200fee8bc19554d1cc236faeb1901c))

## 0.1.0-beta.0 (2026-05-09)


### ⚠ BREAKING CHANGES

* rename pipeline start to initial
* default logging level warn
* transmuter returns pipeline output
* Removed builder-level decodeOptions/encodeOptions in favor of decode { options(...) } / encode { options(...) } stage blocks. Added PipelineHandler '+' composition and tap handler to encourage class-first pipelines.
* removed *FormatTag and ByteArray-centric codec/pipeline APIs in favor of module-specific format objects and the canonical Bytes wrapper. Updated pipelines, defaults, tests, and docs accordingly.
* EncodeOptions outputFormat is now non-nullable via OutputFormat (ORIGINAL vs Exact), Default* encode/decode options renamed to Canonical*, and PipelineBuilder adds startWith()/infix then for clearer pipeline construction. Updated docs and expanded pipeline tests.
* Refactors Transmute pipelines and public DSL; removes builder outputFormat()/metadata() APIs, replaces ConversionContext/ContextKey scratchpad with TransmuteContext + DecodeOptions/EncodeOptions, and updates default handlers + docs accordingly.
* transmute() and transmuteInto() now require source bytes as a parameter. Transmuter constructors no longer accept source bytes. The old source-binding factory removed.

### Features

* add HEIC structure and metadata decoder support ([e35bfce](https://github.com/SteveOberst/Transmute/commit/e35bfcec1568e360172fcf26c47a344ac1ff916c))
* allow += for same-type pipeline steps ([b200bac](https://github.com/SteveOberst/Transmute/commit/b200bac22547f4de217bb975cbf55dedcdf4121c))
* comprehensive audit -- GStreamer staging, test split, CI fixes, HEIC preview, process cleanup ([94d66ef](https://github.com/SteveOberst/Transmute/commit/94d66ef8d1e7d2fdd7576a59a8c53900a7d8483f))
* cut beta pre-releases via release-please ([bd08d23](https://github.com/SteveOberst/Transmute/commit/bd08d230258310545641f2d726bc73c98e21f247))
* default logging level warn ([29db6eb](https://github.com/SteveOberst/Transmute/commit/29db6eb848a1927a4bd0233faf7767ce2aca9596))
* ImageRotateTransform takes explicit degrees (default 90 deg) ([6db5270](https://github.com/SteveOberst/Transmute/commit/6db527018932e886650c67e684da3e2210df0b2a))
* initial release ([1090962](https://github.com/SteveOberst/Transmute/commit/10909625b29e1797e6a647130d71e66e45e4db20))
* options mutation blocks ([69de3ea](https://github.com/SteveOberst/Transmute/commit/69de3ea801f5ebc19a98145c588577e8ac9f3660))
* OutputFormat + startWith pipeline DSL ([3af7e78](https://github.com/SteveOberst/Transmute/commit/3af7e78cdaf170ce933ba4888821d0769c350f64))
* reusable transmuters with wouldAffect predicate and public transform fields ([ae6a84d](https://github.com/SteveOberst/Transmute/commit/ae6a84d74461f176a92a6558c78d75a06978cf87))
* run both Android and desktop unit tests in integration workflow ([e4a87ba](https://github.com/SteveOberst/Transmute/commit/e4a87bace3929254b6cc974fa19ceb2f9ae829a0))
* stage DSL for decode/encode ([389a078](https://github.com/SteveOberst/Transmute/commit/389a07860df8a59d01d8671650d6f246a79bbf44))
* transmuter returns pipeline output ([dc91dba](https://github.com/SteveOberst/Transmute/commit/dc91dba5465f37902bd93a7802d97cf023852a22))
* vNext pipeline-based transmuter API ([bc383a0](https://github.com/SteveOberst/Transmute/commit/bc383a078e8d38d07bf7cf1eb7a37a71887016a5))


### Bug Fixes

* add Volatile import for K/N, gate release on CI+integration ([abc23b9](https://github.com/SteveOberst/Transmute/commit/abc23b95d59d6bfc9f725efd9f56a1698dbcd6c1))
* **audio:** avoid AAC ADTS misdetected as MP3 on Android ([0e71a98](https://github.com/SteveOberst/Transmute/commit/0e71a983da3a5be294774291653daf15148d2518))
* autoRotate DSL name, imageFrom/pipeline API, transform tables ([a4b2890](https://github.com/SteveOberst/Transmute/commit/a4b28902d2ad135d7a922a76d0573569d6c2fff9))
* **build:** move important notation to top of the file ([4e61118](https://github.com/SteveOberst/Transmute/commit/4e6111810fcaebdd56f94d5cdbb3cf53ce70179f))
* **build:** track ProjectVersion buildSrc helper ([2d6ef1b](https://github.com/SteveOberst/Transmute/commit/2d6ef1b1e79426d37f7be50eeab5bda020ec8c6f))
* default log level WARN, codec/inspect property notation, compressor defaults, frameRate param name ([a21c3d4](https://github.com/SteveOberst/Transmute/commit/a21c3d4bb16499ed1d7eb537e36e05a958a5184c))
* eliminate exception swallowing in tests and fix CI failure propagation ([5407b09](https://github.com/SteveOberst/Transmute/commit/5407b09ab4247e32af41fca64fcee638ca567c48))
* guard against JOrbis getInfo null in OGG decode, skip on CI decode failure ([5c2385a](https://github.com/SteveOberst/Transmute/commit/5c2385a9c1aaefbba239a9039061cc394a8dc981))
* HEIF/HEIC/AVIF structure display and metadata extraction ([04f55b6](https://github.com/SteveOberst/Transmute/commit/04f55b620b1795a99cc9e187ddc050e02370fbf6))
* **ios:** fix format detection + tolerate simulator codec limits ([6b8b1c1](https://github.com/SteveOberst/Transmute/commit/6b8b1c11973b562f1728b30727e1b265a1eb0db4))
* **ios:** fix video codec K/N compilation + runtime interop issues ([f2afb10](https://github.com/SteveOberst/Transmute/commit/f2afb10cc5f53c3201416db5211fd95a0191bf99))
* **ios:** wrap entire audio roundtrip tests in try-catch Throwable ([420aac8](https://github.com/SteveOberst/Transmute/commit/420aac8d03ca75483d85aa0bec3fd299dbb34899))
* **registry:** make defaults install idempotent ([93f3335](https://github.com/SteveOberst/Transmute/commit/93f33359ba94915fc8a8d4569fd9e39756f84c07))
* remove codec skip workaround, use generous timeouts instead ([1e5c86b](https://github.com/SteveOberst/Transmute/commit/1e5c86b4c81b8ff72e46bad363934b6cd5feba4e))
* restore coroutine timeout for Android MediaCodec tests ([6935504](https://github.com/SteveOberst/Transmute/commit/693550491ac20f8d1b1e569fa01ae87c6333a716))
* transmute-api/README.md plugin install example missing plugins{} wrapper ([83f43c1](https://github.com/SteveOberst/Transmute/commit/83f43c1e2d98413ed5162a327401df6a2f55977e))
* update stale scope.imageDecoders/Encoders API in docs and KDocs ([efd9064](https://github.com/SteveOberst/Transmute/commit/efd9064116b390d6ed54c5b2c399923d64a6b66b))


### Code Refactoring

* rename pipeline start to initial ([80a63f9](https://github.com/SteveOberst/Transmute/commit/80a63f9e6eac8231accbb93843bb7e2ec869d5e2))
* typed formats and Bytes canonicalization ([f34f790](https://github.com/SteveOberst/Transmute/commit/f34f7902e466f1afc8e61249b50571e0e106faf4))

## Changelog
