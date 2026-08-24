package tech.echo.app.ui.detail

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tech.echo.app.core.model.DailySummary
import tech.echo.app.core.model.TimelineEntry
import tech.echo.app.ui.theme.EchoSpacing

@Composable
fun SummaryTab(
    summary: DailySummary,
    isRegenerating: Boolean,
    onRegenerateSummary: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = EchoSpacing.pageHorizontal),
    ) {
        Spacer(Modifier.height(38.dp))
        Section(
            title = "日記",
            showDivider = summary.todos.isNotEmpty() || summary.inspirations.isNotEmpty() || summary.timeline.isNotEmpty(),
            trailing = { RegenerateSummaryAction(isRegenerating, onRegenerateSummary) },
        ) {
            Text(
                summary.diary,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Normal,
                    fontSize = 14.sp,
                    lineHeight = 23.sp,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (summary.todos.isNotEmpty()) {
            Section(title = "待辦", showDivider = summary.inspirations.isNotEmpty() || summary.timeline.isNotEmpty()) {
                summary.todos.forEach { TodoLine(it) }
            }
        }
        if (summary.inspirations.isNotEmpty()) {
            Section(title = "靈感", showDivider = summary.timeline.isNotEmpty()) {
                summary.inspirations.forEach { InspirationLine(it) }
            }
        }
        if (summary.timeline.isNotEmpty()) {
            Section(title = "時間線", showDivider = false) {
                summary.timeline.forEachIndexed { index, entry ->
                    TimelineRow(entry, index == 0, index == summary.timeline.lastIndex)
                }
            }
        }
        Spacer(Modifier.height(48.dp))
    }
}

@Composable
private fun Section(
    title: String,
    showDivider: Boolean,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp,
                lineHeight = 26.sp,
            ),
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.weight(1f))
        trailing?.invoke()
    }
    Spacer(Modifier.height(26.dp))
    content()
    if (showDivider) {
        Spacer(Modifier.height(38.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(38.dp))
    }
}

@Composable
private fun RegenerateSummaryAction(loading: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.height(36.dp).clickable(enabled = !loading, onClick = onClick).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Icon(
                imageVector = Icons.Filled.Refresh,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(4.dp))
        Text(
            if (loading) "整理中" else "重新整理",
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp, lineHeight = 16.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TodoLine(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(modifier = Modifier.size(28.dp)) {
            drawCircle(
                color = androidx.compose.ui.graphics.Color(0xFF6F6F6F),
                radius = size.minDimension / 2f - 2.dp.toPx(),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()),
                center = center,
            )
        }
        Spacer(Modifier.width(26.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 22.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun InspirationLine(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.headlineSmall.copy(
            fontWeight = FontWeight.Normal,
            fontSize = 15.sp,
            lineHeight = 24.sp,
        ),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 12.dp),
    )
}

@Composable
private fun TimelineRow(entry: TimelineEntry, first: Boolean, last: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().height(46.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(modifier = Modifier.size(width = 26.dp, height = 46.dp)) {
            val x = size.width / 2f
            if (!first) {
                drawLine(
                    color = androidx.compose.ui.graphics.Color(0xFF6F6F6F),
                    start = Offset(x, 0f),
                    end = Offset(x, size.height / 2f),
                    strokeWidth = 1.4.dp.toPx(),
                )
            }
            if (!last) {
                drawLine(
                    color = androidx.compose.ui.graphics.Color(0xFF6F6F6F),
                    start = Offset(x, size.height / 2f),
                    end = Offset(x, size.height),
                    strokeWidth = 1.4.dp.toPx(),
                )
            }
            drawCircle(
                color = androidx.compose.ui.graphics.Color(0xFF1A1A1A),
                radius = 5.dp.toPx(),
                center = Offset(x, size.height / 2f),
            )
        }
        Spacer(Modifier.width(24.dp))
        Text(
            entry.time,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 20.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(82.dp),
        )
        Text(
            listOf(entry.person, entry.topic).filter { it.isNotBlank() }.joinToString("  "),
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 20.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
