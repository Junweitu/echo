package tech.echo.app.core.audio

import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import tech.echo.app.core.data.db.SegmentEntity
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 录音引擎端到端单测（codex-core-audio.md §3 验收点 1/2）：
 * - 说话才落盘、静音不落盘（VAD 链路生效）。
 * - 片段开头不丢字（环形预录缓冲把触发点之前的历史帧补进段开头）。
 *
 * 用「假 VAD」按帧序给定概率，脱离真机用合成 PCM 验证整条链路。
 */
class RecordingEngineTest {

    @get:Rule
    val tmp = TemporaryFolder()

    /** 假 VAD：按预设概率序列逐帧返回，不依赖 ONNX。 */
    private class FakeVad(private val probs: List<Float>) : VadDetector {
        private var i = 0
        override fun probability(frame: ShortArray): Float =
            probs.getOrElse(i++) { 0f }
        override fun reset() { i = 0 }
        override fun close() {}
    }

    /** 用帧内首个采样值作为该帧的「编号」，便于断言补进段开头的是哪些历史帧。 */
    private fun tagged(tag: Int): ShortArray =
        ShortArray(AudioConfig.FRAME_SAMPLES) { tag.toShort() }

    /** 读出 WAV 的 PCM 部分，按帧还原每帧的 tag（取首采样）。 */
    private fun readFrameTags(file: File): List<Int> {
        RandomAccessFile(file, "r").use { raf ->
            raf.seek(WavWriter.WAV_HEADER_SIZE.toLong())
            val pcmLen = raf.length() - WavWriter.WAV_HEADER_SIZE
            val bytes = ByteArray(pcmLen.toInt())
            raf.readFully(bytes)
            val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            val frameBytes = AudioConfig.FRAME_SAMPLES * 2
            val tags = mutableListOf<Int>()
            var off = 0
            while (off + frameBytes <= bytes.size) {
                tags.add(bb.getShort(off).toInt()) // 每帧首采样即 tag
                off += frameBytes
            }
            return tags
        }
    }
    // __CONTINUE_HERE__

    private fun newEngine(
        audioDir: File,
        vad: VadDetector,
        recorded: MutableList<SegmentEntity>,
    ) = RecordingEngine(
        audioDir = audioDir,
        vad = vad,
        onSegmentRecorded = { recorded.add(it) },
    )

    @Test
    fun `全程静音不落盘也不入库`() = runTest {
        val dir = tmp.newFolder("audio")
        val recorded = mutableListOf<SegmentEntity>()
        // 60 帧全静音（概率 0）
        val frames = List(60) { tagged(0) }
        val vad = FakeVad(List(60) { 0f })

        newEngine(dir, vad, recorded).run(frames.asFlow()) { 1_000L }

        assertEquals("静音不应入库", 0, recorded.size)
        val files = dir.walkTopDown().filter { it.isFile }.toList()
        assertTrue("静音不应落盘任何音频", files.isEmpty())
    }

    @Test
    fun `前置静音突然说话 落盘且补回触发点之前的预录帧`() = runTest {
        val dir = tmp.newFolder("audio")
        val recorded = mutableListOf<SegmentEntity>()

        // 帧编排（tag 标记来源）：
        //   静音前缀 tag=7（远多于预录容量，确保环形缓冲被填满）
        //   说话    tag=1（前 3 帧用于确认起始，其中第 3 帧为触发帧）
        //   收尾静音 tag=9（凑满 SILENCE_END_FRAMES 帧判定段结束）
        val prefixCount = AudioConfig.PREROLL_FRAMES + 20
        val voicedTotal = 43               // 有效内容 43 帧 → 43*32=1376ms > MIN_SEGMENT_MS(1000)
        val trailing = AudioConfig.SILENCE_END_FRAMES

        val frames = buildList {
            repeat(prefixCount) { add(tagged(7)) }
            repeat(voicedTotal) { add(tagged(1)) }
            repeat(trailing) { add(tagged(9)) }
        }
        val probs = buildList {
            repeat(prefixCount) { add(0f) }
            repeat(voicedTotal) { add(0.9f) }
            repeat(trailing) { add(0f) }
        }

        newEngine(dir, FakeVad(probs), recorded).run(frames.asFlow()) { 1_700_000_000_000L }

        // 1) 说话才落盘：恰好一段
        assertEquals("应落盘一段", 1, recorded.size)
        val seg = recorded.first()

        // 2) 时长正确：有效内容 = voicedTotal 帧
        assertEquals(voicedTotal.toLong() * AudioConfig.FRAME_MS, seg.durationMs)

        // 3) 文件确实存在于 audio/{yyyyMMdd}/ 下
        val file = File(seg.audioPath)
        assertTrue("音频文件应存在", file.exists())

        // 4) 防丢开头：文件开头必须是触发点之前的预录静音帧（tag=7），
        //    且预录帧数 = 环形缓冲容量 - 2（触发前 2 个未确认有声帧也在缓冲里）
        val tags = readFrameTags(file)
        assertEquals("段首帧应是预录历史帧", 7, tags.first())
        val tag7InPreroll = AudioConfig.PREROLL_FRAMES - 2
        assertEquals("应补回满缓冲的预录静音帧", tag7InPreroll, tags.count { it == 7 })

        // 5) 总帧数 = 预录静音帧(tag7) + 全部有声内容 + 收尾静音帧。
        //    注意：触发前 2 个未确认有声帧已在环形缓冲里随 snapshot 补进段首，
        //    它们既属预录又属 voicedTotal，故总数用 tag7InPreroll(=46-2) 计，避免重复。
        val expectedTotal = tag7InPreroll + voicedTotal + trailing
        assertEquals("落盘总帧数应等于预录静音+内容+收尾", expectedTotal, tags.size)
    }

    @Test
    fun `两段说话之间静音 分别落盘两段`() = runTest {
        val dir = tmp.newFolder("audio")
        val recorded = mutableListOf<SegmentEntity>()
        val voiced = 43
        val gapAndEnd = AudioConfig.SILENCE_END_FRAMES

        val frames = buildList {
            repeat(10) { add(tagged(7)) }            // 前导静音
            repeat(voiced) { add(tagged(1)) }        // 第一段
            repeat(gapAndEnd) { add(tagged(9)) }     // 段间静音（结束第一段）
            repeat(voiced) { add(tagged(2)) }        // 第二段
            repeat(gapAndEnd) { add(tagged(9)) }     // 结束第二段
        }
        val probs = buildList {
            repeat(10) { add(0f) }
            repeat(voiced) { add(0.9f) }
            repeat(gapAndEnd) { add(0f) }
            repeat(voiced) { add(0.9f) }
            repeat(gapAndEnd) { add(0f) }
        }

        newEngine(dir, FakeVad(probs), recorded).run(frames.asFlow()) { 1_700_000_000_000L }

        assertEquals("应落盘两段", 2, recorded.size)
        recorded.forEach {
            assertEquals(voiced.toLong() * AudioConfig.FRAME_MS, it.durationMs)
            assertTrue(File(it.audioPath).exists())
        }
    }
}
