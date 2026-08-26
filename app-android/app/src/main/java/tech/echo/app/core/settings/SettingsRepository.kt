package tech.echo.app.core.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 配置倉庫：用 [EncryptedSharedPreferences]（AES256-GCM）加密存敏感配置。
 *
 * key 不進明文 prefs、不進 git、不進 BuildConfig。
 * [config] 暴露為 Flow，寫入後 ASR/LLM 客戶端即時拿到最新配置。
 */
@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext context: Context,
) : AppConfigProvider {
    private val prefs: SharedPreferences = run {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    override fun current(): AppConfig = readConfig()

    override val config: Flow<AppConfig> = callbackFlow {
        trySend(readConfig())
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            trySend(readConfig())
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    /**
     * 儲存前先正規化 DeepSeek 欄位。
     *
     * Android 剪貼簿有時會把 API Key 尾端的 CR/LF、BOM 或 zero-width space 一起貼進來；
     * 這些字元肉眼看不到，但 OkHttp 會拒絕把它們放進 Authorization header。
     */
    fun save(config: AppConfig) {
        prefs.edit()
            .putString(KEY_VOLC_APP_ID, config.volcAppId)
            .putString(KEY_VOLC_ACCESS_KEY, config.volcAccessKey)
            .putString(KEY_VOLC_RESOURCE_ID, config.volcResourceId)
            .putString(KEY_DS_BASE_URL, sanitizePlainField(config.deepSeekBaseUrl))
            .putString(KEY_DS_API_KEY, sanitizeApiKey(config.deepSeekApiKey))
            .putString(KEY_DS_MODEL, sanitizePlainField(config.deepSeekModel))
            .apply()
    }

    private fun readConfig(): AppConfig = AppConfig(
        volcAppId = prefs.getString(KEY_VOLC_APP_ID, "").orEmpty(),
        volcAccessKey = prefs.getString(KEY_VOLC_ACCESS_KEY, "").orEmpty(),
        volcResourceId = prefs.getString(KEY_VOLC_RESOURCE_ID, AppConfig.DEFAULT_VOLC_RESOURCE_ID)
            .orEmpty(),
        deepSeekBaseUrl = sanitizePlainField(
            prefs.getString(KEY_DS_BASE_URL, AppConfig.DEFAULT_DEEPSEEK_BASE_URL).orEmpty()
        ).ifBlank { AppConfig.DEFAULT_DEEPSEEK_BASE_URL },
        // 讀取時也清一次，讓舊版本已經儲存進去的 CR/LF 不必手動重輸 Key 就能修復。
        deepSeekApiKey = sanitizeApiKey(prefs.getString(KEY_DS_API_KEY, "").orEmpty()),
        deepSeekModel = sanitizePlainField(
            prefs.getString(KEY_DS_MODEL, AppConfig.DEFAULT_DEEPSEEK_MODEL).orEmpty()
        ).ifBlank { AppConfig.DEFAULT_DEEPSEEK_MODEL },
    )

    private fun sanitizePlainField(value: String): String =
        value.replace("\r", "").replace("\n", "").trim()

    private fun sanitizeApiKey(value: String): String = buildString(value.length) {
        value.forEach { ch ->
            if (!ch.isWhitespace() && !Character.isISOControl(ch) && ch != '\u200B' && ch != '\uFEFF') {
                append(ch)
            }
        }
    }

    private companion object {
        const val PREFS_NAME = "echo_secure_settings"
        const val KEY_VOLC_APP_ID = "volc_app_id"
        const val KEY_VOLC_ACCESS_KEY = "volc_access_key"
        const val KEY_VOLC_RESOURCE_ID = "volc_resource_id"
        const val KEY_DS_BASE_URL = "ds_base_url"
        const val KEY_DS_API_KEY = "ds_api_key"
        const val KEY_DS_MODEL = "ds_model"
    }
}
