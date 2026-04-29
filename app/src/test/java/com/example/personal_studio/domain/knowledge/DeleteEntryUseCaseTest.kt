package com.example.personal_studio.domain.knowledge

import com.example.personal_studio.data.repository.FakeKnowledgeRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class DeleteEntryUseCaseTest {
    @Test fun forwardsToRepository() = runTest {
        val repo = FakeKnowledgeRepository()
        DeleteEntryUseCase(repo)(42L)
        assertEquals(42L, repo.lastDeletedId)
    }
}
