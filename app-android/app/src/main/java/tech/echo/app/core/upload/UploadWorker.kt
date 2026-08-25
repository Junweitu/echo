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
        val result = processor.processPending()
        Log.i(TAG, "local ASR batch total=${result.total} completed=${result.completed} failed=${result.failed}")
        if (result.failed > 0) {
            Log.i(TAG, "local ASR failures stay in FAILED and will be retried by the next queued worker")
        }
        return Result.success()
    }

    companion object {
        private const val TAG = "EchoUploadWorker"
        private const val UNIQUE_NAME = "upload-pending-segments"

        fun request(): OneTimeWorkRequest =
            OneTimeWorkRequestBuilder<UploadWorker>()
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()

        fun uniqueName(): String = UNIQUE_NAME
    }
}

@Singleton
class UploadWorkScheduler @Inject constructor(
    private val workManager: WorkManager,
) {
    /**
     * Local ASR is CPU-heavy and must be serialized.  Never REPLACE an active worker:
     * cancelling it after the DB row was marked UPLOADING leaves the UI stuck on
     * "正在轉寫".  APPEND_OR_REPLACE keeps one ordered chain and recovers if an old
     * chain had already been cancelled or failed.
     */
    fun enqueue() {
        enqueue(ExistingWorkPolicy.APPEND_OR_REPLACE)
    }

    fun enqueueNow() {
        enqueue(ExistingWorkPolicy.APPEND_OR_REPLACE)
    }

    private fun enqueue(policy: ExistingWorkPolicy) {
        workManager.enqueueUniqueWork(
            UploadWorker.uniqueName(),
            policy,
            UploadWorker.request(),
        )
    }
}
