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
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import tech.echo.app.MainActivity
import tech.echo.app.R
import tech.echo.app.core.data.repository.SegmentRepository
import tech.echo.app.core.model.RecordingStatus
import tech.echo.app.core.upload.UploadProcessor
import tech.echo.app.core.upload.UploadWorkScheduler
import java.io.File
import javax.inject.Inject

/**
 * 錄音前台服務。
 *
 * captureJob 持續讀取麥克風、VAD 切段並寫入 Room。
 * 每當片段入庫後，直接在 serviceScope 啟動本機 ASR drain；Mutex 保證同一時間
 * 只有一條 Zipformer 處理管線，不阻塞錄音 coroutine，也不依賴 WorkManager。
 *
 * 舊 Channel queue 暫時保留作為相容性程式碼，但 0.6.3 的實際喚醒路徑
 * 由 wakeLocalAsr() 直接進入 UploadProcessor。
 */
@AndroidEntryPoint
class RecordingService : Service() {

    @Inject lateinit var repository: SegmentRepository
    @Inject lateinit var stateHolder: RecordingStateHolder
    @Inject lateinit var uploadProcessor: UploadProcessor
    @Inject lateinit var uploadWorkScheduler: UploadWorkScheduler

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var captureJob: Job? = null
    private var asrQueueJob: Job? = null
    private val asrDrainMutex = Mutex()

    /**
     * 舊版 Channel queue 暫留，避免一次同時改動過多；0.6.3 不再靠它觸發 ASR。
     */
    private val asrWakeups = Channel<Unit>(Channel.CONFLATED)

    private val audioCapture = AudioCapture()
    private var vad: VadDetector? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        // 舊 WorkManager ASR 工作先取消，避免和直接本機 ASR 競爭。
        uploadWorkScheduler.cancelScheduledAsrWork()
        startLocalAsrQueue()
        // App 更新、程序重啟或服務被系統重建時，主動撿回舊的
        // RECORDED / UPLOADING / TRANSCRIBING / FAILED 資料。
        wakeLocalAsr("service_start")
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
            else -> startRecording()
        }
        return START_STICKY
    }

    /**
     * 舊版 Channel consumer 暫時保留但不再由 wakeLocalAsr() 投遞 signal。
     * 這樣可以把 0.6.3 的變更聚焦在單一、可驗證的直接 drain 路徑。
     */
    private fun startLocalAsrQueue() {
        if (asrQueueJob?.isActive == true) return

        asrQueueJob = serviceScope.launch {
            for (ignored in asrWakeups) {
                var rounds = 0
                while (rounds < MAX_ASR_DRAIN_ROUNDS) {
                    rounds += 1
                    val result = try {
                        uploadProcessor.processPending()
                    } catch (error: Throwable) {
                        Log.w(TAG, "legacy local ASR queue failed", error)
                        break
                    }

                    Log.i(
                        TAG,
                        "legacy local ASR round=$rounds total=${result.total} " +
                            "completed=${result.completed} failed=${result.failed}",
                    )

                    if (result.total == 0) break
                    if (result.completed == 0) break
                }
            }
        }
    }

    /**
     * 直接啟動 ASR drain，不再只丟一個 Channel signal。
     *
     * 每次錄音片段入庫都會 launch 一個很輕量的工作；Mutex 讓真正的
     * UploadProcessor 串行執行。若前一輪已順手清空新片段，後面的工作只會
     * 查到 total=0 後立即結束，因此不會重複轉寫同一段。
     */
    private fun wakeLocalAsr(reason: String) {
        Log.i(TAG, "request direct local ASR drain reason=$reason")
        serviceScope.launch {
            asrDrainMutex.withLock {
                var rounds = 0
                while (rounds < MAX_ASR_DRAIN_ROUNDS) {
                    rounds += 1
                    val result = try {
                        uploadProcessor.processPending()
                    } catch (error: Throwable) {
                        Log.w(TAG, "direct local ASR drain failed reason=$reason", error)
                        break
                    }

                    Log.i(
                        TAG,
                        "direct local ASR drain reason=$reason round=$rounds " +
                            "total=${result.total} completed=${result.completed} failed=${result.failed}",
                    )

                    if (result.total == 0) break
                    // 若這一輪完全沒有成功項目，避免永久失敗資料形成 busy loop。
                    if (result.completed == 0) break
                }
            }
        }
    }

    /** 啟動前台 + 麥克風採集循環。 */
    private fun startRecording() {
        Log.i(TAG, "startRecording requested active=${captureJob?.isActive == true}")
        startForegroundCompat(RecordingStatus.LISTENING)
        if (captureJob?.isActive == true) return

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "startRecording missing RECORD_AUDIO permission")
            stateHolder.update(RecordingStatus.PAUSED)
            updateNotification(RecordingStatus.PAUSED)
            return
        }

        // Echo 只是麥克風記錄器，不申請 VOICE_COMMUNICATION Audio Focus。
        // 真正的電話/VoIP App 由 Android 的音訊輸入優先權機制處理。
        val detector = vad ?: runCatching {
            SileroVadDetector(applicationContext)
        }
            .getOrElse {
                Log.w(TAG, "startRecording vad init failed", it)
                stateHolder.update(RecordingStatus.PAUSED)
                updateNotification(RecordingStatus.PAUSED)
                return
            }.also { vad = it }

        val engine = RecordingEngine(
            audioDir = File(filesDir, "audio"),
            vad = detector,
            onSegmentRecorded = { segment ->
                repository.insertSegment(segment)
                // 片段一入庫就直接啟動 Zipformer drain，不等待 WorkManager/Channel。
                wakeLocalAsr("segment_recorded:${segment.id}")
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

    private fun pause() {
        Log.i(TAG, "pause requested")
        stopRecording()
        stateHolder.update(RecordingStatus.PAUSED)
        startForegroundCompat(RecordingStatus.PAUSED)
    }

    private fun resume() = startRecording()

    private fun stopRecording() {
        Log.i(TAG, "stopRecording active=${captureJob?.isActive == true}")
        captureJob?.cancel()
        captureJob = null
    }

    // —— 前台通知 ——

    private fun createChannel() {
        val mgr = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            RecordingLiveNotificationPolicy.CHANNEL_ID,
            "錄音狀態",
            RecordingLiveNotificationPolicy.CHANNEL_IMPORTANCE,
        ).apply {
            description = "Echo 正在背景聆聽的常駐通知"
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
        asrWakeups.close()
        serviceScope.cancel()
        runCatching { vad?.close() }
        vad = null
        super.onDestroy()
    }

    companion object {
        private const val NOTIF_ID = 1001
        private const val TAG = "EchoRecordingService"
        private const val MAX_ASR_DRAIN_ROUNDS = 50
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

        fun start(context: Context) =
            ContextCompat.startForegroundService(context, intent(context, ACTION_START))

        fun pause(context: Context) = context.startService(intent(context, ACTION_PAUSE))
        fun resume(context: Context) =
            ContextCompat.startForegroundService(context, intent(context, ACTION_RESUME))
        fun stop(context: Context) = context.startService(intent(context, ACTION_STOP))
    }
}
