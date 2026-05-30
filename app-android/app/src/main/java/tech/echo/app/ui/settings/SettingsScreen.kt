package tech.echo.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.Lifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import tech.echo.app.core.audio.PromotedNotificationPermission
import tech.echo.app.core.audio.PromotedNotificationPermissionState
import tech.echo.app.core.settings.VolcAsrResourceIds
import tech.echo.app.ui.theme.EchoSpacing

/**
 * 设置页：用户填写火山 ASR / DeepSeek 配置，保存到加密存储。
 *
 * 遵守 ui-design.md：M3 原生组件、单列、近黑主色、低密度留白。
 * key 字段用密码遮罩，避免肩窥。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val form by viewModel.form.collectAsStateWithLifecycle()
    val saved by viewModel.saved.collectAsStateWithLifecycle()
    val asrTest by viewModel.asrTest.collectAsStateWithLifecycle()
    val llmTest by viewModel.llmTest.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var promotedState by remember {
        mutableStateOf(PromotedNotificationPermission.current(context))
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        promotedState = PromotedNotificationPermission.current(context)
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("设置", style = MaterialTheme.typography.headlineSmall) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = EchoSpacing.pageHorizontal),
            verticalArrangement = Arrangement.spacedBy(EchoSpacing.elementGap),
        ) {
            LiveUpdateSettingsSection(
                state = promotedState,
                onOpenSettings = { PromotedNotificationPermission.openSettings(context) },
            )

            SectionTitle("火山引擎 · 语音转写")
            ConfigField(
                label = "App ID / API Key",
                value = form.volcAppId,
                onValueChange = { v -> viewModel.update { it.copy(volcAppId = v) } },
            )
            ConfigField(
                label = "Access Key（旧控制台）",
                value = form.volcAccessKey,
                onValueChange = { v -> viewModel.update { it.copy(volcAccessKey = v) } },
                isSecret = true,
            )
            ConfigField(
                label = "Resource ID",
                value = form.volcResourceId,
                onValueChange = { v -> viewModel.update { it.copy(volcResourceId = v) } },
                supportingText = VolcAsrResourceIds.settingsHint(form.volcResourceId),
            )
            ConnectionTestButton(
                text = "测试豆包语音连接",
                state = asrTest,
                onClick = viewModel::testAsr,
            )

            SectionTitle("DeepSeek · 每日整理")
            ConfigField(
                label = "Base URL",
                value = form.deepSeekBaseUrl,
                onValueChange = { v -> viewModel.update { it.copy(deepSeekBaseUrl = v) } },
            )
            ConfigField(
                label = "API Key",
                value = form.deepSeekApiKey,
                onValueChange = { v -> viewModel.update { it.copy(deepSeekApiKey = v) } },
                isSecret = true,
            )
            ConfigField(
                label = "模型名",
                value = form.deepSeekModel,
                onValueChange = { v -> viewModel.update { it.copy(deepSeekModel = v) } },
            )
            ConnectionTestButton(
                text = "测试 DeepSeek 连接",
                state = llmTest,
                onClick = viewModel::testLlm,
            )

            Button(
                onClick = viewModel::save,
                modifier = Modifier.fillMaxWidth().padding(top = EchoSpacing.elementGap),
            ) {
                Text(if (saved) "已保存" else "保存")
            }

            Text(
                "密钥仅加密保存在本机，不上传、不进安装包。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = EchoSpacing.sectionGap),
            )
        }
    }
}

@Composable
private fun LiveUpdateSettingsSection(
    state: PromotedNotificationPermissionState,
    onOpenSettings: () -> Unit,
) {
    if (!state.supported) return

    SectionTitle("后台状态")
    Text(
        text = if (state.enabled) "实时活动已开启" else "实时活动未开启",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface,
    )
    Text(
        text = if (state.enabled) {
            "系统可在顶部状态区显示聆听、记录和暂停。"
        } else {
            "打开后，系统才会把录音状态提升到顶部状态区。"
        },
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (state.needsUserAction) {
        OutlinedButton(
            onClick = onOpenSettings,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Outlined.Notifications, contentDescription = null)
            Spacer(Modifier.width(EchoSpacing.elementGapSmall))
            Text("打开实时活动设置")
        }
    }
}

@Composable
private fun ConnectionTestButton(
    text: String,
    state: ConnectionTestUiState,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = !state.testing,
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (state.testing) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
            )
        } else {
            Icon(Icons.Filled.PlayArrow, contentDescription = null)
        }
        Spacer(Modifier.width(EchoSpacing.elementGapSmall))
        Text(if (state.testing) "测试中…" else text)
    }
    state.message?.let { message ->
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = when (state.success) {
                true -> MaterialTheme.colorScheme.primary
                false -> MaterialTheme.colorScheme.error
                null -> MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

/** 分组标题（火山 / DeepSeek）。 */
@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(top = EchoSpacing.sectionGap),
    )
}

/** 单个配置输入框；isSecret=true 时密码遮罩。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConfigField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    isSecret: Boolean = false,
    supportingText: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        supportingText = supportingText?.let { text -> { Text(text) } },
        singleLine = true,
        visualTransformation = if (isSecret) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (isSecret) KeyboardType.Password else KeyboardType.Text,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}
