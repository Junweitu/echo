package tech.echo.app.core.audio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 环形预录缓冲单测（codex-core-audio.md §3 验收点 2：防丢句子开头）。
 */
class RingBufferTest {

    private fun frame(vararg v: Int): ShortArray = ShortArray(v.size) { v[it].toShort() }

    @Test
    fun `未写满时按写入顺序返回全部帧`() {
        val rb = RingBuffer(capacityFrames = 4)
        rb.push(frame(1))
        rb.push(frame(2))
        rb.push(frame(3))

        assertEquals(3, rb.size)
        assertFalse(rb.isFull)
        val snap = rb.snapshot()
        assertEquals(3, snap.size)
        assertArrayEquals(frame(1), snap[0])
        assertArrayEquals(frame(2), snap[1])
        assertArrayEquals(frame(3), snap[2])
    }

    @Test
    fun `刚好写满返回全部且标记 full`() {
        val rb = RingBuffer(capacityFrames = 3)
        rb.push(frame(1)); rb.push(frame(2)); rb.push(frame(3))

        assertTrue(rb.isFull)
        assertEquals(3, rb.size)
        assertArrayEquals(frame(1), rb.snapshot()[0])
        assertArrayEquals(frame(3), rb.snapshot()[2])
    }

    @Test
    fun `超量写入覆盖最旧帧并保持时间顺序`() {
        val rb = RingBuffer(capacityFrames = 3)
        // 写 5 帧，容量 3 → 只剩最近 3 帧 [3,4,5]
        for (i in 1..5) rb.push(frame(i))

        val snap = rb.snapshot()
        assertEquals(3, snap.size)
        assertArrayEquals(frame(3), snap[0]) // 最旧
        assertArrayEquals(frame(4), snap[1])
        assertArrayEquals(frame(5), snap[2]) // 最新
    }

    @Test
    fun `push 后修改原数组不影响缓冲内容`() {
        val rb = RingBuffer(capacityFrames = 2)
        val f = frame(7, 8)
        rb.push(f)
        f[0] = 99 // 调用方复用数组
        assertArrayEquals(frame(7, 8), rb.snapshot()[0]) // 缓冲里仍是旧值
    }

    @Test
    fun `snapshot 返回拷贝 修改它不影响缓冲`() {
        val rb = RingBuffer(capacityFrames = 2)
        rb.push(frame(1, 2))
        val snap = rb.snapshot()
        snap[0][0] = 99
        assertArrayEquals(frame(1, 2), rb.snapshot()[0]) // 原缓冲不变
    }

    @Test
    fun `clear 后缓冲为空`() {
        val rb = RingBuffer(capacityFrames = 3)
        rb.push(frame(1)); rb.push(frame(2))
        rb.clear()
        assertEquals(0, rb.size)
        assertFalse(rb.isFull)
        assertTrue(rb.snapshot().isEmpty())
    }

    @Test
    fun `防丢开头 触发瞬间补回触发点之前约1点5秒的历史帧`() {
        // 模拟：缓冲容量 = 预录帧数（约 1.5s）。先灌一段"前置静音"，
        // 再到"突然说话"的触发点，snapshot 应拿到触发点之前的全部历史帧。
        val capacity = AudioConfig.PREROLL_FRAMES
        val rb = RingBuffer(capacityFrames = capacity)

        // 持续推 2 倍容量的帧（用序号标记），模拟一直在滚动保存最近历史
        val totalPushed = capacity * 2
        for (i in 0 until totalPushed) rb.push(frame(i))

        // 触发录入：拿历史帧补到段开头
        val preroll = rb.snapshot()

        // 应正好补回容量上限帧数（最近 1.5s），且是触发点之前最近的那批
        assertEquals(capacity, preroll.size)
        // 第一帧应是 totalPushed - capacity（最旧的保留帧），最后一帧是 totalPushed-1（触发点前一刻）
        assertArrayEquals(frame(totalPushed - capacity), preroll.first())
        assertArrayEquals(frame(totalPushed - 1), preroll.last())
    }
}
