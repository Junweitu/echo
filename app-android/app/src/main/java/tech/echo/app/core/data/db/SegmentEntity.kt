package tech.echo.app.core.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 语音片段实体（见 design.md §5.2 / codex-core-audio.md §2.7）。
 *
 * 这是整条流水线的中心表，status 字段驱动后续阶段（上传/转写/整理）。
 * 阶段 1 只用到 recorded 状态，speakerLabel/transcriptText 等留空，给阶段 2/3 预留。
 */
@Entity(
    tableName = "segment",
    indices = [Index("date")], // 历史列表按日期分组查询，加索引
)
data class SegmentEntity(
    @PrimaryKey val id: String,         // UUID
    val date: String,                   // yyyyMMdd（落盘当天，便于按天聚合）
    val startTime: Long,                // 段开始的 epoch 毫秒
    val durationMs: Long,               // 段时长（含环形缓冲补的开头）
    val audioPath: String,              // 音频文件绝对路径
    val speakerLabel: String? = null,   // A/B/C，阶段 2 转写后填
    val speakerPersonId: String? = null,// 声纹命中的真人 id，阶段 3 填
    val transcriptText: String? = null, // 转写文本，阶段 2 填
    val status: String = SegmentStatus.RECORDED.name, // 见 SegmentStatus
)
