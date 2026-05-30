package tech.echo.app.core.upload

import android.util.Log
import tech.echo.app.core.data.db.SegmentStatus
import tech.echo.app.core.data.repository.SegmentRepository
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UploadProcessor @Inject constructor(
    private val segmentRepository: SegmentRepository,
    private val asrClient: AsrClient,
) {
    suspend fun processPending(limit: Int = DEFAULT_LIMIT): UploadBatchResult {
        val pending = segmentRepository.findPendingUpload(limit)
        var completed = 0
        var failed = 0

        pending.forEach { segment ->
            runCatching {
                segmentRepository.updateStatus(segment.id, SegmentStatus.UPLOADING.name)
                val utterances = asrClient.transcribe(File(segment.audioPath))
                if (utterances.isEmpty()) throw IOException("ASR 返回空转写")
                segmentRepository.markTranscribed(
                    id = segment.id,
                    text = TranscriptionFormatter.combineText(utterances),
                    speakerLabel = TranscriptionFormatter.primarySpeakerLabel(utterances),
                )
                completed += 1
            }.onFailure {
                Log.w(TAG, "upload segment failed id=${segment.id} status=${it.javaClass.simpleName}: ${it.message}", it)
                segmentRepository.updateStatus(segment.id, SegmentStatus.FAILED.name)
                failed += 1
            }
        }

        return UploadBatchResult(
            total = pending.size,
            completed = completed,
            failed = failed,
        )
    }

    companion object {
        private const val TAG = "EchoUploadProcessor"
        const val DEFAULT_LIMIT = 10
    }
}

data class UploadBatchResult(
    val total: Int,
    val completed: Int,
    val failed: Int,
)
