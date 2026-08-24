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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import tech.echo.app.ui.theme.EchoSpacing

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
    val systemSpeechTest by viewModel.systemSpeechTest.collectAsStateWithLifecycle()
    val llmTest by viewModel.llmTest.collectAsStateWithLifecycle()

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
            SectionTitle("Samsung / 系统语音识别诊断")
            Text(
                "先确认 Note10+ 是否向第三方 App 暴露系统或装置端语音辨识，并测试 Android 12 能否把 Echo 的 WAV 片段直接交给该辨识器。测试前请先暂停 Echo 录音并保持安静；若系统不支持 WAV 注入，辨识器可能尝试打开麦克风。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ConnectionTestButton(
                text = "测试 Samsung / 系统 ASR",
                state = systemSpeechTest,
                onClick = viewModel::testSystemSpeech,
            )

            SectionTitle("本机语音识别（备用）")
            Text(
                "Vosk 小型中文模型，完全在手机上转写；不需要火山引擎，也不按录音时数收费。若 Samsung / 系统 ASR 可稳定接收 Echo 的 WAV，我们之后可以改用系统辨识并移除 Vosk。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ConnectionTestButton(
                text = "测试 Vosk 本机中文语音识别",
                state = asrTest,
                onClick = viewModel::testAsr,
            )

            SectionTitle("DeepSeek · 每日整理")
            ConfigField(
                label = "Base URL",
                value = form.deepSeekBaseUrl,
                onValueChange = { viewModel.update { cfg -> cfg.copy(deepSeekBaseUrl = it) } },
            )
            ConfigField(
                label = "API Key",
                value = form.deepSeekApiKey,
                onValueChange = { viewModel.update { cfg -> cfg.copy(deepSeekApiKey = it) } },
                isSecret = true,
            )
            ConfigField(
                label = "模型名",
                value = form.deepSeekModel,
                onValueChange = { viewModel.update { cfg -> cfg.copy(deepSeekModel = it) } },
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
                "目前 Echo 正式转写仍使用 Vosk；Samsung / 系统 ASR 这里只做诊断，不会改动既有录音或逐字稿。每日整理仍会把转写文字发送给你配置的 DeepSeek 服务。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = EchoSpacing.sectionGap),
            )
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
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
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

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(top = EchoSpacing.sectionGap),
    )
}

@Composable
private fun ConfigField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    isSecret: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = if (isSecret) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (isSecret) KeyboardType.Password else KeyboardType.Text,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}
