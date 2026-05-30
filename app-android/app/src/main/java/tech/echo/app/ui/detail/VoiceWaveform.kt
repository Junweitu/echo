package tech.echo.app.ui.detail

import kotlin.math.roundToInt
import kotlin.random.Random

object VoiceWaveform {
    const val DEFAULT_BAR_COUNT = 42

    fun heights(seed: String, count: Int = DEFAULT_BAR_COUNT): List<Float> {
        val random = Random(seed.hashCode())
        return List(count) { index ->
            val base = random.nextFloat()
            val envelope = when {
                index < count * 0.15f -> 0.65f
                index > count * 0.85f -> 0.7f
                else -> 1f
            }
            (0.25f + base * 0.75f * envelope).coerceIn(0.25f, 1f)
        }
    }

    fun playedBars(progress: Float, count: Int): Int =
        (progress.coerceIn(0f, 1f) * count).roundToInt().coerceIn(0, count)
}
