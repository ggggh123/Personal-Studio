package com.example.personal_studio.feature.bitgrades

import com.example.personal_studio.data.local.db.dao.GradesDao
import com.example.personal_studio.data.local.db.entity.GradeEntryEntity
import com.example.personal_studio.domain.bitgrades.ComputeGpaUseCase
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WhatIfViewModelTest {
    @Before fun setUp() = Dispatchers.setMain(StandardTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun `reverseResult uses current book`() = runTest {
        val dao = mockk<GradesDao> {
            every { observeAll() } returns flowOf(listOf(
                GradeEntryEntity(0,"2024-2025-1","t","c","c",60.0,"x",3.0,null,null,"正常",true,1L)))
            every { observeRanks() } returns flowOf(emptyList())
        }
        val vm = WhatIfViewModel(dao, ComputeGpaUseCase())
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()
        val st = vm.uiState.value.copy(targetGpa = "3.2", remainingCredits = "40")
        // done=60, sum=60*3.0=180, (3.2*100-180)/40 = 3.5
        val r = vm.reverseResult(st)!!
        assertEquals(3.5, r.value, 0.001)
        assertEquals(true, r.achievable)
        job.cancel()
    }
}
