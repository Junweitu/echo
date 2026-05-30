package tech.echo.app.ui.settings

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject

interface AsrTestAudioProvider {
    fun createSampleFile(sampleDir: File): File
}

class AssetAsrTestAudioProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) : AsrTestAudioProvider {
    override fun createSampleFile(sampleDir: File): File {
        sampleDir.mkdirs()
        val file = File(sampleDir, SAMPLE_FILE_NAME)
        context.assets.open(ASSET_NAME).use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        }
        return file
    }

    private companion object {
        const val ASSET_NAME = "asr_test_zh.wav"
        const val SAMPLE_FILE_NAME = "asr-connection-test-zh.wav"
    }
}
