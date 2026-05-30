package tech.echo.app.ui.today

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import tech.echo.app.core.model.RecordingStatus
import tech.echo.app.ui.theme.EchoListeningBlue
import tech.echo.app.ui.theme.EchoOutline
import tech.echo.app.ui.theme.EchoRecordingRed
import tech.echo.app.ui.theme.EchoSurfaceGray
import tech.echo.app.ui.theme.EchoWhite

/**
 * 今天页圆形主控：暂停静止、聆听蓝色细线、记录红色实心。
 * 声波和呼吸只做轻反馈，避免把日记工具做成报警器。
 */
@Composable
fun RecordingControl(
    status: RecordingStatus,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isRecording = status == RecordingStatus.RECORDING
    val isListening = status == RecordingStatus.LISTENING
    val active = isRecording || isListening
    val activeColor = if (isRecording) EchoRecordingRed else EchoListeningBlue

    val transition = rememberInfiniteTransition(label = "recording-control")
    val wavePhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isRecording) 1200 else 2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "wavePhase",
    )
    val coreScale by transition.animateFloat(
        initialValue = if (active) 0.985f else 1f,
        targetValue = if (active) 1.015f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isRecording) 1200 else 2500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "coreScale",
    )

    val coreColor by animateColorAsState(
        targetValue = when (status) {
            RecordingStatus.PAUSED -> EchoSurfaceGray.copy(alpha = 0.52f)
            RecordingStatus.LISTENING -> EchoWhite
            RecordingStatus.RECORDING -> EchoRecordingRed
        },
        animationSpec = tween(260),
        label = "coreColor",
    )

    val desc = when (status) {
        RecordingStatus.PAUSED -> "开始聆听"
        else -> "暂停聆听"
    }
    val interaction = remember { MutableInteractionSource() }
    val controlSize = 320.dp
    val coreSize = if (isRecording) 154.dp else 150.dp

    Box(
        modifier = modifier.size(controlSize),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(controlSize)) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val coreRadius = coreSize.toPx() / 2f
            if (active) {
                val maxRadius = coreRadius + 18.dp.toPx()
                val ringAlpha = if (isRecording) 0.09f else 0.13f
                drawCircle(
                    color = activeColor,
                    radius = coreRadius + (maxRadius - coreRadius) * wavePhase,
                    alpha = ringAlpha * (1f - wavePhase),
                    style = Stroke(width = 2.dp.toPx()),
                )
                drawAmbientWave(
                    color = activeColor,
                    phase = wavePhase,
                    coreRadius = coreRadius,
                    recording = isRecording,
                )
            } else {
                drawCircle(
                    color = EchoOutline,
                    radius = coreRadius + 5.dp.toPx(),
                    alpha = 0.72f,
                    style = Stroke(width = 1.dp.toPx()),
                    center = center,
                )
            }
        }

        Box(
            modifier = Modifier
                .size(coreSize)
                .scale(if (active) coreScale else 1f)
                .clip(CircleShape)
                .background(coreColor, CircleShape)
                .border(
                    width = if (isListening) 1.4.dp else 0.dp,
                    color = if (isListening) EchoListeningBlue else Color.Transparent,
                    shape = CircleShape,
                )
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    onClick = onClick,
                )
                .semantics { contentDescription = desc },
            contentAlignment = Alignment.Center,
        ) {
            val icon = if (status == RecordingStatus.RECORDING) Icons.Filled.Pause else Icons.Filled.Mic
            val iconTint = if (isRecording) EchoWhite else MaterialTheme.colorScheme.onBackground
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(if (isRecording) 46.dp else 44.dp),
            )
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawAmbientWave(
    color: Color,
    phase: Float,
    coreRadius: Float,
    recording: Boolean,
) {
    val centerY = size.height / 2f
    val centerX = size.width / 2f
    val startPadding = 6.dp.toPx()
    val endPadding = 6.dp.toPx()
    val barGap = 7.dp.toPx()
    val strokeWidth = if (recording) 2.dp.toPx() else 1.7.dp.toPx()
    val alpha = if (recording) 0.78f else 0.68f
    val minHeight = 2.dp.toPx()
    val maxHeight = if (recording) 18.dp.toPx() else 24.dp.toPx()

    fun drawSide(sign: Int) {
        var x = centerX + sign * (coreRadius + 26.dp.toPx())
        val limit = if (sign < 0) startPadding else size.width - endPadding
        var index = 0
        while ((sign < 0 && x > limit) || (sign > 0 && x < limit)) {
            val t = ((index * 0.37f + phase) % 1f)
            val shape = kotlin.math.sin((t * Math.PI * 2f)).toFloat().let { kotlin.math.abs(it) }
            val distanceFade = (1f - (index / 18f)).coerceIn(0.15f, 1f)
            val barHeight = minHeight + (maxHeight - minHeight) * shape * distanceFade
            drawLine(
                color = color.copy(alpha = alpha * distanceFade),
                start = Offset(x, centerY - barHeight / 2f),
                end = Offset(x, centerY + barHeight / 2f),
                strokeWidth = strokeWidth,
            )
            x += sign * barGap
            index += 1
        }
    }

    drawSide(-1)
    drawSide(1)
}
