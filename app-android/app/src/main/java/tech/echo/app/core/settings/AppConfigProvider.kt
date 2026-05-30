package tech.echo.app.core.settings

import kotlinx.coroutines.flow.Flow

/** 运行时配置读取接口，方便网络客户端与测试使用同一契约。 */
interface AppConfigProvider {
    val config: Flow<AppConfig>
    fun current(): AppConfig
}
