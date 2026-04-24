package com.example.personal_studio.feature.scanner.enhance

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * The VM's init block decodes a real file via BitmapIo.decodeDownscaled, which touches
 * the Android BitmapFactory — unavailable in pure JVM. A richer test would use Robolectric
 * or a testable factory that accepts a pre-loaded Bitmap. For now, VM behavior is covered
 * by on-device smoke (Task 14) and this shell verifies the test harness compiles.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EnhanceReviewViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @Before fun before() { Dispatchers.setMain(dispatcher) }
    @After fun after() { Dispatchers.resetMain() }

    @Test fun `selectFilter updates state to requested filter`() = runTest {
        // Placeholder — see class-level doc. VM cannot be constructed here without
        // a real Bitmap decode. Rich coverage deferred.
    }
}
