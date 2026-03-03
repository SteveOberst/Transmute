package dev.transmute.model.structure.audio

import dev.transmute.model.core.Bytes
import dev.transmute.model.core.Channels
import dev.transmute.model.core.Hertz
import dev.transmute.model.core.asBytes
import dev.transmute.model.identify.Brand
import dev.transmute.model.identify.FourCC
import dev.transmute.model.identify.RiffChunkId
import dev.transmute.model.structure.audio.types.AacProfile
import dev.transmute.model.structure.audio.types.AacRaw
import dev.transmute.model.structure.audio.types.FlacMetadataBlock
import dev.transmute.model.structure.audio.types.FlacMetadataBlockType
import dev.transmute.model.structure.audio.types.FlacRaw
import dev.transmute.model.structure.audio.types.M4aRaw
import dev.transmute.model.structure.audio.types.Mp3Raw
import dev.transmute.model.structure.audio.types.Mp3VbrInfo
import dev.transmute.model.structure.audio.types.MpegChannelMode
import dev.transmute.model.structure.audio.types.MpegLayer
import dev.transmute.model.structure.audio.types.MpegVersion
import dev.transmute.model.structure.audio.types.OggAudioRaw
import dev.transmute.model.structure.audio.types.OpusRaw
import dev.transmute.model.structure.audio.types.WavAudioFormat
import dev.transmute.model.structure.audio.types.WavRaw
import dev.transmute.model.structure.audio.types.audioFormat
import dev.transmute.model.structure.audio.types.channels
import dev.transmute.model.structure.audio.types.compatibleBrands
import dev.transmute.model.structure.audio.types.firstFrameHeader
import dev.transmute.model.structure.audio.types.fmt
import dev.transmute.model.structure.audio.types.id3v1Tag
import dev.transmute.model.structure.audio.types.majorBrand
import dev.transmute.model.structure.audio.types.minorVersion
import dev.transmute.model.structure.audio.types.opusIdentification
import dev.transmute.model.structure.audio.types.preSkipSamples
import dev.transmute.model.structure.audio.types.sampleRate
import dev.transmute.model.structure.audio.types.streamInfoBlock
import dev.transmute.model.structure.audio.types.streamSerialNumbers
import dev.transmute.model.structure.audio.types.vorbisIdentification
import dev.transmute.model.structure.common.IsoBmffBox
import dev.transmute.model.structure.common.OggPage
import dev.transmute.model.structure.common.OggSerialNumber
import dev.transmute.model.structure.common.RiffChunk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for all audio [MediaStructure] implementations.
 */
class AudioFileTests {

    // -- helpers --

    private fun buildFtypData(major: String, minorVer: Int, compat: List<String>): Bytes {
        val out = ByteArray(8 + compat.size * 4)
        major.encodeToByteArray().copyInto(out, 0)
        out[4] = ((minorVer shr 24) and 0xFF).toByte()
        out[5] = ((minorVer shr 16) and 0xFF).toByte()
        out[6] = ((minorVer shr 8) and 0xFF).toByte()
        out[7] = (minorVer and 0xFF).toByte()
        var pos = 8
        for (b in compat) { b.encodeToByteArray().copyInto(out, pos); pos += 4 }
        return out.asBytes()
    }

    /** Build a 16-byte WAV fmt chunk data (PCM). */
    private fun buildFmtData(
        format: Int = 1, channels: Int = 2, sr: Int = 44100,
        bps: Int = 16,
    ): Bytes {
        val ba = (channels * bps / 8)
        val br = sr * ba
        val out = ByteArray(16)
        fun w16(off: Int, v: Int) { out[off] = (v and 0xFF).toByte(); out[off + 1] = ((v shr 8) and 0xFF).toByte() }
        fun w32(off: Int, v: Int) {
            out[off]   = (v and 0xFF).toByte(); out[off+1] = ((v shr 8) and 0xFF).toByte()
            out[off+2] = ((v shr 16) and 0xFF).toByte(); out[off+3] = ((v shr 24) and 0xFF).toByte()
        }
        w16(0, format); w16(2, channels); w32(4, sr); w32(8, br); w16(12, ba); w16(14, bps)
        return out.asBytes()
    }

    // -- Aac --

    @Test
    fun aacFileConstruction() {
        val file = AacRaw(data = ByteArray(100).asBytes())
        assertEquals(100, file.toBytes().data.size)
    }

    @Test
    fun aacProfileEnum() {
        assertEquals(5, AacProfile.entries.size)
        assertEquals(AacProfile.AacLc, AacProfile.fromObjectType(2))
        assertNull(AacProfile.fromObjectType(99))
    }

    @Test
    fun aacFirstFrameHeaderFromAdts() {
        // Build a minimal 7-byte ADTS header:
        // syncword=0xFFF, id=0(MPEG4), layer=00, protection=1(no CRC)
        // profile=1(AAC-LC, stored as 01 = objectType-1)
        // samplingFreqIdx=3(48000), private=0, channelConfig=2
        // ...
        val d = ByteArray(9)
        d[0] = 0xFF.toByte()
        // 1111 1001 -> sync(4)+id(0=mpeg4)+layer(00)+protection(1=no CRC)
        d[1] = 0xF1.toByte()
        // 01 01 0 010 -> profile(01=LC) | srIdx(0101=3? No, let's use 0100=4=44100) => let me recalculate
        // profile=01(AAC-LC=objType2-1=1), srIdx=0100(44100), private=0, chCfg high bit=0
        // byte2: PP SSSS P C = 01 0100 0 0 = 0x50
        d[2] = 0x50.toByte()
        // chCfg remaining 2 bits=10 (chCfg=2), ...frame length bits etc
        // byte3: CC OOO O OO FF = 10 000 0 00 00 -> but we need frameLength too
        // Let frameLength = 9 (just the header): bits 12-0
        // byte3: CC xx xxxx = channelCfg(10) + ... 
        // frame_length[12:11] in bits 2-3 of byte3
        // byte3 = chCfg(2 bits) | original_copy(1 bit) | home(1 bit) | copy_id(1 bit) | copy_start(1 bit) | frame_len[12:11](2 bits)
        // chCfg lower 2 = 10, original=0, home=0, copy_id=0, copy_start=0, frame_len[12:11]=00
        d[3] = 0x80.toByte()
        // byte4 = frame_len[10:3] -> 9 = 0b0000_01001 -> bits[10:3] = 0000_0100 = 0x01
        // Actually frame_length = 9, in 13 bits = 0_0000_0000_1001
        // bits [12:11] = 00 (in byte3), bits [10:3] = 00000001 (byte4), bits [2:0] + buffer fullness...
        d[4] = 0x01.toByte()
        // byte5: frame_len[2:0] | buffer_fullness[10:6] -> 001 + 11111 = 0x3F
        d[5] = 0x3F.toByte()
        // byte6: buffer_fullness[5:0] | num_raw_data_blocks[1:0] -> 111111 00 = 0xFC
        d[6] = 0xFC.toByte()

        val file = AacRaw(data = d.asBytes())
        val hdr = file.firstFrameHeader
        assertNotNull(hdr)
        assertEquals(Hertz(44100), hdr.sampleRate)
        assertEquals(Channels(2), hdr.channels)
        assertEquals(AacProfile.AacLc, hdr.profile)
        assertTrue(hdr.isMpeg4)
    }

    // -- Flac --

    @Test
    fun flacFileConstruction() {
        val streamInfoData = ByteArray(34) // 34 bytes of STREAMINFO
        // Set sample rate = 44100 in bits [80..99] -> bytes [10..12] packed
        // sampleRate is 20 bits at offset 80: bytes 10..12 high nibble
        // 44100 = 0xAC44 -> 20 bits = 0x0AC44
        // byte10 = 0x0A, byte11 = 0xC4, byte12 high nibble = 0x4
        streamInfoData[10] = 0x0A.toByte()
        streamInfoData[11] = 0xC4.toByte()
        // channels-1 (3 bits) = 1 (stereo) -> 001
        // bps-1 (5 bits) = 15 (16-bit) -> 01111
        // byte12 = sampleRate[3:0] | channels-1[2:0] | bps-1[4]
        //        = 0100 | 001 | 0 = 0x42
        streamInfoData[12] = 0x42.toByte()
        // byte13 = bps-1[3:0] | totalSamples[35:32]
        //        = 1111 | 0000 = 0xF0
        streamInfoData[13] = 0xF0.toByte()

        val block = FlacMetadataBlock(FlacMetadataBlockType.StreamInfo, isLast = true, data = streamInfoData.asBytes())
        val file = FlacRaw(metadataBlocks = listOf(block), audioData = ByteArray(50).asBytes())

        assertEquals(Hertz(44100), file.sampleRate)
        assertEquals(Channels(2), file.channels)
        assertNotNull(file.streamInfoBlock)

        // toBytes() should start with "fLaC"
        val bytes = file.toBytes()
        assertEquals(0x66, bytes.data[0].toInt()) // 'f'
        assertEquals(0x4C, bytes.data[1].toInt()) // 'L'
        assertEquals(0x61, bytes.data[2].toInt()) // 'a'
        assertEquals(0x43, bytes.data[3].toInt()) // 'C'
    }

    @Test
    fun flacMetadataBlockTypes() {
        assertEquals(8, FlacMetadataBlockType.entries.size)
        assertEquals(0, FlacMetadataBlockType.StreamInfo.code)
        assertEquals(FlacMetadataBlockType.Picture, FlacMetadataBlockType.fromCode(6))
        assertEquals(FlacMetadataBlockType.Unknown, FlacMetadataBlockType.fromCode(99))
    }

    @Test
    fun flacMetadataBlockToBytes() {
        val data = byteArrayOf(1, 2, 3, 4).asBytes()
        val block = FlacMetadataBlock(FlacMetadataBlockType.Padding, isLast = false, data = data)
        val bytes = block.toBytes()
        // header: type=1, isLast=false -> byte0 = 0x01
        // length = 4 -> bytes 1-3 = 0x000004
        assertEquals(0x01, bytes.data[0].toInt())
        assertEquals(0x00, bytes.data[1].toInt())
        assertEquals(0x00, bytes.data[2].toInt())
        assertEquals(0x04, bytes.data[3].toInt())
        // followed by the data
        assertEquals(1, bytes.data[4].toInt())
    }

    // -- Mp3 --

    @Test
    fun mp3FileConstruction() {
        val file = Mp3Raw(id3v2Tag = null, audioData = ByteArray(100).asBytes(), id3v1TagData = null)
        assertEquals(100, file.toBytes().data.size)
        assertNull(file.id3v1Tag)
    }

    @Test
    fun mp3Id3v1TagParsing() {
        val tag = ByteArray(128)
        tag[0] = 'T'.code.toByte()
        tag[1] = 'A'.code.toByte()
        tag[2] = 'G'.code.toByte()
        // title at 3..32, artist at 33..62, album at 63..92, year at 93..96
        "Hello".encodeToByteArray().copyInto(tag, 3)
        tag[127] = 0xFF.toByte() // genre 255

        val file = Mp3Raw(null, ByteArray(10).asBytes(), tag.asBytes())
        val parsed = file.id3v1Tag
        assertNotNull(parsed)
        assertEquals("Hello", parsed.title)
        assertEquals(255, parsed.genre)
    }

    @Test
    fun mp3FrameHeaderParsing() {
        // Valid MPEG1 Layer3 frame header: sync=0xFFE0, v=11(MPEG1), layer=01(L3), prot=1(no CRC)
        // -> 0xFF 0xFB
        // bitrate=1001(320kbps for MPEG1 L3), sampleRate=00(44100), padding=0, private=0
        // -> 0x90
        // channelMode=01(JointStereo), modeExt=00, copyright=0, original=0, emphasis=00
        // -> 0x40
        val frameData = byteArrayOf(0xFF.toByte(), 0xFB.toByte(), 0x90.toByte(), 0x40.toByte())
        val file = Mp3Raw(null, (frameData + ByteArray(100)).asBytes(), null)
        val hdr = file.firstFrameHeader
        assertNotNull(hdr)
        assertEquals(MpegVersion.Mpeg1, hdr.version)
        assertEquals(MpegLayer.Layer3, hdr.layer)
        assertEquals(Hertz(44100), hdr.sampleRate)
        assertEquals(MpegChannelMode.JointStereo, hdr.channelMode)
    }

    @Test
    fun mp3VbrInfoDataClass() {
        val vbr = Mp3VbrInfo()
        assertNull(vbr.totalFrames)
        assertNull(vbr.totalBytes)
        assertNull(vbr.qualityIndicator)
    }

    @Test
    fun mpegEnums() {
        assertEquals(3, MpegVersion.entries.size)
        assertEquals(3, MpegLayer.entries.size)
        assertEquals(4, MpegChannelMode.entries.size)
    }

    // -- M4a --

    @Test
    fun m4aFileWithFtyp() {
        val ftypData = buildFtypData("M4A ", 512, listOf("isom", "M4A "))
        val file = M4aRaw(boxes = listOf(IsoBmffBox(FourCC("ftyp"), ftypData)))

        assertEquals(Brand(FourCC("M4A ")), file.majorBrand)
        assertEquals(512u, file.minorVersion)
        assertEquals(2, file.compatibleBrands.size)
    }

    // -- OggAudio --

    @Test
    fun oggAudioFileConstruction() {
        val file = OggAudioRaw(pages = emptyList())
        assertEquals(0, file.toBytes().data.size)
        assertTrue(file.streamSerialNumbers.isEmpty())
    }

    @Test
    fun oggAudioVorbisIdentification() {
        // Build a BOS page with Vorbis ID header packet
        val vorbisIdPacket = ByteArray(30)
        vorbisIdPacket[0] = 1 // packet type = identification
        "vorbis".encodeToByteArray().copyInto(vorbisIdPacket, 1)
        // vorbis version = 0 at offset 7 (4 bytes LE)
        // channels = 2 at offset 11
        vorbisIdPacket[11] = 2
        // sampleRate = 44100 at offset 12 (4 bytes LE) = 0xAC44
        vorbisIdPacket[12] = 0x44; vorbisIdPacket[13] = 0xAC.toByte()
        // blockSizes at offset 28: block0=8(1<<8=256), block1=11(1<<11=2048) -> 0xB8
        vorbisIdPacket[28] = 0xB8.toByte()

        val page = OggPage(
            headerType = 0x02u.toUByte(), // BOS
            granulePosition = 0L,
            serialNumber = OggSerialNumber(42),
            pageSequence = 0u,
            crc = 0u,
            segmentTable = byteArrayOf(30).asBytes(),
            data = vorbisIdPacket.asBytes(),
        )
        val file = OggAudioRaw(pages = listOf(page))

        assertEquals(Hertz(44100), file.sampleRate)
        assertEquals(Channels(2), file.channels)

        val vorbisId = file.vorbisIdentification
        assertNotNull(vorbisId)
        assertEquals(0u, vorbisId.vorbisVersion)
        assertEquals(256, vorbisId.blockSize0)
        assertEquals(2048, vorbisId.blockSize1)
    }

    // -- Opus --

    @Test
    fun opusFileConstruction() {
        val file = OpusRaw(pages = emptyList())
        // Default sample rate is 48000 (Opus standard)
        assertEquals(Hertz(48000), file.sampleRate)
    }

    @Test
    fun opusIdentificationParsing() {
        // Build OpusHead packet (19 bytes min)
        val opusHead = ByteArray(19)
        "OpusHead".encodeToByteArray().copyInto(opusHead, 0)
        opusHead[8] = 1 // version
        opusHead[9] = 2 // channels
        // preSkip = 312 LE = 0x0138
        opusHead[10] = 0x38; opusHead[11] = 0x01
        // inputSampleRate = 48000 LE = 0xBB80
        opusHead[12] = 0x80.toByte(); opusHead[13] = 0xBB.toByte()
        // outputGain = 0
        // channelMappingFamily = 0

        val page = OggPage(
            headerType = 0x02u.toUByte(),
            granulePosition = 0L,
            serialNumber = OggSerialNumber(1),
            pageSequence = 0u,
            crc = 0u,
            segmentTable = byteArrayOf(19).asBytes(),
            data = opusHead.asBytes(),
        )
        val file = OpusRaw(pages = listOf(page))

        assertEquals(Hertz(48000), file.sampleRate)
        assertEquals(Channels(2), file.channels)
        assertEquals(312, file.preSkipSamples)

        val id = file.opusIdentification
        assertNotNull(id)
        assertEquals(1, id.version)
    }

    // -- Wav --

    @Test
    fun wavFileWithFmtChunk() {
        val fmtData = buildFmtData(format = 1, channels = 2, sr = 44100, bps = 16)
        val fmtChunk = RiffChunk(RiffChunkId("fmt "), 16u, data = fmtData)
        val dataChunk = RiffChunk(RiffChunkId("data"), 100u, data = ByteArray(100).asBytes())
        val riff = RiffChunk(RiffChunkId("RIFF"), 128u, formType = RiffChunkId("WAVE"), children = listOf(fmtChunk, dataChunk))
        val file = WavRaw(riff)

        val fmt = file.fmt
        assertNotNull(fmt)
        assertEquals(44100u, fmt.sampleRate)
        assertEquals(2u.toUShort(), fmt.numChannels)
        assertEquals(16u.toUShort(), fmt.bitsPerSample)
        assertEquals(WavAudioFormat.Pcm, file.audioFormat)
    }

    @Test
    fun wavAudioFormatEnum() {
        assertEquals(5, WavAudioFormat.entries.size)
        assertEquals(WavAudioFormat.Pcm, WavAudioFormat.fromCode(1u.toUShort()))
        assertNull(WavAudioFormat.fromCode(999u.toUShort()))
    }

    @Test
    fun wavTypedAccessors() {
        val fmtData = buildFmtData(format = 1, channels = 1, sr = 22050, bps = 8)
        val fmtChunk = RiffChunk(RiffChunkId("fmt "), 16u, data = fmtData)
        val riff = RiffChunk(RiffChunkId("RIFF"), 28u, formType = RiffChunkId("WAVE"), children = listOf(fmtChunk))
        val file = WavRaw(riff)

        assertEquals(Hertz(22050), file.sampleRate)
        assertEquals(Channels(1), file.channels)
    }
}
