package tech.echo.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * echo Application。
 *
 * 已启用 Hilt（[HiltAndroidApp]）。录音控制器由 Hilt 注入真实实现
 * （[tech.echo.app.core.audio.RealRecordingController]），不再手动持有 Fake。
 */
@HiltAndroidApp
class EchoApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
