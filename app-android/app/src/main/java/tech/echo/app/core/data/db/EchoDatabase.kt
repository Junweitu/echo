package tech.echo.app.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * echo 本地数据库。
 *
 * v1：仅 segment 表（阶段 1）。
 * v2：新增 daily_summary 表（阶段 2 每日整理）；segment 表字段不变（阶段 1 已预留
 *     transcriptText/speakerLabel/status，阶段 2 直接复用，无需改表）。
 * person 表（声纹）留待阶段 3。
 */
@Database(
    entities = [SegmentEntity::class, DailySummaryEntity::class],
    version = 2,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class EchoDatabase : RoomDatabase() {
    abstract fun segmentDao(): SegmentDao
    abstract fun dailySummaryDao(): DailySummaryDao

    companion object {
        const val NAME = "echo.db"

        /**
         * v1→v2：仅新增 daily_summary 表。segment 表无结构变化。
         * 字段类型与 [DailySummaryEntity] 严格对齐：列表/timeline 经 Converters 存为 TEXT。
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `daily_summary` (
                        `date` TEXT NOT NULL,
                        `diary` TEXT NOT NULL,
                        `todos` TEXT NOT NULL,
                        `inspirations` TEXT NOT NULL,
                        `timeline` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `generatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`date`)
                    )
                    """.trimIndent()
                )
            }
        }
    }
}
