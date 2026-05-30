package tech.echo.app.core.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import tech.echo.app.core.data.db.DayCount
import tech.echo.app.core.data.db.SegmentDao
import tech.echo.app.core.data.db.SegmentEntity
import tech.echo.app.core.data.db.SegmentStatus

class SegmentRepositoryTest {

    private class FakeSegmentDao(
        private val uploadCandidates: List<SegmentEntity> = emptyList(),
    ) : SegmentDao {
        var claimedSegment: Pair<String, String>? = null
        var claimedDateLabel: Triple<String, String, String>? = null

        override suspend fun insert(segment: SegmentEntity) = Unit
        override fun observeByDate(date: String): Flow<List<SegmentEntity>> = emptyFlow()
        override fun observeCountByDate(date: String): Flow<Int> = emptyFlow()
        override fun observeTotalDurationByDate(date: String): Flow<Long> = emptyFlow()
        override fun observeDailyCounts(): Flow<List<DayCount>> = emptyFlow()
        override suspend fun deleteOlderThan(beforeDate: String) = Unit
        override suspend fun findUploadCandidates(): List<SegmentEntity> = uploadCandidates
        override suspend fun updateStatus(id: String, status: String) = Unit
        override suspend fun markTranscribed(id: String, text: String, speakerLabel: String?) = Unit
        override suspend fun claimSpeakerForSegment(id: String, personName: String) {
            claimedSegment = id to personName
        }
        override suspend fun claimSpeakerByDateAndLabel(
            date: String,
            speakerLabel: String,
            personName: String,
        ) {
            claimedDateLabel = Triple(date, speakerLabel, personName)
        }
        override suspend fun getDoneByDate(date: String): List<SegmentEntity> = emptyList()
        override suspend fun getByDate(date: String): List<SegmentEntity> = emptyList()
    }

    @Test
    fun `claim speaker with raw speaker label updates same day label`() = runTest {
        val dao = FakeSegmentDao()
        val repository = SegmentRepository(dao)

        repository.claimSpeaker(
            date = "20260530",
            segmentId = "seg-1",
            speakerLabel = "A",
            personName = " 我 ",
        )

        assertEquals(Triple("20260530", "A", "我"), dao.claimedDateLabel)
        assertNull(dao.claimedSegment)
    }

    @Test
    fun `claim speaker without raw speaker label updates only selected segment`() = runTest {
        val dao = FakeSegmentDao()
        val repository = SegmentRepository(dao)

        repository.claimSpeaker(
            date = "20260530",
            segmentId = "seg-1",
            speakerLabel = null,
            personName = "我",
        )

        assertEquals("seg-1" to "我", dao.claimedSegment)
        assertNull(dao.claimedDateLabel)
    }

    @Test
    fun `blank claim name is ignored`() = runTest {
        val dao = FakeSegmentDao()
        val repository = SegmentRepository(dao)

        repository.claimSpeaker(
            date = "20260530",
            segmentId = "seg-1",
            speakerLabel = "A",
            personName = " ",
        )

        assertNull(dao.claimedDateLabel)
        assertNull(dao.claimedSegment)
    }

    @Test
    fun `pending upload prioritizes fresh recorded segments before failed backlog`() = runTest {
        val oldFailed = segment(id = "failed-old", startTime = 1, status = SegmentStatus.FAILED)
        val newRecorded = segment(id = "recorded-new", startTime = 3, status = SegmentStatus.RECORDED)
        val olderRecorded = segment(id = "recorded-old", startTime = 2, status = SegmentStatus.RECORDED)
        val interruptedUploading = segment(id = "uploading", startTime = 4, status = SegmentStatus.UPLOADING)
        val dao = FakeSegmentDao(uploadCandidates = listOf(oldFailed, newRecorded, interruptedUploading, olderRecorded))
        val repository = SegmentRepository(dao)

        val pending = repository.findPendingUpload(limit = 3)

        assertEquals(listOf("recorded-old", "recorded-new", "uploading"), pending.map { it.id })
    }

    private fun segment(
        id: String,
        startTime: Long,
        status: SegmentStatus,
    ): SegmentEntity =
        SegmentEntity(
            id = id,
            date = "20260530",
            startTime = startTime,
            durationMs = 1000,
            audioPath = "/tmp/$id.wav",
            status = status.name,
        )
}
