package tech.echo.app.core.upload

import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

data class AsrDiagnostic(
    val engine: String,
    val elapsedMs: Long,
    val fallbackReason: String? = null,
)

/** 暫存一次 ASR 呼叫的來源與耗時，讓 UploadProcessor 寫回 segment 資料庫。 */
@Singleton
class AsrDiagnosticsStore @Inject constructor() {
    private val byAudioPath = ConcurrentHashMap<String, AsrDiagnostic>()

    fun record(audioPath: String, diagnostic: AsrDiagnostic) {
        byAudioPath[audioPath] = diagnostic
    }

    fun consume(audioPath: String): AsrDiagnostic? = byAudioPath.remove(audioPath)
}
