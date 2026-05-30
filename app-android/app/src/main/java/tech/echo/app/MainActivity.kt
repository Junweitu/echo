package tech.echo.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import tech.echo.app.core.audio.RecordingAutostartPolicy
import tech.echo.app.core.audio.RecordingService
import tech.echo.app.core.summary.SummaryWorkScheduler
import tech.echo.app.core.upload.UploadWorkScheduler
import tech.echo.app.ui.nav.EchoNavHost
import tech.echo.app.ui.onboarding.OnboardingScreen
import tech.echo.app.ui.theme.EchoTheme
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var summaryWorkScheduler: SummaryWorkScheduler
    @Inject
    lateinit var uploadWorkScheduler: UploadWorkScheduler

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        summaryWorkScheduler.scheduleDaily()
        uploadWorkScheduler.enqueueNow()
        enableEdgeToEdge()
        setContent {
            EchoTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    val context = LocalContext.current
                    val prefs = remember {
                        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    }
                    var onboarded by remember {
                        mutableStateOf(prefs.getBoolean(KEY_ONBOARDED, false))
                    }
                    LaunchedEffect(onboarded) {
                        if (RecordingAutostartPolicy.shouldStart(onboarded, hasMicPermission(context))) {
                            RecordingService.start(context)
                        }
                    }
                    if (onboarded) {
                        EchoNavHost()
                    } else {
                        OnboardingScreen(onFinish = {
                            prefs.edit().putBoolean(KEY_ONBOARDED, true).apply()
                            // 引导完成且已授麦克风权限 → 立即开始后台聆听
                            if (hasMicPermission(context)) RecordingService.start(context)
                            onboarded = true
                        })
                    }
                }
            }
        }
    }

    private fun hasMicPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    companion object {
        private const val PREFS = "echo_prefs"
        private const val KEY_ONBOARDED = "onboarded"
    }
}
