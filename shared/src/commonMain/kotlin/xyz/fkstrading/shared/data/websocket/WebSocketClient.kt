package xyz.fkstrading.shared.data.websocket

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * WebSocket client interface for real-time communication with the FKS backend.
 *
 * This interface defines the contract for WebSocket operations including
 * connection management, message sending/receiving, and connection state observation.
 *
 * Implementations should provide:
 * - Automatic reconnection with exponential backoff
 * - Connection state management
 * - Heartbeat/ping mechanism to detect stale connections
 * - Thread-safe message sending and receiving
 * - Proper resource cleanup
 *
 * Example usage:
 * ```
 * val client: WebSocketClient = get() // from DI
 *
 * // Observe connection state
 * client.connectionState.collect { state ->
 *     when (state) {
 *         is ConnectionState.Connected -> println("Connected!")
 *         is ConnectionState.Error -> println("Error: ${state.message}")
 *         else -> println("State: $state")
 *     }
 * }
 *
 * // Connect to endpoint
 * client.connect("ws://localhost:8000/ws/signals")
 *     .onSuccess { println("Connection established") }
 *     .onFailure { error -> println("Failed to connect: $error") }
 *
 * // Observe incoming messages
 * client.observeMessages().collect { message ->
 *     println("Received: $message")
 * }
 *
 * // Send a message
 * client.sendMessage("""{"action":"subscribe","channel":"signals"}""")
 *     .onSuccess { println("Message sent") }
 *     .onFailure { error -> println("Failed to send: $error") }
 *
 * // Disconnect when done
 * client.disconnect()
 * ```
 */
interface WebSocketClient {
    /**
     * Current connection state as a hot StateFlow.
     *
     * Subscribers will immediately receive the current state and all subsequent
     * state changes. The state is updated whenever the connection status changes.
     *
     * States flow in this order:
     * Disconnected -> Connecting -> Connected -> (Error/Disconnected)
     *
     * @see ConnectionState
     */
    val connectionState: StateFlow<ConnectionState>

    /**
     * Connect to the WebSocket server.
     *
     * This method initiates a WebSocket connection to the specified URL.
     * The connection process is asynchronous - use the [connectionState] flow
     * to observe when the connection is established.
     *
     * If already connected or connecting, this method may:
     * - Return success if already connected to the same URL
     * - Disconnect and reconnect if connecting to a different URL
     * - Return failure if already in the process of connecting
     *
     * After a successful connection, the client will:
     * - Automatically send heartbeat pings
     * - Auto-reconnect on disconnection (unless manually disconnected)
     * - Update [connectionState] to Connected
     *
     * @param url WebSocket endpoint URL (e.g., "ws://localhost:8000/ws/signals")
     *            Must use "ws://" or "wss://" protocol
     * @return Result indicating success or failure of the connection attempt.
     *         Success doesn't guarantee the connection is established immediately,
     *         observe [connectionState] for the actual connection status.
     */
    suspend fun connect(url: String): Result<Unit>

    /**
     * Disconnect from the WebSocket server.
     *
     * This method performs a graceful disconnection from the WebSocket server.
     * It will:
     * - Cancel any pending auto-reconnect attempts
     * - Stop the heartbeat mechanism
     * - Close the WebSocket connection cleanly
     * - Update [connectionState] to Disconnected
     *
     * This is a suspending function that completes when the disconnection is done.
     * After calling disconnect(), the client will NOT automatically reconnect.
     * Call [connect] again to re-establish the connection.
     */
    suspend fun disconnect()

    /**
     * Send a text message to the server.
     *
     * Messages are typically JSON strings containing commands or data.
     * The message will only be sent if the connection is in the Connected state.
     *
     * Example messages:
     * - Subscribe: `{"action":"subscribe","channel":"signals"}`
     * - Unsubscribe: `{"action":"unsubscribe","channel":"signals"}`
     * - Custom command: `{"action":"get_history","symbol":"BTC/USD"}`
     *
     * @param message The text message to send (typically JSON)
     * @return Result indicating success or failure.
     *         Failure reasons include:
     *         - Not connected (state is not Connected)
     *         - Network error during send
     *         - Message encoding error
     *
     * @throws IllegalStateException if called when not connected (wrapped in Result)
     */
    suspend fun sendMessage(message: String): Result<Unit>

    /**
     * Observe incoming messages from the server.
     *
     * Returns a cold Flow that emits text messages received from the WebSocket.
     * Each collector will receive messages independently.
     *
     * The flow will:
     * - Emit messages as they arrive from the server
     * - Continue emitting across reconnections
     * - Complete when the client is permanently disconnected
     * - Emit errors if message processing fails
     *
     * Messages are typically JSON strings that need to be parsed by the caller.
     *
     * Example:
     * ```
     * client.observeMessages()
     *     .catch { error -> println("Error: $error") }
     *     .collect { message ->
     *         val parsed = Json.decodeFromString<WebSocketMessage>(message)
     *         // Process parsed message
     *     }
     * ```
     *
     * @return Flow of incoming text messages
     */
    fun observeMessages(): Flow<String>

    /**
     * Check if the client is currently connected.
     *
     * This is a convenience method equivalent to:
     * `connectionState.value is ConnectionState.Connected`
     *
     * @return True if connected, false otherwise
     */
    fun isConnected(): Boolean = connectionState.value is ConnectionState.Connected

    /**
     * Get the current connection URL.
     *
     * @return The WebSocket URL currently connected to, or null if not connected
     */
    fun getCurrentUrl(): String?
}
