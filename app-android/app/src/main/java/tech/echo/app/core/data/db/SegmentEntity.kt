package tech.echo.app.core.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** 語音片段實體。 */
@Entity(
    tableName = "segment",
    indices = [Index("date")],
)
data class SegmentEntity(
    @PrimaryKey val id: String,
    val date: String,
    val startTime: Long,
    val durationMs: Long,
    val audioPath: String,
    val speakerLabel: String? = null,
    val speakerPersonId: String? = null,
    val transcriptText: String? = null,
    val status: String = SegmentStatus.RECORDED.name,
    /** 實際完成此片段轉寫的 ASR，例如 Samsung/Bixby 或 Vosk（備援）。 */
    val asrEngine: String? = null,
    /** 從開始嘗試 ASR 到最終得到文字的總耗時。 */
    val asrElapsedMs: Long? = null,
    /** Samsung 失敗而改用 Vosk 時，記錄 Samsung 的失敗原因。 */
    val asrFallbackReason: String? = null,
)
