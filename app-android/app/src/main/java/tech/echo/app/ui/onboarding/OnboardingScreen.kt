package tech.echo.app.ui.onboarding

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import tech.echo.app.ui.theme.EchoSpacing

private data class OnboardingPage(
    val icon: ImageVector,
    val title: String,
    val body: String,
    val cta: String,
)

private val onboardingPages = listOf(
    OnboardingPage(
        icon = Icons.Outlined.GraphicEq,
        title = "echo 帮你记住每一天",
        body = "它在后台安静聆听，听到说话才记录，每天自动整理成日记、待办和灵感。",
        cta = "开始",
    ),
    OnboardingPage(
        icon = Icons.Outlined.Mic,
        title = "需要麦克风权限",
        body = "echo 只在你开启时聆听，录音先存在手机本地，由你掌控。",
        cta = "允许麦克风",
    ),
    OnboardingPage(
        icon = Icons.Outlined.BatteryChargingFull,
        title = "请允许后台运行",
        body = "为了不漏掉重要的话，echo 需要在后台保持聆听。请在弹窗里选择「不优化」。",
        cta = "去设置",
    ),
)

// __CONTINUE_HERE__

/**
 * 首启引导（见 ui-design.md §5 / 03 号图）：3 页，逐页申请真实权限。
 * - 第 2 页：申请 RECORD_AUDIO（+ Android 13+ POST_NOTIFICATIONS）。
 * - 第 3 页：跳系统「忽略电池优化」弹窗。
 * 权限是否授予不阻塞流程（用户可拒，今天页仍可见，仅录音受限）。
 */
@Composable
fun OnboardingScreen(
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var index by remember { mutableIntStateOf(0) }
    val page = onboardingPages[index]
    val isLast = index == onboardingPages.lastIndex

    // 麦克风（+通知）权限申请：无论授予与否都进入下一页，不卡流程
    val micPermissions = remember {
        buildList {
            add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.toTypedArray()
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { index++ }

    Box(modifier = modifier.fillMaxSize().padding(EchoSpacing.pageHorizontal)) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = page.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.size(64.dp),
            )
            Spacer(Modifier.height(EchoSpacing.sectionGap))
            Text(
                page.title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(EchoSpacing.elementGap))
            Text(
                page.body,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }

        Column(
            modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter).padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Button(
                onClick = {
                    when (index) {
                        0 -> index++                                   // 介绍页：直接下一页
                        1 -> permissionLauncher.launch(micPermissions) // 申请麦克风/通知，回调里 index++
                        else -> {                                      // 电池白名单后完成
                            requestIgnoreBatteryOptimizations(context)
                            onFinish()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(page.cta) }

            if (!isLast) {
                Spacer(Modifier.height(EchoSpacing.elementGapSmall))
                TextButton(onClick = onFinish) { Text("跳过") }
            }
        }
    }
}
/** 跳转系统「忽略电池优化」弹窗；已忽略或无法跳转则静默跳过。 */
private fun requestIgnoreBatteryOptimizations(context: Context) {
    val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
    if (pm.isIgnoringBatteryOptimizations(context.packageName)) return
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
        )
    }.onFailure {
        // 个别 ROM 不支持该 Action：退化到电池优化设置列表页
        runCatching {
            context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }
}
