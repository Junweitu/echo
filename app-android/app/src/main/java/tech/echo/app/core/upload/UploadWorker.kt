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

        // One worker owns the local-ASR queue and drains it in batches. This avoids
        // building an ever-growing WorkManager prerequisite chain for all-day recording.
        while (rounds < MAX_DRAIN_ROUNDS) {
            val result = processor.processPending()
            total += result.total
            completed += result.completed
            failed += result.failed
            rounds += 1

            if (result.total == 0) break
            // When only failed rows remain, stop instead of retrying the same bad row forever.
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

        // v2 deliberately uses a new unique-work name. Older Echo versions may have left
        // a long APPEND prerequisite chain under LEGACY_UNIQUE_NAME, which can starve new
        // recordings in the RECORDED/等待語音轉寫 state.
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
    /**
     * Keep exactly one active local-ASR worker. New speech never appends another
     * prerequisite; the active worker drains the Room queue itself.
     *
     * Cancel the legacy chain on every trigger. cancelUniqueWork is idempotent, and using
     * a fresh v2 unique name lets this version recover immediately from stale 0.5.x/0.6.0
     * WorkManager state after an in-place APK update.
     */
    fun enqueue() {
        workManager.cancelUniqueWork(UploadWorker.legacyUniqueName())
        workManager.enqueueUniqueWork(
            UploadWorker.uniqueName(),
            ExistingWorkPolicy.KEEP,
            UploadWorker.request(),
        )
    }

    fun enqueueNow() = enqueue()
}
