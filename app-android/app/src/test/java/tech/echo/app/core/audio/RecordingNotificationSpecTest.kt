package tech.echo.app.core.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tech.echo.app.core.model.RecordingStatus

class RecordingNotificationSpecTest {

    @Test
    fun `recording state is distinct and promotable`() {
        val spec = RecordingNotificationSpec.from(RecordingStatus.RECORDING)

        assertEquals("Echo 录制中", spec.title)
        assertEquals("检测到人声，正在保存片段", spec.text)
        assertEquals("录制", spec.shortCriticalText)
        assertEquals(RecordingNotificationAction.PAUSE, spec.action)
        assertTrue(spec.requestPromotedOngoing)
        assertFalse(spec.colorized)
        assertEquals(RecordingNotificationSpec.COLOR_RECORDING, spec.color)
        assertEquals(RecordingNotificationSpec.COLOR_RECORDING, spec.livePulseColor)
        assertEquals(RecordingLiveIndicator.BREATHING_DOT, spec.liveIndicator)
        assertTrue(spec.showChronometer)
        assertTrue(spec.progressIndeterminate)
        assertTrue(spec.shortCriticalText.length <= RecordingNotificationSpec.MAX_SHORT_CRITICAL_TEXT_LENGTH)
    }

    @Test
    fun `listening and paused states use short live update chips`() {
        val listening = RecordingNotificationSpec.from(RecordingStatus.LISTENING)
        val paused = RecordingNotificationSpec.from(RecordingStatus.PAUSED)

        assertEquals("Echo 聆听中", listening.title)
        assertEquals("等待人声，后台运行中", listening.text)
        assertEquals("聆听", listening.shortCriticalText)
        assertEquals(RecordingNotificationAction.PAUSE, listening.action)
        assertTrue(listening.requestPromotedOngoing)
        assertFalse(listening.colorized)
        assertEquals(RecordingNotificationSpec.COLOR_LISTENING, listening.color)
        assertEquals(RecordingNotificationSpec.COLOR_LISTENING, listening.livePulseColor)
        assertEquals(RecordingLiveIndicator.BREATHING_DOT, listening.liveIndicator)
        assertTrue(listening.showChronometer)
        assertTrue(listening.progressIndeterminate)

        assertEquals("Echo 已暂停", paused.title)
        assertEquals("麦克风已关闭", paused.text)
        assertEquals("暂停", paused.shortCriticalText)
        assertEquals(RecordingNotificationAction.RESUME, paused.action)
        assertTrue(paused.requestPromotedOngoing)
        assertFalse(paused.colorized)
        assertEquals(RecordingNotificationSpec.COLOR_PAUSED, paused.color)
        assertEquals(null, paused.livePulseColor)
        assertEquals(RecordingLiveIndicator.MICROPHONE, paused.liveIndicator)
        assertTrue(!paused.showChronometer)
        assertTrue(!paused.progressIndeterminate)

        listOf(listening, paused).forEach {
            assertTrue(it.shortCriticalText.length <= RecordingNotificationSpec.MAX_SHORT_CRITICAL_TEXT_LENGTH)
        }
    }
}
