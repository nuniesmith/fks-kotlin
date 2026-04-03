package xyz.fkstrading.clients.domain.viewmodel

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Advanced tests for SignalViewModel
 * Tests filtering and data manipulation methods
 */
class SignalViewModelAdvancedTest {
    @Test
    fun testGetSignalsByType() =
        runTest {
            val viewModel = SignalViewModel()

            // Note: In a real test, we would load test signals first
            // For now, test the method exists and handles empty list
            val buySignals = viewModel.getBuySignals()
            assertTrue(buySignals.isEmpty(), "Should return empty list when no signals loaded")

            val sellSignals = viewModel.getSellSignals()
            assertTrue(sellSignals.isEmpty(), "Should return empty list when no signals loaded")
        }

    @Test
    fun testGetSignalsByStrength() =
        runTest {
            val viewModel = SignalViewModel()

            // Test with empty signals
            val strongSignals = viewModel.getStrongSignals()
            assertTrue(strongSignals.isEmpty(), "Should return empty list when no signals loaded")
        }

    @Test
    fun testGetHighConfidenceSignals() =
        runTest {
            val viewModel = SignalViewModel()

            // Test with empty signals
            val highConfidence = viewModel.getHighConfidenceSignals(minConfidence = 0.8)
            assertTrue(highConfidence.isEmpty(), "Should return empty list when no signals loaded")
        }

    @Test
    fun testGetAverageConfidence() =
        runTest {
            val viewModel = SignalViewModel()

            // Test with empty signals - should return 0.0
            val avgConfidence = viewModel.getAverageConfidence()
            assertEquals(0.0, avgConfidence, "Average confidence should be 0.0 when no signals")
        }
}
