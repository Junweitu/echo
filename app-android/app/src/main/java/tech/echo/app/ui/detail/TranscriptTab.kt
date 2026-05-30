package tech.echo.app.ui.detail

import android.media.MediaPlayer
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tech.echo.app.core.model.TranscriptSegment
import tech.echo.app.ui.theme.EchoListeningBlue
import tech.echo.app.ui.theme.EchoSpacing
import tech.echo.app.ui.theme.EchoSpeakerGreen
import java.io.File
import kotlin.math.roundToInt

@Composable
fun TranscriptTab(
    segments: List<TranscriptSegment>,
    onClaimSpeaker: (segmentId: String, speakerKey: String?, personName: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var claimingSegment by remember { mutableStateOf<TranscriptSegment?>(null) }
    var readingSegment by remember { mutableStateOf<TranscriptSegment?>(null) }
    var playingSegmentId by remember { mutableStateOf<String?>(null) }
    val sections = remember(segments) { TranscriptTimeline.sections(segments) }
    val hourIndexes = remember(sections) { TranscriptTimeline.itemIndexByHour(sections) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var activeIndexHour by remember { mutableStateOf<String?>(null) }
    var hourIndexActive by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(start = EchoSpacing.pageHorizontal, end = EchoSpacing.pageHorizontal + 30.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 12.dp, bottom = 28.dp),
        ) {
            sections.forEach { section ->
                item(key = "hour-${section.hourKey}") {
                    HourHeader(section.headerLabel)
                }
                items(section.segments, key = { it.id }) { seg ->
                    SegmentRow(
                        segment = seg,
                        isPlaying = playingSegmentId == seg.id,
                        onClickSpeaker = { claimingSegment = seg },
                        onOpenTranscript = { readingSegment = seg },
                        onTogglePlayback = {
                            playingSegmentId = if (playingSegmentId == seg.id) null else seg.id
                        },
                        onPlaybackFinished = {
                            if (playingSegmentId == seg.id) playingSegmentId = null
                        },
                    )
                }
            }
        }

        if (sections.size > 1) {
            HourIndexRail(
                sections = sections,
                activeHour = activeIndexHour,
                active = hourIndexActive,
                onActiveChange = { hourIndexActive = it },
                onClickHour = { hour ->
                    activeIndexHour = hour
                    hourIndexes[hour]?.let { itemIndex ->
                        scope.launch { listState.scrollToItem(itemIndex) }
                    }
                },
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }
    }

    claimingSegment?.let { segment ->
        ClaimSpeakerDialog(
            currentName = segment.speakerLabel,
            onConfirm = { newName ->
                onClaimSpeaker(segment.id, segment.speakerKey, newName)
                claimingSegment = null
            },
            onDismiss = { claimingSegment = null },
        )
    }

    readingSegment?.let { segment ->
        TranscriptTextSheet(
            segment = segment,
            onDismiss = { readingSegment = null },
        )
    }
}

@Composable
private fun HourHeader(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.bodyMedium.copy(
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
            lineHeight = 18.sp,
        ),
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 4.dp),
    )
}

@Composable
private fun HourIndexRail(
    sections: List<TranscriptHourSection>,
    activeHour: String?,
    active: Boolean,
    onActiveChange: (Boolean) -> Unit,
    onClickHour: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    fun selectByY(y: Float, height: Int) {
        if (height <= 0) return
        val index = ((y / height) * sections.size)
            .toInt()
            .coerceIn(0, sections.lastIndex)
        onClickHour(sections[index].hourKey)
    }

    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(42.dp)
            .pointerInput(sections) {
                awaitEachGesture {
                    val height = size.height
                    val down = awaitFirstDown(requireUnconsumed = false)
                    onActiveChange(true)
                    selectByY(down.position.y, height)
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break
                        if (!change.pressed) break
                        selectByY(change.position.y, height)
                        change.consume()
                    }
                    onActiveChange(false)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(top = 42.dp, bottom = 42.dp, end = 2.dp),
            verticalArrangement = Arrangement.SpaceEvenly,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            sections.forEach { section ->
                val selected = active && activeHour == section.hourKey
                Box(
                    modifier = Modifier.size(width = 38.dp, height = 30.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = section.hourKey,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            fontSize = if (selected) 16.sp else 10.sp,
                            lineHeight = 18.sp,
                        ),
                        color = if (selected) {
                            MaterialTheme.colorScheme.onBackground
                        } else {
                            EchoListeningBlue.copy(alpha = 0.32f)
                        },
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun SegmentRow(
    segment: TranscriptSegment,
    isPlaying: Boolean,
    onClickSpeaker: () -> Unit,
    onOpenTranscript: () -> Unit,
    onTogglePlayback: () -> Unit,
    onPlaybackFinished: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                segment.time,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            Text(
                formatVoiceDuration(segment.durationMs),
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            PlayCircleButton(
                isPlaying = isPlaying,
                enabled = File(segment.audioPath).exists(),
                onClick = onTogglePlayback,
            )
            Spacer(Modifier.width(10.dp))
            SpeakerChip(
                name = segment.speakerLabel,
                onClick = onClickSpeaker,
            )
            Spacer(Modifier.width(10.dp))
            VoiceInlinePlayer(
                audioPath = segment.audioPath,
                durationMs = segment.durationMs,
                isPlaying = isPlaying,
                tint = segment.speakerTint(),
                onPlaybackFinished = onPlaybackFinished,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                segment.text,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 20.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onOpenTranscript),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            IconButton(onClick = onClickSpeaker, modifier = Modifier.size(34.dp)) {
                Icon(
                    imageVector = Icons.Filled.MoreHoriz,
                    contentDescription = "认领或编辑说话人",
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.62f))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TranscriptTextSheet(
    segment: TranscriptSegment,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.background,
        dragHandle = {
            Surface(
                modifier = Modifier
                    .padding(top = 12.dp)
                    .size(width = 46.dp, height = 5.dp),
                shape = RoundedCornerShape(100),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.55f),
                content = {},
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = EchoSpacing.pageHorizontal, vertical = 16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "完整转写",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 17.sp,
                        lineHeight = 24.sp,
                    ),
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "关闭")
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = segment.time,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(10.dp))
                SpeakerChip(
                    name = segment.speakerLabel,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = formatVoiceDuration(segment.durationMs),
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.62f))
            Spacer(Modifier.height(14.dp))
            Text(
                text = segment.text,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, lineHeight = 24.sp),
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
            )
            Spacer(Modifier.height(18.dp))
        }
    }
}

@Composable
private fun PlayCircleButton(
    isPlaying: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(24.dp)
            .border(1.dp, MaterialTheme.colorScheme.onBackground, CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            contentDescription = if (isPlaying) "暂停原始声音" else "播放原始声音",
            tint = if (enabled) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(15.dp),
        )
    }
}

@Composable
private fun SpeakerChip(
    name: String,
    onClick: (() -> Unit)? = null,
) {
    val color = speakerTint(name)
    val clickModifier = if (onClick == null) Modifier else Modifier.clickable(onClick = onClick)
    Surface(
        modifier = Modifier
            .height(24.dp)
            .then(clickModifier),
        shape = RoundedCornerShape(100),
        color = Color.Transparent,
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.38f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 10.sp, lineHeight = 14.sp),
                color = color,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun VoiceInlinePlayer(
    audioPath: String,
    durationMs: Long,
    isPlaying: Boolean,
    tint: Color,
    onPlaybackFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val audioFile = remember(audioPath) { File(audioPath) }
    val onFinished by rememberUpdatedState(onPlaybackFinished)
    var progressMs by remember(audioPath) { mutableStateOf(0) }
    var failed by remember(audioPath) { mutableStateOf(false) }
    var player by remember(audioPath) { mutableStateOf<MediaPlayer?>(null) }
    val waveform = remember(audioPath) { VoiceWaveform.heights(audioPath) }

    DisposableEffect(audioPath) {
        onDispose {
            runCatching { player?.release() }
        }
    }

    LaunchedEffect(isPlaying, audioPath) {
        if (isPlaying) {
            val activePlayer = player ?: runCatching {
                MediaPlayer().apply {
                    setDataSource(audioPath)
                    prepare()
                    setOnCompletionListener {
                        progressMs = duration
                        onFinished()
                    }
                }
            }.onFailure {
                failed = true
                onFinished()
            }.getOrNull()?.also { player = it } ?: return@LaunchedEffect

            if (!activePlayer.isPlaying) {
                if (activePlayer.currentPosition >= activePlayer.duration - 100) {
                    activePlayer.seekTo(0)
                    progressMs = 0
                }
                runCatching { activePlayer.start() }
            }
            while (isPlaying && activePlayer.isPlaying) {
                progressMs = activePlayer.currentPosition
                delay(200)
            }
        } else {
            player?.takeIf { it.isPlaying }?.let { activePlayer ->
                runCatching { activePlayer.pause() }
            }
        }
    }

    val totalMs = remember(durationMs, player) {
        player?.duration?.takeIf { it > 0 }?.toLong() ?: durationMs
    }.coerceAtLeast(1)
    val progress = if (audioFile.exists() && !failed) {
        (progressMs.toFloat() / totalMs).coerceIn(0f, 1f)
    } else {
        0f
    }

    VoiceWaveformBar(
        heights = waveform,
        progress = progress,
        playedColor = tint,
        modifier = modifier.height(32.dp),
    )
}

@Composable
private fun VoiceWaveformBar(
    heights: List<Float>,
    progress: Float,
    playedColor: Color,
    modifier: Modifier = Modifier,
) {
    val playedBars = VoiceWaveform.playedBars(progress, heights.size)
    val remainingColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.72f)
    Canvas(modifier = modifier) {
        if (heights.isEmpty()) return@Canvas
        val gap = 3.dp.toPx()
        val barWidth = ((size.width - gap * (heights.size - 1)) / heights.size)
            .coerceAtLeast(1.dp.toPx())
        heights.forEachIndexed { index, heightFraction ->
            val barHeight = (size.height * heightFraction).coerceAtLeast(barWidth)
            val x = index * (barWidth + gap)
            drawRoundRect(
                color = if (index < playedBars) playedColor else remainingColor,
                topLeft = Offset(x = x, y = (size.height - barHeight) / 2f),
                size = Size(width = barWidth, height = barHeight),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f),
            )
        }
    }
}

private fun TranscriptSegment.speakerTint(): Color = speakerTint(speakerLabel)

private fun speakerTint(name: String): Color =
    if (name.contains("B", ignoreCase = true) || name.contains("家人")) {
        EchoSpeakerGreen
    } else {
        EchoListeningBlue
    }

private fun formatVoiceDuration(ms: Long): String {
    val totalSeconds = (ms / 1000f).roundToInt().coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return minutes.toString().padStart(2, '0') + ":" + seconds.toString().padStart(2, '0')
}
