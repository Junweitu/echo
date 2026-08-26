package tech.echo.app.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import tech.echo.app.core.audio.RecordingController
import tech.echo.app.core.model.TodayState
import tech.echo.app.core.summary.SummaryGenerator
import tech.echo.app.core.time.EchoDateFormatter
import javax.inject.Inject

/**
 * 今天主頁 ViewModel：把 RecordingController 的狀態透傳給 UI，並轉發圓形主控點擊。
 * Hilt 注入真實 [RecordingController]（RealRecordingController）。
 */
@HiltViewModel
class TodayViewModel @Inject constructor(
    private val controller: RecordingController,
    private val summaryGenerator: SummaryGenerator,
) : ViewModel() {

    val state: StateFlow<TodayState> = controller.state
    val todayDate: String = EchoDateFormatter.todayKey()
    val todayDisplay: String = EchoDateFormatter.displayDate(todayDate)

    /** 圓形主控點擊：在聆聽/暫停間切換。 */
    fun onToggle() = controller.toggle()

    /** 手動整理今天內容直接執行，不再依賴 WorkManager 才開始。 */
    fun onSummarizeToday() {
        viewModelScope.launch {
            summaryGenerator.generate(todayDate)
        }
    }
}
