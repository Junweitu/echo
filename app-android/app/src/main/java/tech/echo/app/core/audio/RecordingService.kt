package tech.echo.app.core.audio

import android.Manifest
import android.app.Notification
import android.app.Notification.ProgressStyle
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import tech.echo.app.MainActivity
import tech.echo.app.R
import tech.echo.app.core.data.repository.SegmentRepository
import tech.echo.app.core.model.RecordingStatus
import tech.echo.app.core.upload.UploadWorkScheduler
import java.io.File
import javax.inject.Inject

/**
 * 录音前台服务（codex-core-audio.md §2.1 / design.md §3）。
 *
 * 职责：持有麦克风、跑 [RecordingEngine]、维持常驻通知、处理暂停/继续与音频焦点。
 * - Foreground Service，类型 microphone（Android 14+ 强制）。
 * - START_STICKY：被杀后系统尽量重建，自动恢复聆听。
 * - 状态写入 [RecordingStateHolder]，UI 经 RealRecordingController 读取。
 *
 * 控制入口：通过 startService + action（START/PAUSE/RESUME/STOP），
 * 也响应通知上的「暂停/继续」Action。
 */
@AndroidEntryPoint
class RecordingService : Service() {

    @Inject lateinit var repository: SegmentRepository
    @Inject lateinit var stateHolder: RecordingStateHolder
    @Inject lateinit var uploadWorkScheduler: UploadWorkScheduler

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var captureJob: Job? = null

    private val audioCapture = AudioCapture()
    private var vad: VadDetector? = null

    private lateinit var audioManager: AudioManager
    private var focusRequest: Any? = null // AudioFocusRequest（API 26+），用 Any 避免低版本引用问题

    /** 是否处于「用户主动暂停」——音频焦点恢复时据此决定要不要续录。 */
    @Volatile private var pausedByUser = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PAUSE -> pause()
            ACTION_RESUME -> resume()
            ACTION_STOP -> {
                stopRecording()
                stopForegroundCompat()
                stopSelf()
                return START_NOT_STICKY
            }
            // 无 action（含被系统重建）或 ACTION_START：进入聆听
            else -> startRecording()
        }
        return START_STICKY
    }

    /** 启动前台 + 采集循环（已在录则忽略）。 */
    private fun startRecording() {
        Log.i(TAG, "startRecording requested active=${captureJob?.isActive == true}")
        pausedByUser = false
        startForegroundCompat(RecordingStatus.LISTENING)
        if (captureJob?.isActive == true) return

        // 没麦克风权限直接降级为暂停态，不崩
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "startRecording missing RECORD_AUDIO permission")
            stateHolder.update(RecordingStatus.PAUSED)
            updateNotification(RecordingStatus.PAUSED)
            return
        }

        if (!requestAudioFocus()) {
            // 抢不到焦点：先待命，焦点回来再续
            Log.w(TAG, "startRecording audio focus denied")
            stateHolder.update(RecordingStatus.PAUSED)
            updateNotification(RecordingStatus.PAUSED)
            return
        }

        val detector = vad ?: runCatching {
            EnergyFallbackVadDetector(SileroVadDetector(applicationContext))
        }
            .getOrElse {
                // VAD 模型加载失败：服务无法工作，停掉并提示
                Log.w(TAG, "startRecording vad init failed", it)
                stateHolder.update(RecordingStatus.PAUSED)
                updateNotification(RecordingStatus.PAUSED)
                return
            }.also { vad = it }

        val engine = RecordingEngine(
            audioDir = File(filesDir, "audio"),
            vad = detector,
            onSegmentRecorded = {
                repository.insertSegment(it)
                uploadWorkScheduler.enqueueNow()
            },
            onStatus = { status ->
                stateHolder.update(status)
                updateNotification(status)
            },
            onDiagnostic = { message -> Log.i(TAG, message) },
        )

        captureJob = serviceScope.launch {
            runCatching {
                @Suppress("MissingPermission")
                engine.run(audioCapture.frames())
            }.onFailure {
                // 采集异常（麦克风被占等）：回到暂停态，通知提示
                if (it is CancellationException) {
                    Log.i(TAG, "recording loop cancelled")
                    return@onFailure
                }
                Log.w(TAG, "recording loop failed", it)
                stateHolder.update(RecordingStatus.PAUSED)
                updateNotification(RecordingStatus.PAUSED)
            }
        }
    }

    /** 用户主动暂停：停采集、放焦点，保留前台通知（隐私开关，对应 ui-design §4.5）。 */
    private fun pause() {
        Log.i(TAG, "pause requested")
        pausedByUser = true
        stopRecording()
        stateHolder.update(RecordingStatus.PAUSED)
        startForegroundCompat(RecordingStatus.PAUSED) // 维持前台，仅文案变「已暂停」
    }

    /** 从暂停恢复聆听。 */
    private fun resume() = startRecording()

    /** 停止采集循环并释放焦点（不退前台、不停服）。 */
    private fun stopRecording() {
        Log.i(TAG, "stopRecording active=${captureJob?.isActive == true}")
        captureJob?.cancel()
        captureJob = null
        abandonAudioFocus()
    }

    // —— 音频焦点（来电/其他录音 App 抢占时优雅暂停，恢复后续录）——

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                // 被抢：停采集但不算用户暂停，等焦点回来自动续
                if (!pausedByUser) {
                    Log.i(TAG, "audio focus lost change=$change")
                    captureJob?.cancel()
                    captureJob = null
                    stateHolder.update(RecordingStatus.PAUSED)
                    updateNotification(RecordingStatus.PAUSED)
                }
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                Log.i(TAG, "audio focus gain pausedByUser=$pausedByUser")
                if (!pausedByUser) startRecording()
            }
        }
    }

    private fun requestAudioFocus(): Boolean {
        val req = android.media.AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setOnAudioFocusChangeListener(focusListener)
            .build()
        focusRequest = req
        return audioManager.requestAudioFocus(req) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonAudioFocus() {
        (focusRequest as? android.media.AudioFocusRequest)?.let {
            audioManager.abandonAudioFocusRequest(it)
        }
        focusRequest = null
    }

    // —— 前台通知 ——

    private fun createChannel() {
        val mgr = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            RecordingLiveNotificationPolicy.CHANNEL_ID,
            "录音状态",
            RecordingLiveNotificationPolicy.CHANNEL_IMPORTANCE,
        ).apply {
            description = "echo 正在后台聆听的常驻通知"
            if (!RecordingLiveNotificationPolicy.ENABLE_SOUND) {
                setSound(null, null)
            }
            enableVibration(RecordingLiveNotificationPolicy.ENABLE_VIBRATION)
            enableLights(false)
            setShowBadge(false)
        }
        mgr.deleteNotificationChannel(RecordingLiveNotificationPolicy.LEGACY_CHANNEL_ID)
        mgr.createNotificationChannel(channel)
    }

    private fun buildNotification(status: RecordingStatus): Notification {
        val spec = RecordingNotificationSpec.from(status)
        val actionIntent = when (spec.action) {
            RecordingNotificationAction.PAUSE -> controlIntent(ACTION_PAUSE)
            RecordingNotificationAction.RESUME -> controlIntent(ACTION_RESUME)
        }
        val contentIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(this, RecordingLiveNotificationPolicy.CHANNEL_ID)
            .setContentTitle(spec.title)
            .setContentText(spec.text)
            .setSubText(spec.shortCriticalText)
            .setSmallIcon(spec.notificationIconResId())
            .setContentIntent(contentIntent)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(RecordingLiveNotificationPolicy.PRIORITY)
            .setColor(spec.color)
            .setColorized(spec.colorized)
            .setShowWhen(spec.showChronometer)
            .setUsesChronometer(spec.showChronometer)
            .setProgress(PROGRESS_MAX, PROGRESS_VALUE, spec.progressIndeterminate)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setExtras(liveUpdateExtras(spec))
            .addAction(0, spec.action.label, actionIntent)
            .build()
        return applyLiveProgressStyle(notification, spec)
    }

    private fun applyLiveProgressStyle(
        notification: Notification,
        spec: RecordingNotificationSpec,
    ): Notification {
        val pulseColor = spec.livePulseColor ?: return notification
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA) return notification

        val style = ProgressStyle()
            .setProgressIndeterminate(true)
            .setProgressTrackerIcon(Icon.createWithResource(this, spec.notificationIconResId()))
            .addProgressSegment(
                ProgressStyle.Segment(PROGRESS_MAX).setColor(pulseColor)
            )

        return Notification.Builder.recoverBuilder(this, notification)
            .setStyle(style)
            .build()
    }

    private fun liveUpdateExtras(spec: RecordingNotificationSpec): Bundle = Bundle().apply {
        putBoolean(EXTRA_REQUEST_PROMOTED_ONGOING, spec.requestPromotedOngoing)
        putString(EXTRA_SHORT_CRITICAL_TEXT, spec.shortCriticalText)
    }

    private fun RecordingNotificationSpec.notificationIconResId(): Int =
        when (liveIndicator) {
            RecordingLiveIndicator.BREATHING_DOT -> when (livePulseColor) {
                RecordingNotificationSpec.COLOR_RECORDING -> R.drawable.ic_stat_echo_dot_recording
                RecordingNotificationSpec.COLOR_LISTENING -> R.drawable.ic_stat_echo_dot_listening
                else -> R.drawable.ic_stat_echo_dot
            }
            RecordingLiveIndicator.MICROPHONE -> R.drawable.ic_stat_echo
        }

    private fun controlIntent(action: String): PendingIntent {
        val intent = Intent(this, RecordingService::class.java).apply { this.action = action }
        return PendingIntent.getService(
            this, action.hashCode(), intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun startForegroundCompat(status: RecordingStatus) {
        val notification = buildNotification(status)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun updateNotification(status: RecordingStatus) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIF_ID, buildNotification(status))
    }

    @Suppress("DEPRECATION")
    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            stopForeground(true)
        }
    }

    override fun onDestroy() {
        stopRecording()
        serviceScope.cancel()
        runCatching { vad?.close() }
        vad = null
        super.onDestroy()
    }

    companion object {
        private const val NOTIF_ID = 1001
        private const val TAG = "EchoRecordingService"
        private const val EXTRA_REQUEST_PROMOTED_ONGOING = "android.requestPromotedOngoing"
        private const val EXTRA_SHORT_CRITICAL_TEXT = "android.shortCriticalText"
        private const val PROGRESS_MAX = 100
        private const val PROGRESS_VALUE = 50

        const val ACTION_START = "tech.echo.app.action.START"
        const val ACTION_PAUSE = "tech.echo.app.action.PAUSE"
        const val ACTION_RESUME = "tech.echo.app.action.RESUME"
        const val ACTION_STOP = "tech.echo.app.action.STOP"

        private fun intent(context: Context, action: String) =
            Intent(context, RecordingService::class.java).apply { this.action = action }

        /** 启动/恢复聆听（前台服务）。 */
        fun start(context: Context) =
            ContextCompat.startForegroundService(context, intent(context, ACTION_START))

        fun pause(context: Context) = context.startService(intent(context, ACTION_PAUSE))
        fun resume(context: Context) =
            ContextCompat.startForegroundService(context, intent(context, ACTION_RESUME))
        fun stop(context: Context) = context.startService(intent(context, ACTION_STOP))
    }
}
