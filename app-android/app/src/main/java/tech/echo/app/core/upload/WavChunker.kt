package tech.echo.app.core.upload

import tech.echo.app.core.audio.AudioConfig
import tech.echo.app.core.audio.WavWriter
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 將 Echo 產生的 PCM WAV 暫時切成較短片段，只供 Samsung/Bixby ASR 使用。
 * 原始錄音檔完全不修改；暫存片段用完即刪除。
 */
object WavChunker {

    data class Chunk(
        val file: File,
        val startMs: Long,
        val endMs: Long,
        val temporary: Boolean,
    )

    fun split(
        source: File,
        targetDir: File,
        chunkMs: Long,
        overlapMs: Long,
    ): List<Chunk> {
        require(source.exists()) { "WAV 檔不存在：${source.absolutePath}" }
        require(chunkMs > 1_000L)
        require(overlapMs >= 0L && overlapMs < chunkMs)

        val bytesPerSecond = AudioConfig.SAMPLE_RATE * 2L // mono, 16-bit
        val pcmBytes = (source.length() - WavWriter.WAV_HEADER_SIZE).coerceAtLeast(0L)
        val durationMs = pcmBytes * 1000L / bytesPerSecond

        if (durationMs <= chunkMs) {
            return listOf(Chunk(source, 0L, durationMs, temporary = false))
        }

        targetDir.mkdirs()
        targetDir.listFiles()?.forEach { runCatching { it.delete() } }

        val bytesPerMs = bytesPerSecond / 1000L // 32 bytes/ms at 16 kHz, mono, 16-bit
        val chunkBytes = chunkMs * bytesPerMs
        val overlapBytes = overlapMs * bytesPerMs
        val chunks = mutableListOf<Chunk>()

        var startByte = 0L
        var index = 0
        while (startByte < pcmBytes) {
            val endByte = minOf(startByte + chunkBytes, pcmBytes)
            val out = File(targetDir, "%s-%02d.wav".format(source.nameWithoutExtension, index + 1))
            writeSlice(source, out, startByte, endByte)
            chunks += Chunk(
                file = out,
                startMs = startByte / bytesPerMs,
                endMs = endByte / bytesPerMs,
                temporary = true,
            )

            if (endByte >= pcmBytes) break
            startByte = (endByte - overlapBytes).coerceAtLeast(startByte + bytesPerMs)
            index += 1
        }

        return chunks
    }

    private fun writeSlice(source: File, target: File, startPcmByte: Long, endPcmByte: Long) {
        val dataLen = (endPcmByte - startPcmByte).toInt()
        require(dataLen > 0)

        val header = ByteArray(WavWriter.WAV_HEADER_SIZE)
        RandomAccessFile(source, "r").use { input ->
            input.readFully(header)
            patchLittleEndianInt(header, 4, 36 + dataLen)
            patchLittleEndianInt(header, 40, dataLen)

            target.parentFile?.mkdirs()
            RandomAccessFile(target, "rw").use { output ->
                output.setLength(0)
                output.write(header)
                input.seek(WavWriter.WAV_HEADER_SIZE.toLong() + startPcmByte)

                var remaining = dataLen.toLong()
                val buffer = ByteArray(64 * 1024)
                while (remaining > 0L) {
                    val want = minOf(buffer.size.toLong(), remaining).toInt()
                    val read = input.read(buffer, 0, want)
                    check(read > 0) { "切割 WAV 時提前讀到 EOF" }
                    output.write(buffer, 0, read)
                    remaining -= read
                }
            }
        }
    }

    private fun patchLittleEndianInt(bytes: ByteArray, offset: Int, value: Int) {
        ByteBuffer.wrap(bytes, offset, 4)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putInt(value)
    }
}
