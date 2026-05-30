package tech.echo.app.core.upload

/** 把 ASR utterance 合并成当前 segment 表能承载的一段转写文本。 */
object TranscriptionFormatter {

    fun combineText(utterances: List<AsrUtterance>): String {
        if (utterances.size == 1) return utterances.single().text.trim()
        return utterances.joinToString("\n") { utterance ->
            val text = utterance.text.trim()
            val label = utterance.speakerLabel?.takeIf { it.isNotBlank() }
            if (label == null) text else "$label: $text"
        }.trim()
    }

    fun primarySpeakerLabel(utterances: List<AsrUtterance>): String? =
        utterances.mapNotNull { it.speakerLabel?.takeIf(String::isNotBlank) }
            .distinct()
            .singleOrNull()
}
