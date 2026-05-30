package tech.echo.app.core.summary

import org.junit.Assert.assertEquals
import org.junit.Test

class SummaryJsonParserTest {

    @Test
    fun parsesStructuredSummaryJson() {
        val parsed = SummaryJsonParser.parse(
            """
            {
              "diary": "上午讨论周末安排。",
              "todos": ["订机票"],
              "inspirations": ["做语音整理 App"],
              "timeline": [
                {"time": "09:20", "person": "我", "topic": "周末安排"}
              ]
            }
            """.trimIndent(),
        )

        assertEquals("上午讨论周末安排。", parsed.diary)
        assertEquals("订机票", parsed.todos.single())
        assertEquals("做语音整理 App", parsed.inspirations.single())
        assertEquals("09:20", parsed.timeline.single().time)
    }

    @Test
    fun stripsJsonMarkdownFence() {
        val parsed = SummaryJsonParser.parse(
            """
            ```json
            {"diary":"日记","todos":[],"inspirations":[],"timeline":[]}
            ```
            """.trimIndent(),
        )

        assertEquals("日记", parsed.diary)
    }
}
