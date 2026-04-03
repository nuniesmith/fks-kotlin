package xyz.fkstrading.clients.domain.viewmodel

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for WebSocketViewModel
 */
class WebSocketViewModelTest {
    @Test
    fun testInitialState() =
        runTest {
            val viewModel = WebSocketViewModel()

            assertFalse(viewModel.isConnected.first(), "Should not be connected initially")
            assertEquals(null, viewModel.latestSignal.first(), "Latest signal should be null initially")
            assertTrue(viewModel.signalUpdates.first().isEmpty(), "Should have no signal updates initially")
            assertEquals(null, viewModel.error.first(), "Should have no error initially")
        }

    @Test
    fun testDisconnect() =
        runTest {
            val viewModel = WebSocketViewModel()

            // Disconnect should work even if not connected
            viewModel.disconnect()

            assertFalse(viewModel.isConnected.first(), "Should not be connected after disconnect")
        }

    @Test
    fun testClearUpdates() =
        runTest {
            val viewModel = WebSocketViewModel()

            viewModel.clearUpdates()

            assertTrue(viewModel.signalUpdates.first().isEmpty(), "Signal updates should be empty after clearing")
            assertEquals(null, viewModel.latestSignal.first(), "Latest signal should be null after clearing")
        }

    @Test
    fun testGetUpdateCount() =
        runTest {
            val viewModel = WebSocketViewModel()

            val count = viewModel.getUpdateCount()
            assertEquals(0, count, "Update count should be 0 initially")
        }
}
