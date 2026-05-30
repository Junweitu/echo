package tech.echo.app.core.upload

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
import java.io.File

class VolcAsrClientTest {

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
    fun uploadsLocalFileAsBase64ToFlashApi() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("X-Api-Status-Code", "20000000")
                .setBody(
                    """
                    {
                      "result": {
                        "utterances": [
                          {"speaker": "1", "text": "你好", "start_time": 100, "end_time": 900}
                        ]
                      }
                    }
                    """.trimIndent(),
                ),
        )
        val audioFile = File.createTempFile("echo-asr", ".wav").apply {
            writeBytes(byteArrayOf(1, 2, 3))
            deleteOnExit()
        }
        val client = VolcAsrClient(
            configProvider = FakeConfigProvider(
                AppConfig(
                    volcAppId = "app-id",
                    volcAccessKey = "access-key",
                    volcResourceId = "volc.bigasr.auc_turbo",
                ),
            ),
            httpClient = OkHttpClient(),
            json = Json { ignoreUnknownKeys = true },
            endpoint = server.url("/recognize/flash").toString(),
        )

        val utterances = client.transcribe(audioFile)

        assertEquals("1", utterances.single().speakerLabel)
        assertEquals("你好", utterances.single().text)
        val request = server.takeRequest()
        assertEquals("/recognize/flash", request.path)
        assertEquals("app-id", request.getHeader("X-Api-App-Key"))
        assertEquals("access-key", request.getHeader("X-Api-Access-Key"))
        assertEquals("volc.bigasr.auc_turbo", request.getHeader("X-Api-Resource-Id"))
        assertEquals("-1", request.getHeader("X-Api-Sequence"))
        val body = request.body.readUtf8()
        assertTrue(body.contains("\"data\":\"AQID\""))
        assertTrue(body.contains("\"model_name\":\"bigmodel\""))
    }

    @Test
    fun rejectsStreamingResourceIdBeforeCallingFlashApi() = runTest {
        val client = clientForResourceId("volc.seedasr.sauc.duration")
        val audioFile = tempAudioFile()

        val error = captureConfigurationError {
            client.transcribe(audioFile)
        }

        assertTrue(error.message.orEmpty().contains("流式"))
        assertTrue(error.message.orEmpty().contains("volc.bigasr.auc_turbo"))
        assertEquals(0, server.requestCount)
    }

    @Test
    fun rejectsStandardFileResourceIdBeforeCallingFlashApi() = runTest {
        val client = clientForResourceId("volc.seedasr.auc")
        val audioFile = tempAudioFile()

        val error = captureConfigurationError {
            client.transcribe(audioFile)
        }

        assertTrue(error.message.orEmpty().contains("标准版"))
        assertTrue(error.message.orEmpty().contains("volc.bigasr.auc_turbo"))
        assertEquals(0, server.requestCount)
    }

    private fun clientForResourceId(resourceId: String): VolcAsrClient =
        VolcAsrClient(
            configProvider = FakeConfigProvider(
                AppConfig(
                    volcAppId = "api-key",
                    volcResourceId = resourceId,
                ),
            ),
            httpClient = OkHttpClient(),
            json = Json { ignoreUnknownKeys = true },
            endpoint = server.url("/recognize/flash").toString(),
        )

    private fun tempAudioFile(): File =
        File.createTempFile("echo-asr", ".wav").apply {
            writeBytes(byteArrayOf(1, 2, 3))
            deleteOnExit()
        }

    private suspend fun captureConfigurationError(block: suspend () -> Unit): AsrConfigurationException {
        var captured: AsrConfigurationException? = null
        try {
            block()
        } catch (error: AsrConfigurationException) {
            captured = error
        }
        return captured ?: throw AssertionError("Expected AsrConfigurationException")
    }

    private class FakeConfigProvider(
        private val appConfig: AppConfig,
    ) : AppConfigProvider {
        override val config: Flow<AppConfig> = flowOf(appConfig)
        override fun current(): AppConfig = appConfig
    }
}
