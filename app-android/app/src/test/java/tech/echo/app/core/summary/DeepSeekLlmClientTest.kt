package tech.echo.app.core.summary

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import tech.echo.app.core.settings.AppConfig
import tech.echo.app.core.settings.AppConfigProvider

class DeepSeekLlmClientTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun postsOpenAiCompatibleJsonModeRequest() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    {
                      "choices": [
                        {"message": {"content": "{\"diary\":\"ok\",\"todos\":[],\"inspirations\":[],\"timeline\":[]}"}}
                      ]
                    }
                    """.trimIndent(),
                ),
        )

        val client = DeepSeekLlmClient(
            configProvider = FakeConfigProvider(
                AppConfig(
                    deepSeekBaseUrl = server.url("/").toString().trimEnd('/'),
                    deepSeekApiKey = "sk-test",
                    deepSeekModel = "deepseek-v4-flash",
                ),
            ),
            httpClient = OkHttpClient(),
            json = Json { ignoreUnknownKeys = true },
        )

        assertEquals(
            "{\"diary\":\"ok\",\"todos\":[],\"inspirations\":[],\"timeline\":[]}",
            client.summarize("please return json"),
        )

        val request = server.takeRequest()
        assertEquals("/chat/completions", request.path)
        assertEquals("Bearer sk-test", request.getHeader("Authorization"))
        val body = request.body.readUtf8()
        assertTrue(body.contains("\"model\":\"deepseek-v4-flash\""))
        assertTrue(body.contains("\"response_format\":{\"type\":\"json_object\"}"))
    }

    private class FakeConfigProvider(
        private val appConfig: AppConfig,
    ) : AppConfigProvider {
        override val config: Flow<AppConfig> = flowOf(appConfig)
        override fun current(): AppConfig = appConfig
    }
}
