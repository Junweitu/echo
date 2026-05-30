package tech.echo.app.ui.settings

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tech.echo.app.core.summary.LlmClient
import tech.echo.app.core.upload.AsrClient
import tech.echo.app.core.upload.AsrStatusException
import tech.echo.app.core.upload.AsrUtterance
import java.io.File
import kotlin.io.path.createTempDirectory

class SettingsConnectionTesterTest {

    @Test
    fun llmTestReturnsSuccessWhenClientResponds() = runTest {
        val tester = SettingsConnectionTester(
            sampleDir = createTempDir(),
            asrClient = FakeAsrClient(),
            llmClient = FakeLlmClient("""{"ok":true}"""),
            audioProvider = FakeAudioProvider(),
        )

        val result = tester.testLlm()

        assertTrue(result.success)
        assertEquals("DeepSeek 连接正常", result.message)
    }

    @Test
    fun asrTestCreatesValidWavFileAndReturnsSuccess() = runTest {
        val asrClient = FakeAsrClient()
        val audioProvider = FakeAudioProvider()
        val tester = SettingsConnectionTester(
            sampleDir = createTempDir(),
            asrClient = asrClient,
            llmClient = FakeLlmClient("""{"ok":true}"""),
            audioProvider = audioProvider,
        )

        val result = tester.testAsr()

        assertTrue(result.success)
        assertEquals("豆包语音连接正常", result.message)
        assertTrue(audioProvider.called)
        assertTrue(asrClient.called)
        assertEquals("RIFF", asrClient.header)
    }

    @Test
    fun clientFailureReturnsReadableError() = runTest {
        val tester = SettingsConnectionTester(
            sampleDir = createTempDir(),
            asrClient = FakeAsrClient(error = IllegalStateException("bad key")),
            llmClient = FakeLlmClient("""{"ok":true}"""),
            audioProvider = FakeAudioProvider(),
        )

        val result = tester.testAsr()

        assertFalse(result.success)
        assertTrue(result.message.contains("bad key"))
    }

    @Test
    fun silentAudioStatusStillMeansAsrConnectionIsReachable() = runTest {
        val tester = SettingsConnectionTester(
            sampleDir = createTempDir(),
            asrClient = FakeAsrClient(error = AsrStatusException("20000003", "静音音频", "{}")),
            llmClient = FakeLlmClient("""{"ok":true}"""),
            audioProvider = FakeAudioProvider(),
        )

        val result = tester.testAsr()

        assertTrue(result.success)
        assertEquals("豆包语音连接正常（测试音频无语音）", result.message)
    }

    private class FakeAsrClient(
        private val error: Throwable? = null,
    ) : AsrClient {
        var called = false
        var header = ""

        override suspend fun transcribe(audioFile: File): List<AsrUtterance> {
            called = true
            header = audioFile.inputStream().use { input ->
                String(input.readNBytes(4), Charsets.US_ASCII)
            }
            error?.let { throw it }
            return emptyList()
        }
    }

    private fun createTempDir(): File = createTempDirectory("echo-settings-test").toFile()

    private class FakeLlmClient(
        private val response: String,
    ) : LlmClient {
        override suspend fun summarize(prompt: String): String = response
    }

    private class FakeAudioProvider : AsrTestAudioProvider {
        var called = false

        override fun createSampleFile(sampleDir: File): File {
            called = true
            sampleDir.mkdirs()
            return File(sampleDir, "sample.wav").apply {
                writeBytes("RIFFfake voice sample".toByteArray(Charsets.US_ASCII))
            }
        }
    }
}
