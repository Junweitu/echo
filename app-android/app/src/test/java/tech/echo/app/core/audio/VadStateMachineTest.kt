package tech.echo.app.core.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * VAD 状态机单测（codex-core-audio.md §3 验收点 1/7：说话才落盘、静音不落盘）。
 *
 * 用固定参数构造，便于精确算帧数：
 * - speechStartFrames=3：连续 3 帧有声才确认起始
 * - silenceEndFrames=5：连续 5 帧静音才判结束
 * - frameMs=32：每帧 32ms
 * - minSegmentMs=100：有效内容 < 100ms（约 3 帧）丢弃
 * - maxSegmentMs=320：10 帧强制切段
 */
class VadStateMachineTest {

    private fun newSm() = VadStateMachine(
        threshold = 0.5f,
        speechStartFrames = 3,
        silenceEndFrames = 5,
        frameMs = 32,
        minSegmentMs = 100,
        maxSegmentMs = 320,
    )

    /** 喂一串概率，收集事件。 */
    private fun feedAll(sm: VadStateMachine, probs: List<Float>): List<VadEvent> =
        probs.map { sm.feed(it) }

    private fun voiced(n: Int) = List(n) { 0.9f }
    private fun silent(n: Int) = List(n) { 0.1f }

    @Test
    fun `连续有声不足阈值帧数不触发起始`() {
        val sm = newSm()
        // 只有 2 帧有声（< speechStartFrames=3），不应触发
        val events = feedAll(sm, voiced(2))
        assertTrue(events.all { it is VadEvent.Idle })
    }

    @Test
    fun `孤立短噪声不产生段`() {
        val sm = newSm()
        // 3 帧有声触发起始，但紧接静音，有效内容太短 → 应丢弃而非落盘
        // 起始确认期 3 帧并入段，之后立即 5 帧静音结束
        // effectiveFrames = segmentFrames(3 起始 + 5 静音=8) - 5 静音 = 3 帧 = 96ms < 100ms → 丢弃
        val events = feedAll(sm, voiced(3) + silent(5))
        val ends = events.filterIsInstance<VadEvent.SegmentEnd>()
        val discarded = events.filterIsInstance<VadEvent.SegmentDiscarded>()
        assertEquals("不应有正常落盘", 0, ends.size)
        assertEquals("应丢弃一次", 1, discarded.size)
    }

    @Test
    fun `正常说话一段在静音后落盘`() {
        val sm = newSm()
        // 起始 3 帧 + 持续有声 5 帧 + 结束静音 5 帧
        // segmentFrames = 3(起始并入) + 5(持续) + 5(静音) = 13
        // effectiveFrames = 13 - 5 = 8 帧 = 256ms > 100ms → SegmentEnd
        val events = feedAll(sm, voiced(3) + voiced(5) + silent(5))
        val starts = events.filterIsInstance<VadEvent.SegmentStart>()
        val ends = events.filterIsInstance<VadEvent.SegmentEnd>()
        assertEquals(1, starts.size)
        assertEquals(1, ends.size)
        assertEquals(256L, ends.first().durationMs)
    }

    @Test
    fun `段中短暂停顿不结束段`() {
        // 用更大的 maxSegmentMs，避免本用例帧数撞上切段上限，专注验证"短停顿不断段"
        val sm = VadStateMachine(
            threshold = 0.5f,
            speechStartFrames = 3,
            silenceEndFrames = 5,
            frameMs = 32,
            minSegmentMs = 100,
            maxSegmentMs = 10_000,
        )
        // 起始后，中间夹 4 帧静音（< silenceEndFrames=5）不应结束，继续录
        val events = feedAll(sm, voiced(3) + silent(4) + voiced(3) + silent(5))
        val ends = events.filterIsInstance<VadEvent.SegmentEnd>()
        assertEquals("中途停顿不该断段，最终只结束 1 次", 1, ends.size)
    }

    @Test
    fun `超过最长时长强制切段`() {
        val sm = newSm()
        // maxSegmentMs=320 → 10 帧切段。持续喂 12 帧有声应触发一次 Split
        val events = feedAll(sm, voiced(12))
        val splits = events.filterIsInstance<VadEvent.SegmentSplit>()
        assertEquals(1, splits.size)
        assertEquals(320L, splits.first().durationMs)
    }

    @Test
    fun `forceEnd 在录入中返回落盘 在静音中返回 Idle`() {
        val sm = newSm()
        // 静音态 forceEnd → Idle
        assertTrue(sm.forceEnd() is VadEvent.Idle)

        // 进入录入后 forceEnd → 落盘（若够长）
        feedAll(sm, voiced(3) + voiced(5)) // segmentFrames=8, 无尾静音
        val ev = sm.forceEnd()
        assertTrue("录入中强制结束应落盘", ev is VadEvent.SegmentEnd)
        assertEquals(256L, (ev as VadEvent.SegmentEnd).durationMs)
    }

    @Test
    fun `结束后能重新开始新段`() {
        val sm = newSm()
        feedAll(sm, voiced(3) + voiced(5) + silent(5)) // 第一段
        val events2 = feedAll(sm, voiced(3) + voiced(5) + silent(5)) // 第二段
        assertEquals(1, events2.filterIsInstance<VadEvent.SegmentStart>().size)
        assertEquals(1, events2.filterIsInstance<VadEvent.SegmentEnd>().size)
    }
}
