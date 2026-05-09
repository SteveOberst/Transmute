package dev.transmute

import dev.transmute.audio.transform.*
import dev.transmute.image.transform.*
import dev.transmute.video.transform.*
import kotlin.test.Test
import kotlin.test.assertIs

class TransformersTest {

    // -- Image transforms ---

    @Test
    fun imageScaleCreatesCorrectType() {
        assertIs<ImageScaleTransform>(Transformers.image().scale(800, 600))
    }

    @Test
    fun imageCropCreatesCorrectType() {
        assertIs<ImageCropTransform>(Transformers.image().crop(0, 0, 400, 400))
    }

    @Test
    fun imageRotateCreatesCorrectType() {
        assertIs<ImageRotateTransform>(Transformers.image().rotate())
    }

    @Test
    fun imageGrayscaleCreatesCorrectType() {
        assertIs<ImageGrayscaleTransform>(Transformers.image().grayscale())
    }

    @Test
    fun imageFlipCreatesCorrectType() {
        assertIs<ImageFlipTransform>(Transformers.image().flip(horizontal = true))
    }

    @Test
    fun imageBrightnessContrastCreatesCorrectType() {
        assertIs<ImageBrightnessContrastTransform>(
            Transformers.image().brightnessContrast(brightness = 0.1f, contrast = 1.2f),
        )
    }

    @Test
    fun imageBlurCreatesCorrectType() {
        assertIs<ImageBlurTransform>(Transformers.image().blur(radius = 3))
    }

    @Test
    fun imageOpacityCreatesCorrectType() {
        assertIs<ImageOpacityTransform>(Transformers.image().opacity(0.5f))
    }

    // -- Audio transforms ---

    @Test
    fun audioNormalizeCreatesCorrectType() {
        assertIs<AudioNormalizeTransform>(Transformers.audio().normalize(0.95f))
    }

    @Test
    fun audioResampleCreatesCorrectType() {
        assertIs<AudioResampleTransform>(Transformers.audio().resample(48000))
    }

    @Test
    fun audioFadeCreatesCorrectType() {
        assertIs<AudioFadeTransform>(Transformers.audio().fade(fadeInMs = 200, fadeOutMs = 500))
    }

    @Test
    fun audioTrimCreatesCorrectType() {
        assertIs<AudioTrimTransform>(Transformers.audio().trim(startMs = 1000, endMs = 5000))
    }

    @Test
    fun audioGainCreatesCorrectType() {
        assertIs<AudioGainTransform>(Transformers.audio().gain(-3f))
    }

    @Test
    fun audioMonoCreatesCorrectType() {
        assertIs<AudioMonoTransform>(Transformers.audio().mono())
    }

    @Test
    fun audioReverseCreatesCorrectType() {
        assertIs<AudioReverseTransform>(Transformers.audio().reverse())
    }

    @Test
    fun audioSpeedCreatesCorrectType() {
        assertIs<AudioSpeedTransform>(Transformers.audio().speed(1.5f))
    }

    @Test
    fun audioSilenceTrimCreatesCorrectType() {
        assertIs<AudioSilenceTrimTransform>(Transformers.audio().silenceTrim())
    }

    @Test
    fun audioCompressorCreatesCorrectType() {
        assertIs<AudioCompressorTransform>(Transformers.audio().compressor())
    }

    @Test
    fun audioChannelMapCreatesCorrectType() {
        assertIs<AudioChannelMapTransform>(Transformers.audio().channelMap(intArrayOf(0, 1)))
    }

    // -- Video transforms ---

    @Test
    fun videoTrimCreatesCorrectType() {
        assertIs<VideoTrimTransform>(Transformers.video().trim(startMs = 0, endMs = 5000))
    }

    @Test
    fun videoResizeCreatesCorrectType() {
        assertIs<VideoResizeTransform>(Transformers.video().resize(1280, 720))
    }

    @Test
    fun videoFrameRateCreatesCorrectType() {
        assertIs<VideoFrameRateTransform>(Transformers.video().frameRate(30.0))
    }

    @Test
    fun videoRemoveAudioCreatesCorrectType() {
        assertIs<VideoRemoveAudioTransform>(Transformers.video().removeAudio())
    }

    @Test
    fun videoCropCreatesCorrectType() {
        assertIs<VideoCropTransform>(Transformers.video().crop(0, 0, 640, 480))
    }

    @Test
    fun videoSpeedCreatesCorrectType() {
        assertIs<VideoSpeedTransform>(Transformers.video().speed(2.0f))
    }

    @Test
    fun videoRotateCreatesCorrectType() {
        assertIs<VideoRotateTransform>(Transformers.video().rotate(90))
    }

    // -- Factory singletons ---

    @Test
    fun imageFactoryReturnsSameInstance() {
        val a = Transformers.image()
        val b = Transformers.image()
        assertIs<ImageTransforms>(a)
        assertIs<ImageTransforms>(b)
    }

    @Test
    fun audioFactoryReturnsSameInstance() {
        val a = Transformers.audio()
        val b = Transformers.audio()
        assertIs<AudioTransforms>(a)
        assertIs<AudioTransforms>(b)
    }

    @Test
    fun videoFactoryReturnsSameInstance() {
        val a = Transformers.video()
        val b = Transformers.video()
        assertIs<VideoTransforms>(a)
        assertIs<VideoTransforms>(b)
    }
}
