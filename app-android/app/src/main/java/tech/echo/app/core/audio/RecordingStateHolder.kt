package tech.echo.app.core.audio

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import tech.echo.app.core.model.RecordingStatus
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 录音引擎状态桥（Service 与 UI 之间的单例共享点）。
 *
 * 为什么需要它：前台 [RecordingService] 才是真正持有麦克风、跑 VAD 的地方，
 * 但 UI 层（[RealRecordingController] / ViewModel）不能直接拿 Service 实例。
 * 于是用这个 @Singleton 状态桥——Service 写入引擎状态，Controller 读出来合成 UI 状态。
 *
 * 只放"引擎自身状态"（聆听/记录/暂停），当天段数/时长由 Controller 从 Room 另行合并。
 */
@Singleton
class RecordingStateHolder @Inject constructor() {

    private val _status = MutableStateFlow(RecordingStatus.PAUSED)

    /** 当前引擎状态：PAUSED（未录）/ LISTENING（待命）/ RECORDING（检测到人声）。 */
    val status: StateFlow<RecordingStatus> = _status.asStateFlow()

    /** Service 调用：更新引擎状态。 */
    fun update(status: RecordingStatus) {
        _status.value = status
    }
}
