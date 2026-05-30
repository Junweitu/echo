package tech.echo.app.core.audio

import kotlinx.coroutines.flow.Flow
import tech.echo.app.core.data.db.SegmentEntity
import tech.echo.app.core.data.db.SegmentStatus
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.UUID

/**
 * 录音引擎：串起整条端上链路（codex-core-audio.md §2）。
 *
 *   PCM 帧流 → 环形预录缓冲(补开头) → Silero VAD 概率 → 状态机切段 → WAV 落盘 → Room 入库
 *
 * 设计要点：
 * - 无 Android Service 依赖，纯逻辑，便于单测（喂合成帧流即可验证落盘/入库）。
 * - 单协程串行消费帧，VAD / 状态机 / 环形缓冲都非线程安全，由本类保证串行访问。
 * - 落盘失败（磁盘满等）只丢弃当前段并继续录音，不让整条链路崩。
 *
 * @param audioDir 音频根目录（filesDir/audio）。
 * @param vad VAD 检测器（Service 用 SileroVadDetector，测试用假实现）。
 * @param onSegmentRecorded 一段成功落盘后回调（Service 注入 → 写 Room）。
 * @param onStatus 引擎状态变化回调（LISTENING/RECORDING，驱动通知与 UI）。
 */
class RecordingEngine(
    private val audioDir: File,
    private val vad: VadDetector,
    private val onSegmentRecorded: suspend (SegmentEntity) -> Unit,
    private val onStatus: (tech.echo.app.core.model.RecordingStatus) -> Unit = {},
    private val onDiagnostic: (String) -> Unit = {},
) {
    private val ringBuffer = RingBuffer()
    private val stateMachine = VadStateMachine()

    /** 当前正在写的段；null 表示未在录入。 */
    private var writer: WavWriter? = null
    private var currentSegmentId: String? = null
    private var currentStartTime: Long = 0L

    private val dayFormat = SimpleDateFormat("yyyyMMdd", Locale.US)
    private var frameCount = 0
    private var maxProbability = 0f

    /**
     * 消费 PCM 帧流直到流结束或被取消（collect 取消即停采集）。
     *
     * 每帧：先入环形缓冲（始终滚动保存最近 1.5s），再算 VAD 概率喂状态机，
     * 按状态机事件决定 开段补开头 / 累积写入 / 落盘 / 丢弃。
     *
     * @param frames 来自 [AudioCapture] 的 16kHz/mono PCM 帧流。
     * @param nowMs 取当前时间的函数（便于测试注入固定时钟）。
     */
    suspend fun run(frames: Flow<ShortArray>, nowMs: () -> Long = { System.currentTimeMillis() }) {
        vad.reset()
        stateMachine.reset()
        frameCount = 0
        maxProbability = 0f
        onDiagnostic("engine_run_start")
        onStatus(tech.echo.app.core.model.RecordingStatus.LISTENING)
        try {
            frames.collect { frame ->
                frameCount++
                val probability = vad.probability(frame)
                if (probability > maxProbability) maxProbability = probability
                when (val event = stateMachine.feed(probability)) {
                    is VadEvent.SegmentStart -> startSegment(frame, nowMs())
                    is VadEvent.FrameAppended -> writer?.writeFrame(frame)
                    is VadEvent.SegmentEnd -> finishSegment(frame, append = true, durationMs = event.durationMs)
                    is VadEvent.SegmentSplit -> {
                        // 超长强制切段：先落当前段，再把本帧作为新段开头
                        finishSegment(frame, append = false, durationMs = event.durationMs)
                        startSegment(frame, nowMs())
                    }
                    is VadEvent.SegmentDiscarded -> discardSegment()
                    is VadEvent.Idle -> { /* 静音待命，无动作 */ }
                }
                if (frameCount % DIAGNOSTIC_LOG_EVERY_FRAMES == 0) {
                    onDiagnostic("engine_progress frames=$frameCount maxProbability=$maxProbability")
                }
                // 环形缓冲始终保存最近历史（放在事件处理后，避免把刚补过的开头帧重复入缓冲）
                ringBuffer.push(frame)
            }
        } finally {
            // 流结束/取消：把正在写的段强制收尾，避免丢半截
            forceFinish()
            onDiagnostic("engine_run_end frames=$frameCount maxProbability=$maxProbability")
        }
    }

    /** 开新段：建 WAV、把环形缓冲里的历史帧补到开头、写入触发帧。 */
    private fun startSegment(triggerFrame: ShortArray, startTime: Long) {
        val id = UUID.randomUUID().toString()
        val day = dayFormat.format(startTime)
        val file = File(File(audioDir, day), "$id.${WavWriter.EXT}")
        val w = WavWriter(file)
        runCatching {
            w.open()
            // 防丢句子开头：补回触发点之前最近 ~1.5s 的历史帧
            w.writeFrames(ringBuffer.snapshot())
            w.writeFrame(triggerFrame)
        }.onFailure {
            // 落盘起步就失败（磁盘满/权限）：放弃这段，不进入录入态
            runCatching { w.close() }
            return
        }
        writer = w
        currentSegmentId = id
        currentStartTime = startTime
        ringBuffer.clear() // 历史已补进段，清掉避免下段重复
        onDiagnostic("segment_start id=$id frame=$frameCount maxProbability=$maxProbability")
        onStatus(tech.echo.app.core.model.RecordingStatus.RECORDING)
    }

    /** 收尾一段并落库。append=true 时把结束帧也写进去（正常结束），切段时为 false。 */
    private suspend fun finishSegment(lastFrame: ShortArray, append: Boolean, durationMs: Long) {
        val w = writer ?: return
        val id = currentSegmentId ?: return
        runCatching {
            if (append) w.writeFrame(lastFrame)
            w.close()
        }.onFailure {
            onDiagnostic("segment_file_close_failed id=$id error=${it.message}")
        }
        val file = w.file
        writer = null
        currentSegmentId = null
        // 入库（失败不影响继续录音）
        runCatching {
            onSegmentRecorded(
                SegmentEntity(
                    id = id,
                    date = dayFormat.format(currentStartTime),
                    startTime = currentStartTime,
                    durationMs = durationMs,
                    audioPath = file.absolutePath,
                    status = SegmentStatus.RECORDED.name,
                )
            )
            onDiagnostic("segment_recorded id=$id durationMs=$durationMs path=${file.absolutePath}")
        }.onFailure {
            onDiagnostic("segment_insert_failed id=$id error=${it.message}")
        }
        onStatus(tech.echo.app.core.model.RecordingStatus.LISTENING)
    }

    /** 段太短被判噪声：关掉并删文件，不入库。 */
    private fun discardSegment() {
        val w = writer ?: return
        runCatching { w.close() }
        runCatching { w.file.delete() }
        onDiagnostic("segment_discarded frame=$frameCount maxProbability=$maxProbability")
        writer = null
        currentSegmentId = null
        onStatus(tech.echo.app.core.model.RecordingStatus.LISTENING)
    }

    /** 外部停止/暂停时强制收尾当前段（够长则落盘，否则丢弃）。 */
    private suspend fun forceFinish() {
        if (writer == null) return
        when (val ev = stateMachine.forceEnd()) {
            is VadEvent.SegmentEnd -> finishSegment(ShortArray(0), append = false, durationMs = ev.durationMs)
            else -> discardSegment()
        }
    }

    companion object {
        private const val DIAGNOSTIC_LOG_EVERY_FRAMES = 50
    }
}
