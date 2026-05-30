package tech.echo.app.core.audio

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * WAV 落盘单测：验证头部字段和数据长度正确，能被标准解析器读出。
 */
class WavWriterTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `写入后头部 RIFF WAVE 标识与长度正确`() {
        val file = tmp.newFile("seg.wav")
        val writer = WavWriter(file)
        writer.open()
        // 写 3 帧，每帧 512 采样点 → 512*2 字节
        repeat(3) { writer.writeFrame(ShortArray(AudioConfig.FRAME_SAMPLES) { 1 }) }
        writer.close()

        val pcmBytes = 3 * AudioConfig.FRAME_SAMPLES * 2
        val totalExpected = WavWriter.WAV_HEADER_SIZE + pcmBytes
        assertEquals("文件总长 = 头 + PCM", totalExpected.toLong(), file.length())

        RandomAccessFile(file, "r").use { raf ->
            val header = ByteArray(WavWriter.WAV_HEADER_SIZE)
            raf.readFully(header)
            val bb = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)

            val riff = ByteArray(4).also { bb.get(it) }
            assertEquals("RIFF", String(riff, Charsets.US_ASCII))
            val riffLen = bb.int
            assertEquals(36 + pcmBytes, riffLen)
            val wave = ByteArray(4).also { bb.get(it) }
            assertEquals("WAVE", String(wave, Charsets.US_ASCII))

            // 跳到采样率字段（偏移 24）
            bb.position(24)
            assertEquals(AudioConfig.SAMPLE_RATE, bb.int)

            // data chunk 长度在偏移 40
            bb.position(40)
            assertEquals(pcmBytes, bb.int)
        }
    }

    @Test
    fun `writeFrames 批量写入字节数正确`() {
        val file = tmp.newFile("seg2.wav")
        val writer = WavWriter(file)
        writer.open()
        val frames = List(5) { ShortArray(AudioConfig.FRAME_SAMPLES) }
        writer.writeFrames(frames)
        writer.close()

        val pcmBytes = 5 * AudioConfig.FRAME_SAMPLES * 2
        assertEquals((WavWriter.WAV_HEADER_SIZE + pcmBytes).toLong(), file.length())
    }
}
