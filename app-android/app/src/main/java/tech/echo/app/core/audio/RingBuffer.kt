package tech.echo.app.core.audio

/**
 * 环形预录缓冲（见 codex-core-audio.md §2.4 —— 产品核心卖点：防丢句子开头）。
 *
 * 永远滚动保存最近 [capacityFrames] 帧 PCM。VAD 一旦确认"录入中"，
 * 调 [snapshot] 把缓冲区历史帧接到本段录音开头，保证"喂、那个…"这类开头不丢。
 *
 * 设计：
 * - 定长 Array<ShortArray?> 作环形存储，写满后覆盖最旧帧（head 前移）。
 * - 非线程安全；调用方（单一音频线程）串行 push/snapshot，不跨线程共享。
 *   若未来需跨线程，由外部加锁。
 */
class RingBuffer(
    private val capacityFrames: Int = AudioConfig.PREROLL_FRAMES,
) {
    init {
        require(capacityFrames > 0) { "capacityFrames 必须为正：$capacityFrames" }
    }

    private val frames = arrayOfNulls<ShortArray>(capacityFrames)

    /** 下一个写入位置（环形）。 */
    private var writeIndex = 0

    /** 当前已存帧数（未写满时 < capacity，写满后恒等于 capacity）。 */
    private var count = 0

    /** 当前缓存帧数。 */
    val size: Int get() = count

    /** 是否已写满。 */
    val isFull: Boolean get() = count == capacityFrames

    /**
     * 压入一帧。满了则覆盖最旧的一帧。
     * 复制传入数组，避免调用方复用同一 ShortArray 导致历史被改写。
     */
    fun push(frame: ShortArray) {
        frames[writeIndex] = frame.copyOf()
        writeIndex = (writeIndex + 1) % capacityFrames
        if (count < capacityFrames) count++
    }

    /**
     * 按时间先后（最旧→最新）导出当前所有帧的拷贝。
     * 用于 VAD 触发录入瞬间，把这些历史帧拼到段开头。
     */
    fun snapshot(): List<ShortArray> {
        if (count == 0) return emptyList()
        // 未写满时数据从索引 0 开始；写满后最旧帧在 writeIndex 处。
        val start = if (count < capacityFrames) 0 else writeIndex
        return List(count) { i ->
            val idx = (start + i) % capacityFrames
            frames[idx]!!.copyOf()
        }
    }

    /** 清空缓冲（一段开始录入后通常清掉，避免下段重复补同一批历史）。 */
    fun clear() {
        for (i in frames.indices) frames[i] = null
        writeIndex = 0
        count = 0
    }
}
