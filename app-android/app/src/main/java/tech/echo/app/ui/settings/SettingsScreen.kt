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
                title = { Text("設定", style = MaterialTheme.typography.headlineSmall) },
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
            SectionTitle("正式語音辨識")
            Text(
                "Echo 現在優先使用 Samsung / Bixby 系統語音辨識處理 WAV 片段；若 Samsung 服務不可用、無網路或辨識失敗，會自動改用 Vosk 本機中文模型，不需要火山引擎 API Key。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            SectionTitle("Samsung / 系統語音辨識診斷")
            Text(
                "用於檢查系統、Google 與 Samsung/Bixby RecognitionService 是否能直接接收 Echo 的 WAV。診斷本身不會改動既有錄音或逐字稿。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ConnectionTestButton(
                text = "測試 Samsung / 系統 ASR",
                state = systemSpeechTest,
                onClick = viewModel::testSystemSpeech,
            )

            SectionTitle("Vosk 本機語音辨識（自動備援）")
            Text(
                "Vosk 小型中文模型完全在手機上轉寫，不需要網路，也不按錄音時數收費。這個按鈕只測試 Vosk 備援引擎。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ConnectionTestButton(
                text = "測試 Vosk 本機中文語音辨識",
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
                label = "模型名稱",
                value = form.deepSeekModel,
                onValueChange = { viewModel.update { cfg -> cfg.copy(deepSeekModel = it) } },
            )
            ConnectionTestButton(
                text = "測試 DeepSeek 連線",
                state = llmTest,
                onClick = viewModel::testLlm,
            )

            Button(
                onClick = viewModel::save,
                modifier = Modifier.fillMaxWidth().padding(top = EchoSpacing.elementGap),
            ) {
                Text(if (saved) "已儲存" else "儲存")
            }

            Text(
                "注意：這台手機回報 Android『裝置端語音辨識』不可用，因此 Samsung/Bixby 路徑不保證完全離線；若 Samsung 失敗，Vosk 備援路徑則完全離線。每日整理仍會把轉寫文字傳送給你設定的 DeepSeek 服務。",
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
        Text(if (state.testing) "測試中…" else text)
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
