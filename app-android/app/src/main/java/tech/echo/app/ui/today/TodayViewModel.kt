package tech.echo.app.ui.today

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import tech.echo.app.core.audio.RecordingController
import tech.echo.app.core.model.TodayState
import tech.echo.app.core.summary.SummaryWorkScheduler
import tech.echo.app.core.time.EchoDateFormatter
import javax.inject.Inject

/**
 * 今天主页 ViewModel：把 RecordingController 的状态透传给 UI，并转发圆形主控点击。
 * Hilt 注入真实 [RecordingController]（RealRecordingController）。
 */
@HiltViewModel
class TodayViewModel @Inject constructor(
    private val controller: RecordingController,
    private val summaryWorkScheduler: SummaryWorkScheduler,
) : ViewModel() {

    val state: StateFlow<TodayState> = controller.state
    val todayDate: String = EchoDateFormatter.todayKey()
    val todayDisplay: String = EchoDateFormatter.displayDate(todayDate)

    /** 圆形主控点击：在聆听/暂停间切换。 */
    fun onToggle() = controller.toggle()

    fun onSummarizeToday() = summaryWorkScheduler.enqueue(todayDate)
}
