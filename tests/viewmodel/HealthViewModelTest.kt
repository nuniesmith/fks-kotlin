package xyz.fkstrading.clients.domain.viewmodel

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for HealthViewModel
 */
class HealthViewModelTest {
    @Test
    fun testInitialState() =
        runTest {
            val viewModel = HealthViewModel()

            assertEquals(null, viewModel.healthStatus.first(), "Health status should be null initially")
            assertEquals(null, viewModel.comprehensiveHealth.first(), "Comprehensive health should be null initially")
            assertTrue(viewModel.testResults.first().isEmpty(), "Should have no test results initially")
            assertFalse(viewModel.isLoading.first(), "Should not be loading initially")
            assertFalse(viewModel.isChecking.first(), "Should not be checking initially")
            assertEquals(null, viewModel.error.first(), "Should have no error initially")
        }

    @Test
    fun testClearTestResults() =
        runTest {
            val viewModel = HealthViewModel()

            viewModel.clearTestResults()

            assertTrue(viewModel.testResults.first().isEmpty(), "Test results should be empty after clearing")
        }

    @Test
    fun testClearError() =
        runTest {
            val viewModel = HealthViewModel()

            viewModel.clearError()

            assertEquals(null, viewModel.error.first(), "Error should be cleared")
        }

    // Note: Full health check tests would require mocking HealthRepository
    // which would need dependency injection setup
}
