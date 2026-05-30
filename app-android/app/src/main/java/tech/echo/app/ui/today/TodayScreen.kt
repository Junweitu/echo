package tech.echo.app.ui.today

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tech.echo.app.core.model.RecordingStatus
import tech.echo.app.core.model.TodayState
import tech.echo.app.ui.theme.EchoListeningBlue
import tech.echo.app.ui.theme.EchoRecordingRed
import tech.echo.app.ui.theme.EchoSpacing

@Composable
fun TodayScreen(
    state: TodayState,
    onToggleRecording: () -> Unit,
    onOpenSummary: () -> Unit,
    onSummarizeToday: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = EchoSpacing.pageHorizontal),
        ) {
            TodayHeader(
                onOpenHistory = onOpenHistory,
                onOpenSettings = onOpenSettings,
            )

            Spacer(Modifier.weight(0.92f))

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                RecordingControl(status = state.status, onClick = onToggleRecording)

                Spacer(Modifier.height(28.dp))

                Text(
                    text = state.status.primaryText(),
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 18.sp,
                        lineHeight = 25.sp,
                    ),
                    color = state.status.primaryColor(),
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = state.status.hintText(),
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )

            }

            Spacer(Modifier.weight(0.78f))

            SummaryEntry(
                summaryReady = state.summaryReady,
                segmentCount = state.segmentCount,
                totalMinutes = state.totalMinutes,
                onClick = onOpenSummary,
                onSummarizeToday = onSummarizeToday,
            )

            Spacer(Modifier.height(44.dp))
        }
    }
}

@Composable
private fun TodayHeader(
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 30.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "回声",
            style = MaterialTheme.typography.headlineLarge.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 26.sp,
                lineHeight = 34.sp,
            ),
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.weight(1f))
        HeaderAction(
            icon = { Icon(Icons.Outlined.AccessTime, contentDescription = "查看历史") },
            contentDescription = "查看历史",
            onClick = onOpenHistory,
        )
        Spacer(Modifier.width(12.dp))
        HeaderAction(
            icon = { Icon(Icons.Outlined.Tune, contentDescription = "设置") },
            contentDescription = "设置",
            onClick = onOpenSettings,
        )
    }
}

@Composable
private fun HeaderAction(
    icon: @Composable () -> Unit,
    contentDescription: String,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(44.dp),
    ) {
        Box(
            modifier = Modifier.size(28.dp),
            contentAlignment = Alignment.Center,
        ) {
            icon()
        }
    }
}

@Composable
private fun SummaryEntry(
    summaryReady: Boolean,
    segmentCount: Int,
    totalMinutes: Int,
    onClick: () -> Unit,
    onSummarizeToday: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                if (!summaryReady) onSummarizeToday()
                onClick()
            },
        shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 3.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            EchoSummaryIcon()
            Spacer(Modifier.width(16.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = "今日回声",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 17.sp,
                        lineHeight = 23.sp,
                    ),
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = if (summaryReady) "今天的声音已整理" else "今天的声音待整理",
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "$segmentCount 段 · 约 $totalMinutes 分钟 · ${if (summaryReady) "已整理" else "待整理"}",
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(32.dp),
            )
        }
    }
}

@Composable
private fun EchoSummaryIcon() {
    Surface(
        modifier = Modifier.size(58.dp),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Canvas(modifier = Modifier.padding(14.dp)) {
            val bars = listOf(0.42f, 0.72f, 0.94f, 0.55f, 0.36f)
            val gap = 5.dp.toPx()
            val barWidth = 3.dp.toPx()
            val totalWidth = bars.size * barWidth + (bars.size - 1) * gap
            var x = (size.width - totalWidth) / 2f
            bars.forEach { fraction ->
                val h = size.height * fraction
                drawRoundRect(
                    color = Color.Black,
                    topLeft = Offset(x, (size.height - h) / 2f),
                    size = Size(barWidth, h),
                    cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f),
                )
                x += barWidth + gap
            }
        }
    }
}

private fun RecordingStatus.primaryText(): String = when (this) {
    RecordingStatus.PAUSED -> "已暂停"
    RecordingStatus.LISTENING -> "正在聆听"
    RecordingStatus.RECORDING -> "正在记录"
}

private fun RecordingStatus.hintText(): String = when (this) {
    RecordingStatus.PAUSED -> "点按开始聆听"
    RecordingStatus.LISTENING -> "只捕捉有内容的声音"
    RecordingStatus.RECORDING -> "检测到说话"
}

private fun RecordingStatus.primaryColor() = when (this) {
    RecordingStatus.PAUSED -> androidx.compose.ui.graphics.Color.Unspecified
    RecordingStatus.LISTENING -> EchoListeningBlue
    RecordingStatus.RECORDING -> EchoRecordingRed
}
