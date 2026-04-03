package xyz.fkstrading.shared.data.websocket

import kotlin.test.*

/**
 * Unit tests for ConnectionState sealed class.
 *
 * Tests state behavior, helper methods, and string representations.
 */
class ConnectionStateTest {
    @Test
    fun testDisconnectedState() {
        val state = ConnectionState.Disconnected

        assertFalse(state.canSendMessages(), "Disconnected state should not allow sending messages")
        assertFalse(state.isActive(), "Disconnected state should not be active")
        assertEquals("Disconnected", state.toString())
    }

    @Test
    fun testConnectingState() {
        val state = ConnectionState.Connecting

        assertFalse(state.canSendMessages(), "Connecting state should not allow sending messages")
        assertTrue(state.isActive(), "Connecting state should be active")
        assertEquals("Connecting", state.toString())
    }

    @Test
    fun testConnectedState() {
        val state = ConnectionState.Connected

        assertTrue(state.canSendMessages(), "Connected state should allow sending messages")
        assertTrue(state.isActive(), "Connected state should be active")
        assertEquals("Connected", state.toString())
    }

    @Test
    fun testErrorStateWithRetry() {
        val error = Exception("Connection failed")
        val state =
            ConnectionState.Error(
                message = "Network error",
                error = error,
                willRetry = true,
            )

        assertFalse(state.canSendMessages(), "Error state should not allow sending messages")
        assertFalse(state.isActive(), "Error state should not be active")
        assertEquals("Network error", state.message)
        assertEquals(error, state.error)
        assertTrue(state.willRetry)
        assertTrue(state.toString().contains("Network error"))
        assertTrue(state.toString().contains("willRetry=true"))
    }

    @Test
    fun testErrorStateWithoutRetry() {
        val state =
            ConnectionState.Error(
                message = "Max attempts reached",
                error = null,
                willRetry = false,
            )

        assertEquals("Max attempts reached", state.message)
        assertNull(state.error)
        assertFalse(state.willRetry)
        assertTrue(state.toString().contains("willRetry=false"))
    }

    @Test
    fun testErrorStateDefaultValues() {
        val state = ConnectionState.Error(message = "Test error")

        assertEquals("Test error", state.message)
        assertNull(state.error, "Error should default to null")
        assertTrue(state.willRetry, "willRetry should default to true")
    }

    @Test
    fun testStateTransitionSequence() {
        val states =
            listOf(
                ConnectionState.Disconnected,
                ConnectionState.Connecting,
                ConnectionState.Connected,
                ConnectionState.Disconnected,
            )

        // Verify typical state transition sequence
        assertTrue(states[0] is ConnectionState.Disconnected)
        assertTrue(states[1] is ConnectionState.Connecting)
        assertTrue(states[2] is ConnectionState.Connected)
        assertTrue(states[3] is ConnectionState.Disconnected)
    }

    @Test
    fun testStateEquality() {
        // Data objects should be equal
        assertEquals(ConnectionState.Disconnected, ConnectionState.Disconnected)
        assertEquals(ConnectionState.Connecting, ConnectionState.Connecting)
        assertEquals(ConnectionState.Connected, ConnectionState.Connected)

        // Error states with same values should be equal
        val error1 = ConnectionState.Error("Test", null, true)
        val error2 = ConnectionState.Error("Test", null, true)
        assertEquals(error1, error2)

        // Error states with different values should not be equal
        val error3 = ConnectionState.Error("Different", null, true)
        assertNotEquals(error1, error3)
    }

    @Test
    fun testStateInequality() {
        assertNotEquals<ConnectionState>(ConnectionState.Disconnected, ConnectionState.Connecting)
        assertNotEquals<ConnectionState>(ConnectionState.Connecting, ConnectionState.Connected)
        assertNotEquals<ConnectionState>(ConnectionState.Connected, ConnectionState.Disconnected)
    }

    @Test
    fun testCanSendMessagesForAllStates() {
        val states =
            listOf(
                ConnectionState.Disconnected,
                ConnectionState.Connecting,
                ConnectionState.Connected,
                ConnectionState.Error("Test", null, true),
            )

        val expectedResults = listOf(false, false, true, false)

        states.forEachIndexed { index, state ->
            assertEquals(
                expectedResults[index],
                state.canSendMessages(),
                "State $state should have canSendMessages=${expectedResults[index]}",
            )
        }
    }

    @Test
    fun testIsActiveForAllStates() {
        val states =
            listOf(
                ConnectionState.Disconnected,
                ConnectionState.Connecting,
                ConnectionState.Connected,
                ConnectionState.Error("Test", null, true),
            )

        val expectedResults = listOf(false, true, true, false)

        states.forEachIndexed { index, state ->
            assertEquals(
                expectedResults[index],
                state.isActive(),
                "State $state should have isActive=${expectedResults[index]}",
            )
        }
    }

    @Test
    fun testToStringForAllStates() {
        val disconnected = ConnectionState.Disconnected
        val connecting = ConnectionState.Connecting
        val connected = ConnectionState.Connected
        val error = ConnectionState.Error("Network timeout", null, true)

        assertEquals("Disconnected", disconnected.toString())
        assertEquals("Connecting", connecting.toString())
        assertEquals("Connected", connected.toString())
        assertTrue(error.toString().contains("Network timeout"))
    }

    @Test
    fun testErrorStateWithDetailedMessage() {
        val exception = RuntimeException("Socket closed unexpectedly")
        val state =
            ConnectionState.Error(
                message = "Connection lost to ws://localhost:8000",
                error = exception,
                willRetry = true,
            )

        assertEquals("Connection lost to ws://localhost:8000", state.message)
        assertEquals(exception, state.error)
        assertEquals("Socket closed unexpectedly", state.error?.message)
        assertTrue(state.willRetry)
    }

    @Test
    fun testStateTypeChecking() {
        val state: ConnectionState = ConnectionState.Connected

        // Type checking should work correctly
        when (state) {
            is ConnectionState.Disconnected -> fail("Should not be Disconnected")
            is ConnectionState.Connecting -> fail("Should not be Connecting")
            is ConnectionState.Connected -> assertTrue(true, "Should be Connected")
            is ConnectionState.Error -> fail("Should not be Error")
        }
    }

    @Test
    fun testWhenExpressionExhaustiveness() {
        val states: List<ConnectionState> =
            listOf(
                ConnectionState.Disconnected,
                ConnectionState.Connecting,
                ConnectionState.Connected,
                ConnectionState.Error("Test", null, false),
            )

        states.forEach { state ->
            // This when expression must be exhaustive
            val description =
                when (state) {
                    is ConnectionState.Disconnected -> "Not connected"
                    is ConnectionState.Connecting -> "Attempting connection"
                    is ConnectionState.Connected -> "Connection established"
                    is ConnectionState.Error -> "Connection failed: ${state.message}"
                }

            assertNotNull(description)
        }
    }

    @Test
    fun testErrorStateCopy() {
        val original = ConnectionState.Error("Original", null, true)
        val modified = original.copy(message = "Modified")

        assertEquals("Original", original.message)
        assertEquals("Modified", modified.message)
        assertTrue(modified.willRetry)
    }

    @Test
    fun testMultipleErrorInstances() {
        val errors =
            listOf(
                ConnectionState.Error("Error 1", null, true),
                ConnectionState.Error("Error 2", null, false),
                ConnectionState.Error("Error 3", RuntimeException("Test"), true),
            )

        assertEquals(3, errors.size)
        assertEquals("Error 1", errors[0].message)
        assertEquals("Error 2", errors[1].message)
        assertFalse(errors[1].willRetry)
        assertNotNull(errors[2].error)
    }

    @Test
    fun testStateAsFlowValue() {
        // Simulate usage in StateFlow
        val stateSequence = mutableListOf<ConnectionState>()

        stateSequence.add(ConnectionState.Disconnected)
        stateSequence.add(ConnectionState.Connecting)
        stateSequence.add(ConnectionState.Connected)
        stateSequence.add(ConnectionState.Error("Network error", null, true))
        stateSequence.add(ConnectionState.Disconnected)

        assertEquals(5, stateSequence.size)
        assertTrue(stateSequence[0] is ConnectionState.Disconnected)
        assertTrue(stateSequence[2] is ConnectionState.Connected)
        assertTrue(stateSequence[3] is ConnectionState.Error)
    }

    @Test
    fun testHashCodeConsistency() {
        val state1 = ConnectionState.Disconnected
        val state2 = ConnectionState.Disconnected

        assertEquals(state1.hashCode(), state2.hashCode())

        val error1 = ConnectionState.Error("Test", null, true)
        val error2 = ConnectionState.Error("Test", null, true)

        assertEquals(error1.hashCode(), error2.hashCode())
    }
}
