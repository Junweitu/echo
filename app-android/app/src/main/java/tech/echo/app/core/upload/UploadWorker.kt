package tech.echo.app.core.upload

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@HiltWorker
class UploadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val processor: UploadProcessor,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        var total = 0
        var completed = 0
        var failed = 0
        var rounds = 0

        while (rounds < MAX_DRAIN_ROUNDS) {
            val result = processor.processPending()
            total += result.total
            completed += result.completed
            failed += result.failed
            rounds += 1

            if (result.total == 0) break
            if (result.completed == 0) break
        }

        Log.i(
            TAG,
            "local ASR drain rounds=$rounds total=$total completed=$completed failed=$failed",
        )
        return Result.success()
    }

    companion object {
        private const val TAG = "EchoUploadWorker"
        private const val UNIQUE_NAME = "local-asr-pending-segments-v2"
        private const val LEGACY_UNIQUE_NAME = "upload-pending-segments"
        private const val MAX_DRAIN_ROUNDS = 50

        fun request(): OneTimeWorkRequest =
            OneTimeWorkRequestBuilder<UploadWorker>()
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()

        fun uniqueName(): String = UNIQUE_NAME
        fun legacyUniqueName(): String = LEGACY_UNIQUE_NAME
    }
}

@Singleton
class UploadWorkScheduler @Inject constructor(
    private val workManager: WorkManager,
) {
    fun enqueue() {
        workManager.cancelUniqueWork(UploadWorker.legacyUniqueName())
        workManager.enqueueUniqueWork(
            UploadWorker.uniqueName(),
            ExistingWorkPolicy.KEEP,
            UploadWorker.request(),
        )
    }

    fun enqueueNow() = enqueue()

    /**
     * 0.6.2 起即時 ASR 改由 RecordingService 直接執行。
     * 更新後先取消舊版殘留的 unique work，避免舊 Worker 與直接 ASR 佇列同時處理同一筆資料。
     */
    fun cancelScheduledAsrWork() {
        workManager.cancelUniqueWork(UploadWorker.legacyUniqueName())
        workManager.cancelUniqueWork(UploadWorker.uniqueName())
    }
}
