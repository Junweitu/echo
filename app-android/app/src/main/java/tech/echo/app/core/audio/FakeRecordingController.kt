package tech.echo.app.core.audio

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import tech.echo.app.core.model.RecordingStatus
import tech.echo.app.core.model.TodayState

/**
 * 假录音控制器：仅供 UI 阶段独立运行和预览。
 * 不真正录音，只在 PAUSED / LISTENING 间切换，并给一组假的当天计数。
 * Codex 实现真实版本后，在 DI 处替换为真实 RecordingController。
 */
class FakeRecordingController : RecordingController {

    private val _state = MutableStateFlow(
        TodayState(
            status = RecordingStatus.LISTENING,
            segmentCount = 8,
            totalMinutes = 23,
            summaryReady = false,
        )
    )
    override val state: StateFlow<TodayState> = _state.asStateFlow()

    override fun start() {
        _state.value = _state.value.copy(status = RecordingStatus.LISTENING)
    }

    override fun pause() {
        _state.value = _state.value.copy(status = RecordingStatus.PAUSED)
    }

    override fun toggle() {
        val next = if (_state.value.status == RecordingStatus.PAUSED) {
            RecordingStatus.LISTENING
        } else {
            RecordingStatus.PAUSED
        }
        _state.value = _state.value.copy(status = next)
    }
}
