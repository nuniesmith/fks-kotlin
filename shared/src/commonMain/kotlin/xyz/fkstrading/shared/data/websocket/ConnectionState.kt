package xyz.fkstrading.shared.data.websocket

/**
 * Represents the current state of the WebSocket connection.
 *
 * This sealed class hierarchy provides a type-safe way to manage and observe
 * the WebSocket connection lifecycle across all platforms.
 */
sealed class ConnectionState {
    /**
     * WebSocket is disconnected.
     * This is the initial state and the state after a manual disconnect.
     */
    data object Disconnected : ConnectionState()

    /**
     * WebSocket is attempting to establish a connection.
     * This state occurs when connect() is called but the connection hasn't been established yet.
     */
    data object Connecting : ConnectionState()

    /**
     * WebSocket is connected and ready to send/receive messages.
     * Messages can be sent and the connection is actively maintained with heartbeats.
     */
    data object Connected : ConnectionState()

    /**
     * WebSocket encountered an error.
     *
     * @param message Human-readable error description
     * @param error The underlying exception that caused the error (if available)
     * @param willRetry True if the auto-reconnect mechanism will attempt to reconnect
     */
    data class Error(
        val message: String,
        val error: Throwable? = null,
        val willRetry: Boolean = true,
    ) : ConnectionState()

    /**
     * Returns a human-readable string representation of the connection state.
     */
    override fun toString(): String =
        when (this) {
            is Disconnected -> "Disconnected"
            is Connecting -> "Connecting"
            is Connected -> "Connected"
            is Error -> "Error: $message (willRetry=$willRetry)"
        }

    /**
     * Returns true if the connection is in a state where messages can be sent.
     */
    fun canSendMessages(): Boolean = this is Connected

    /**
     * Returns true if the connection is active (either connected or connecting).
     */
    fun isActive(): Boolean = this is Connected || this is Connecting
}
