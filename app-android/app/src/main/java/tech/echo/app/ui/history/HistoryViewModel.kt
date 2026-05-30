package tech.echo.app.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import tech.echo.app.core.data.repository.DailySummaryRepository
import tech.echo.app.core.data.repository.SegmentRepository
import tech.echo.app.core.model.DaySummaryItem
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel @Inject constructor(
    segmentRepository: SegmentRepository,
    dailySummaryRepository: DailySummaryRepository,
) : ViewModel() {

    val days: StateFlow<List<DaySummaryItem>> = combine(
        segmentRepository.observeDailyCounts(),
        dailySummaryRepository.observeAllStatus(),
    ) { counts, statuses ->
        HistoryDayMapper.map(counts, statuses)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )
}
