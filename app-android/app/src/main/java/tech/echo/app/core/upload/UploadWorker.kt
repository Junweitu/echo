package tech.echo.app.core.upload

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import tech.echo.app.core.settings.AppConfigProvider
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@HiltWorker
class UploadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val configProvider: AppConfigProvider,
    private val processor: UploadProcessor,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        if (!configProvider.current().isAsrConfigured) {
            Log.i(TAG, "ASR not configured; skip upload")
            return Result.success()
        }
        val result = processor.processPending()
        Log.i(TAG, "upload batch total=${result.total} completed=${result.completed} failed=${result.failed}")
        if (result.failed > 0) {
            Log.i(TAG, "upload failures stay in FAILED and will be retried by the next enqueue")
        }
        return Result.success()
    }

    companion object {
        private const val TAG = "EchoUploadWorker"
        private const val UNIQUE_NAME = "upload-pending-segments"

        fun request(): OneTimeWorkRequest =
            OneTimeWorkRequestBuilder<UploadWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()

        fun uniqueName(): String = UNIQUE_NAME
    }
}

@Singleton
class UploadWorkScheduler @Inject constructor(
    private val workManager: WorkManager,
) {
    fun enqueue() {
        enqueue(ExistingWorkPolicy.KEEP)
    }

    fun enqueueNow() {
        enqueue(ExistingWorkPolicy.REPLACE)
    }

    private fun enqueue(policy: ExistingWorkPolicy) {
        workManager.enqueueUniqueWork(
            UploadWorker.uniqueName(),
            policy,
            UploadWorker.request(),
        )
    }
}
