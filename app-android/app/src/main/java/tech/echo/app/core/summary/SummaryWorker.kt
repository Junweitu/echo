package tech.echo.app.core.summary

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import tech.echo.app.core.time.EchoDateFormatter
import java.time.Clock
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@HiltWorker
class SummaryWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val generator: SummaryGenerator,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val date = inputData.getString(KEY_DATE) ?: EchoDateFormatter.yesterdayKey()
        generator.generate(date)
        return Result.success()
    }

    companion object {
        const val KEY_DATE = "date"
        private const val UNIQUE_PREFIX = "summary-"
        private const val UNIQUE_DAILY = "summary-daily"

        fun oneTimeRequest(date: String): OneTimeWorkRequest =
            OneTimeWorkRequestBuilder<SummaryWorker>()
                .setInputData(workDataOf(KEY_DATE to date))
                .setConstraints(networkConstraints())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()

        fun periodicRequest(clock: Clock = Clock.systemDefaultZone()): PeriodicWorkRequest {
            val now = LocalDateTime.now(clock)
            val nextRun = now.toLocalDate()
                .plusDays(if (now.toLocalTime().isBefore(LocalTime.of(3, 0))) 0 else 1)
                .atTime(3, 0)
            return PeriodicWorkRequestBuilder<SummaryWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(Duration.between(now, nextRun).toMillis(), TimeUnit.MILLISECONDS)
                .setConstraints(networkConstraints())
                .build()
        }

        fun uniqueName(date: String): String = "$UNIQUE_PREFIX$date"

        fun dailyUniqueName(): String = UNIQUE_DAILY

        private fun networkConstraints(): Constraints =
            Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
    }
}

@Singleton
class SummaryWorkScheduler @Inject constructor(
    private val workManager: WorkManager,
) {
    fun enqueue(date: String) {
        workManager.enqueueUniqueWork(
            SummaryWorker.uniqueName(date),
            ExistingWorkPolicy.REPLACE,
            SummaryWorker.oneTimeRequest(date),
        )
    }

    fun scheduleDaily() {
        workManager.enqueueUniquePeriodicWork(
            SummaryWorker.dailyUniqueName(),
            ExistingPeriodicWorkPolicy.KEEP,
            SummaryWorker.periodicRequest(),
        )
    }
}
