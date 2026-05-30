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
 * 配置仓库：用 [EncryptedSharedPreferences]（AES256-GCM）加密存敏感配置。
 *
 * key 不进明文 prefs、不进 git、不进 BuildConfig（见 journal 2026-05-29 决策）。
 * [config] 暴露为 Flow，写入后 ASR/LLM 客户端实时拿到最新配置。
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

    /** 当前配置（一次性读，客户端构造请求前取）。 */
    override fun current(): AppConfig = readConfig()

    /** 配置变更流：写入后下游（ASR/LLM/设置页）实时刷新。 */
    override val config: Flow<AppConfig> = callbackFlow {
        trySend(readConfig())
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            trySend(readConfig())
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    /** 保存配置（设置页点保存调用）。 */
    fun save(config: AppConfig) {
        prefs.edit()
            .putString(KEY_VOLC_APP_ID, config.volcAppId)
            .putString(KEY_VOLC_ACCESS_KEY, config.volcAccessKey)
            .putString(KEY_VOLC_RESOURCE_ID, config.volcResourceId)
            .putString(KEY_DS_BASE_URL, config.deepSeekBaseUrl)
            .putString(KEY_DS_API_KEY, config.deepSeekApiKey)
            .putString(KEY_DS_MODEL, config.deepSeekModel)
            .apply()
    }

    private fun readConfig(): AppConfig = AppConfig(
        volcAppId = prefs.getString(KEY_VOLC_APP_ID, "").orEmpty(),
        volcAccessKey = prefs.getString(KEY_VOLC_ACCESS_KEY, "").orEmpty(),
        volcResourceId = prefs.getString(KEY_VOLC_RESOURCE_ID, AppConfig.DEFAULT_VOLC_RESOURCE_ID)
            .orEmpty(),
        deepSeekBaseUrl = prefs.getString(KEY_DS_BASE_URL, AppConfig.DEFAULT_DEEPSEEK_BASE_URL)
            .orEmpty(),
        deepSeekApiKey = prefs.getString(KEY_DS_API_KEY, "").orEmpty(),
        deepSeekModel = prefs.getString(KEY_DS_MODEL, AppConfig.DEFAULT_DEEPSEEK_MODEL).orEmpty(),
    )

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
