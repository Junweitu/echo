package tech.echo.app.core.audio

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

data class PromotedNotificationPermissionState(
    val supported: Boolean,
    val enabled: Boolean,
) {
    val needsUserAction: Boolean = supported && !enabled
}

data class PromotedNotificationSettingsIntentSpec(
    val action: String,
    val packageExtraKey: String,
    val packageName: String,
)

object PromotedNotificationPermission {
    const val MIN_SDK = 36
    private const val PROMOTION_SETTINGS_ACTION = "android.settings.APP_NOTIFICATION_PROMOTION_SETTINGS"
    private const val APP_PACKAGE_EXTRA = "android.provider.extra.APP_PACKAGE"

    fun evaluate(
        sdkInt: Int,
        canPostPromotedNotifications: Boolean,
    ): PromotedNotificationPermissionState =
        if (sdkInt >= MIN_SDK) {
            PromotedNotificationPermissionState(
                supported = true,
                enabled = canPostPromotedNotifications,
            )
        } else {
            PromotedNotificationPermissionState(
                supported = false,
                enabled = false,
            )
        }

    fun current(context: Context): PromotedNotificationPermissionState {
        if (Build.VERSION.SDK_INT < MIN_SDK) {
            return evaluate(Build.VERSION.SDK_INT, canPostPromotedNotifications = false)
        }
        val canPost = runCatching {
            context.getSystemService(NotificationManager::class.java)
                .canPostPromotedNotifications()
        }.getOrDefault(false)
        return evaluate(Build.VERSION.SDK_INT, canPost)
    }

    fun settingsIntentSpec(packageName: String): PromotedNotificationSettingsIntentSpec =
        PromotedNotificationSettingsIntentSpec(
            action = PROMOTION_SETTINGS_ACTION,
            packageExtraKey = APP_PACKAGE_EXTRA,
            packageName = packageName,
        )

    fun openSettings(context: Context) {
        val spec = settingsIntentSpec(context.packageName)
        val intent = Intent(spec.action)
            .putExtra(spec.packageExtraKey, spec.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching {
            context.startActivity(intent)
        }.onFailure {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}
