package tech.echo.app.core.upload

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TranscriptionFormatterTest {

    @Test
    fun singleUtteranceKeepsTextAndSpeakerLabel() {
        val utterances = listOf(AsrUtterance("A", "你好", 0, 1000))

        assertEquals("你好", TranscriptionFormatter.combineText(utterances))
        assertEquals("A", TranscriptionFormatter.primarySpeakerLabel(utterances))
    }

    @Test
    fun multipleSpeakersPrefixLinesAndHaveNoPrimarySpeaker() {
        val utterances = listOf(
            AsrUtterance("A", "你好", 0, 1000),
            AsrUtterance("B", "好的", 1100, 1800),
        )

        assertEquals("A: 你好\nB: 好的", TranscriptionFormatter.combineText(utterances))
        assertNull(TranscriptionFormatter.primarySpeakerLabel(utterances))
    }
}
