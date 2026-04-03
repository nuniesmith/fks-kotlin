package xyz.fkstrading.clients.domain.viewmodel

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for PortfolioViewModel
 */
class PortfolioViewModelTest {
    @Test
    fun testInitialState() =
        runTest {
            val viewModel = PortfolioViewModel()

            assertEquals(0.0, viewModel.portfolioValue.first(), "Portfolio value should be 0.0 initially")
            assertTrue(viewModel.assetPrices.first().isEmpty(), "Asset prices should be empty initially")
            assertFalse(viewModel.isLoading.first(), "Should not be loading initially")
            assertEquals(null, viewModel.error.first(), "Should have no error initially")
        }

    // Note: Full portfolio loading tests would require mocking PortfolioRepository
}
