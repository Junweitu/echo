package tech.echo.app.core.audio

import kotlinx.coroutines.flow.StateFlow
import tech.echo.app.core.model.TodayState

/**
 * 录音控制接口（UI 与 core-audio 的契约，见 codex-core-audio.md §6.2）。
 *
 * UI 层（今天主页 ViewModel）只依赖这个接口驱动圆形主控和"已记录 N 段"。
 * Codex 实现真实的前台 Service + VAD 版本，替换 [FakeRecordingController]。
 */
interface RecordingController {
    /** 当前录音状态 + 当天计数快照。 */
    val state: StateFlow<TodayState>

    /** 开始/恢复聆听。 */
    fun start()

    /** 暂停聆听（不再落盘）。 */
    fun pause()

    /** 在聆听/暂停之间切换（供圆形主控点击调用）。 */
    fun toggle()
}
