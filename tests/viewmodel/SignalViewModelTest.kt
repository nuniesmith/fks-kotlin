package xyz.fkstrading.clients.domain.viewmodel

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for SignalViewModel
 */
class SignalViewModelTest {
    @Test
    fun testInitialState() =
        runTest {
            val viewModel = SignalViewModel()

            assertTrue(viewModel.signals.first().isEmpty(), "Should have no signals initially")
            assertEquals(null, viewModel.signalSummary.first(), "Signal summary should be null initially")
            assertFalse(viewModel.isLoading.first(), "Should not be loading initially")
            assertEquals(null, viewModel.error.first(), "Should have no error initially")
            assertEquals("swing", viewModel.selectedCategory.first(), "Default category should be 'swing'")
        }

    @Test
    fun testClearError() =
        runTest {
            val viewModel = SignalViewModel()

            // Note: In a real test, we would set an error through mocked repository
            // For now, just test the method exists and doesn't crash
            viewModel.clearError()

            assertEquals(null, viewModel.error.first(), "Error should be cleared")
        }

    // Note: Full signal loading tests would require mocking SignalRepository
    // which would need dependency injection setup
}
