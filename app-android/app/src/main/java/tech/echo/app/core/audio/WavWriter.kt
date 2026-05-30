package tech.echo.app.core.audio

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * WAV 落盘器（见 codex-core-audio.md §2.6）。
 *
 * 阶段 1 落 WAV（PCM 16bit/16kHz/mono），可直接回听验证。
 * TODO(阶段2): 替换为 Opus 编码省空间——抽象保持不变（open/writeFrame/close）。
 *
 * 流式写：先占位 44 字节头，逐帧追加 PCM，[close] 时回填 RIFF/data 长度。
 */
class WavWriter(val file: File) {

    private var raf: RandomAccessFile? = null
    private var pcmBytes = 0L

    fun open() {
        file.parentFile?.mkdirs()
        raf = RandomAccessFile(file, "rw").apply {
            setLength(0)
            write(ByteArray(WAV_HEADER_SIZE)) // 占位，close 时回填
        }
        pcmBytes = 0
    }

    /** 追加一帧 PCM（ShortArray，小端写入）。 */
    fun writeFrame(frame: ShortArray) {
        val out = raf ?: error("WavWriter 未 open")
        val buf = ByteBuffer.allocate(frame.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (s in frame) buf.putShort(s)
        out.write(buf.array())
        pcmBytes += frame.size * 2
    }

    /** 批量追加（用于补预录历史帧）。 */
    fun writeFrames(frames: List<ShortArray>) = frames.forEach { writeFrame(it) }

    /** 关闭并回填 WAV 头。 */
    fun close() {
        val out = raf ?: return
        try {
            out.seek(0)
            out.write(buildHeader(pcmBytes.toInt()))
        } finally {
            out.close()
            raf = null
        }
    }

    private fun buildHeader(dataLen: Int): ByteArray {
        val sampleRate = AudioConfig.SAMPLE_RATE
        val channels = 1
        val bitsPerSample = 16
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val blockAlign = channels * bitsPerSample / 8
        val riffLen = 36 + dataLen

        return ByteBuffer.allocate(WAV_HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray(Charsets.US_ASCII))
            putInt(riffLen)
            put("WAVE".toByteArray(Charsets.US_ASCII))
            put("fmt ".toByteArray(Charsets.US_ASCII))
            putInt(16)                       // fmt chunk size
            putShort(1)                      // PCM
            putShort(channels.toShort())
            putInt(sampleRate)
            putInt(byteRate)
            putShort(blockAlign.toShort())
            putShort(bitsPerSample.toShort())
            put("data".toByteArray(Charsets.US_ASCII))
            putInt(dataLen)
        }.array()
    }

    companion object {
        const val WAV_HEADER_SIZE = 44
        const val EXT = "wav"
    }
}
