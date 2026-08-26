package tech.echo.app.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import tech.echo.app.core.data.repository.DailySummaryRepository
import tech.echo.app.core.data.repository.SegmentRepository
import tech.echo.app.core.model.DailySummary
import tech.echo.app.core.model.SummaryStatus
import tech.echo.app.core.model.TranscriptSegment
import tech.echo.app.core.summary.SummaryGenerator
import javax.inject.Inject

data class DetailUiState(
    val summary: DailySummary,
    val segments: List<TranscriptSegment>,
    val isRegeneratingSummary: Boolean,
)

@HiltViewModel
class DetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val dailySummaryRepository: DailySummaryRepository,
    private val segmentRepository: SegmentRepository,
    private val summaryGenerator: SummaryGenerator,
) : ViewModel() {
    private val date: String = checkNotNull(savedStateHandle["date"]) {
        "detail route requires date argument"
    }
    private val localRegenerating = MutableStateFlow(false)

    val state: StateFlow<DetailUiState> = combine(
        dailySummaryRepository.observeByDate(date),
        segmentRepository.observeByDate(date),
        localRegenerating,
    ) { summary, segments, localLoading ->
        val uiSummary = DetailMappers.toDailySummary(summary, date)
        DetailUiState(
            summary = uiSummary,
            segments = DetailMappers.toTranscriptSegments(segments),
            isRegeneratingSummary = localLoading || uiSummary.summaryStatus == SummaryStatus.GENERATING,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DetailUiState(
            summary = DetailMappers.toDailySummary(null, date),
            segments = emptyList(),
            isRegeneratingSummary = false,
        ),
    )

    fun claimSpeaker(segmentId: String, speakerKey: String?, personName: String) {
        viewModelScope.launch {
            segmentRepository.claimSpeaker(
                date = date,
                segmentId = segmentId,
                speakerLabel = speakerKey,
                personName = personName,
            )
        }
    }

    /**
     * 手動整理直接執行 SummaryGenerator，不再把 UI 狀態先寫成 GENERATING
     * 再等待 WorkManager。Generator 自己負責 PENDING / GENERATING / DONE / FAILED，
     * 因此即使目前還沒有完成的逐字稿，畫面也會正確回到可重試狀態。
     */
    fun regenerateSummary() {
        if (localRegenerating.value) return
        viewModelScope.launch {
            localRegenerating.value = true
            try {
                summaryGenerator.generate(date)
            } finally {
                localRegenerating.value = false
            }
        }
    }
}
