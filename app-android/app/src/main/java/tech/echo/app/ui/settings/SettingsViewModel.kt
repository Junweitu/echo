package tech.echo.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import tech.echo.app.core.settings.AppConfig
import tech.echo.app.core.settings.SettingsRepository
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: SettingsRepository,
    private val connectionTester: SettingsConnectionTester,
    private val systemSpeechDiagnostic: SystemSpeechDiagnostic,
) : ViewModel() {

    private val _form = MutableStateFlow(repository.current())
    val form: StateFlow<AppConfig> = _form.asStateFlow()

    private val _saved = MutableStateFlow(false)
    val saved: StateFlow<Boolean> = _saved.asStateFlow()

    private val _asrTest = MutableStateFlow(ConnectionTestUiState())
    val asrTest: StateFlow<ConnectionTestUiState> = _asrTest.asStateFlow()

    private val _systemSpeechTest = MutableStateFlow(ConnectionTestUiState())
    val systemSpeechTest: StateFlow<ConnectionTestUiState> = _systemSpeechTest.asStateFlow()

    private val _llmTest = MutableStateFlow(ConnectionTestUiState())
    val llmTest: StateFlow<ConnectionTestUiState> = _llmTest.asStateFlow()

    fun update(transform: (AppConfig) -> AppConfig) {
        _form.value = transform(_form.value)
        _saved.value = false
    }

    fun save() {
        viewModelScope.launch {
            repository.save(_form.value)
            _saved.value = true
        }
    }

    fun testAsr() {
        viewModelScope.launch {
            _asrTest.value = ConnectionTestUiState(testing = true, message = "正在載入本機中文語音模型…")
            _asrTest.value = connectionTester.testAsr().toUiState()
        }
    }

    fun testSystemSpeech() {
        viewModelScope.launch {
            _systemSpeechTest.value = ConnectionTestUiState(
                testing = true,
                message = "正在檢查 Samsung / Android 系統語音辨識，並測試 WAV 注入…",
            )
            _systemSpeechTest.value = systemSpeechDiagnostic.run().toUiState()
        }
    }

    fun testLlm() {
        viewModelScope.launch {
            saveCurrentFormForTest()
            _llmTest.value = ConnectionTestUiState(testing = true, message = "正在測試 DeepSeek…")
            _llmTest.value = connectionTester.testLlm().toUiState()
        }
    }

    private fun saveCurrentFormForTest() {
        repository.save(_form.value)
        _saved.value = true
    }

    private fun ConnectionTestResult.toUiState(): ConnectionTestUiState =
        ConnectionTestUiState(testing = false, success = success, message = message)
}

data class ConnectionTestUiState(
    val testing: Boolean = false,
    val success: Boolean? = null,
    val message: String? = null,
)
