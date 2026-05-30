package tech.echo.app.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import tech.echo.app.core.data.db.DailySummaryEntity
import tech.echo.app.core.data.db.SummaryStatusDb
import tech.echo.app.core.data.repository.DailySummaryRepository
import tech.echo.app.core.data.repository.SegmentRepository
import tech.echo.app.core.model.DailySummary
import tech.echo.app.core.model.SummaryStatus
import tech.echo.app.core.model.TranscriptSegment
import tech.echo.app.core.summary.SummaryWorkScheduler
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
    private val summaryWorkScheduler: SummaryWorkScheduler,
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

    fun regenerateSummary() {
        viewModelScope.launch {
            localRegenerating.value = true
            val existing = dailySummaryRepository.getByDate(date) ?: DailySummaryEntity(date = date)
            dailySummaryRepository.upsert(existing.copy(status = SummaryStatusDb.GENERATING.name))
            summaryWorkScheduler.enqueue(date)
            delay(1_500)
            localRegenerating.value = false
        }
    }
}
