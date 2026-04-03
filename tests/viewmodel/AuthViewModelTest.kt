package xyz.fkstrading.clients.domain.viewmodel

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * Tests for AuthViewModel
 */
class AuthViewModelTest {
    @Test
    fun testInitialState() =
        runTest {
            val viewModel = AuthViewModel()

            assertFalse(viewModel.isAuthenticated.first(), "Should not be authenticated initially")
            assertEquals(null, viewModel.userProfile.first(), "User profile should be null initially")
            assertFalse(viewModel.isLoading.first(), "Should not be loading initially")
            assertEquals(null, viewModel.error.first(), "Should have no error initially")
        }

    @Test
    fun testLogout_ClearsState() =
        runTest {
            val viewModel = AuthViewModel()

            // Simulate logged in state (in real test, would mock repository)
            viewModel.logout()

            assertFalse(viewModel.isAuthenticated.first(), "Should not be authenticated after logout")
            assertEquals(null, viewModel.userProfile.first(), "User profile should be null after logout")
        }

    // Note: Full login tests would require mocking AuthRepository
    // which would need a more sophisticated test setup with dependency injection
}
