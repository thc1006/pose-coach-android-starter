package com.posecoach.app.pose

import android.content.Context
import androidx.lifecycle.LifecycleCoroutineScope
import io.mockk.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import timber.log.Timber

/**
 * Minimal test to isolate the UnsupportedOperationException issue
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PoseDetectionManagerMinimalTest {

    @Before
    fun setUp() {
        // Mock Timber to avoid log errors
        mockkStatic(Timber::class)
        every { Timber.d(any<String>()) } just Runs
        every { Timber.e(any<Throwable>(), any<String>()) } just Runs
        every { Timber.e(any<String>()) } just Runs
        every { Timber.v(any<String>()) } just Runs
        every { Timber.w(any<String>()) } just Runs
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `should be able to run basic test`() {
        // Simple test to verify test infrastructure works
        assertTrue(true)
    }

    @Test
    fun `should be able to create mock context`() {
        val mockContext = mockk<Context>()
        assertNotNull(mockContext)
    }

    @Test
    fun `should be able to create mock lifecycle scope`() {
        val mockLifecycleScope = mockk<LifecycleCoroutineScope>()
        assertNotNull(mockLifecycleScope)
    }
}