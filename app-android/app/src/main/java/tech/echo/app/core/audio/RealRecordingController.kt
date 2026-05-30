package tech.echo.app.core.audio

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import tech.echo.app.core.data.db.SummaryStatusDb
import tech.echo.app.core.data.repository.DailySummaryRepository
import tech.echo.app.core.data.repository.SegmentRepository
import tech.echo.app.core.model.RecordingStatus
import tech.echo.app.core.model.TodayState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 真实录音控制器（替换 FakeRecordingController，codex-core-audio.md §6.2）。
 *
 * - 状态来源：引擎实时状态（[RecordingStateHolder]）+ 当天 Room 统计（段数/时长）合并。
 * - 控制：start/pause/toggle 转发给前台 [RecordingService]，由 Service 真正持麦克风。
 *
 * UI 层（TodayViewModel）只依赖 [RecordingController] 接口，对实现无感。
 */
@Singleton
class RealRecordingController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val stateHolder: RecordingStateHolder,
    repository: SegmentRepository,
    dailySummaryRepository: DailySummaryRepository,
) : RecordingController {

    private val scope = CoroutineScope(SupervisorJob())

    /** 当天 yyyyMMdd（取一次即可，跨天问题阶段 1 不处理，重启自然刷新）。 */
    private val today: String =
        SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())

    override val state: StateFlow<TodayState> = combine(
        stateHolder.status,
        repository.observeCount(today),
        repository.observeTotalDuration(today),
        dailySummaryRepository.observeByDate(today),
    ) { status, count, durationMs, summary ->
        TodayState(
            status = status,
            segmentCount = count,
            totalMinutes = (durationMs / 60_000).toInt(),
            summaryReady = summary?.status == SummaryStatusDb.DONE.name,
        )
    }.stateIn(
        scope = scope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = TodayState(status = RecordingStatus.PAUSED),
    )

    override fun start() {
        RecordingService.start(context)
    }

    override fun pause() {
        RecordingService.pause(context)
    }

    override fun toggle() {
        if (stateHolder.status.value == RecordingStatus.PAUSED) {
            RecordingService.start(context)
        } else {
            RecordingService.pause(context)
        }
    }
}
