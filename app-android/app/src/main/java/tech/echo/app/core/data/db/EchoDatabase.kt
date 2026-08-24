package tech.echo.app.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [SegmentEntity::class, DailySummaryEntity::class],
    version = 3,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class EchoDatabase : RoomDatabase() {
    abstract fun segmentDao(): SegmentDao
    abstract fun dailySummaryDao(): DailySummaryDao

    companion object {
        const val NAME = "echo.db"

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

        /** v2→v3：為每段轉寫加入 ASR 來源、耗時與備援原因。 */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `segment` ADD COLUMN `asrEngine` TEXT")
                db.execSQL("ALTER TABLE `segment` ADD COLUMN `asrElapsedMs` INTEGER")
                db.execSQL("ALTER TABLE `segment` ADD COLUMN `asrFallbackReason` TEXT")
            }
        }
    }
}
