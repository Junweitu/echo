package tech.echo.app.core.data.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import tech.echo.app.core.data.db.EchoDatabase
import tech.echo.app.core.data.db.SegmentDao
import tech.echo.app.core.data.db.DailySummaryDao
import javax.inject.Singleton

/**
 * 数据层 Hilt 模块：提供 Room 数据库与 DAO。
 */
@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): EchoDatabase =
        Room.databaseBuilder(context, EchoDatabase::class.java, EchoDatabase.NAME)
            .addMigrations(EchoDatabase.MIGRATION_1_2)
            .build()

    @Provides
    fun provideSegmentDao(db: EchoDatabase): SegmentDao = db.segmentDao()

    @Provides
    fun provideDailySummaryDao(db: EchoDatabase): DailySummaryDao = db.dailySummaryDao()
}
