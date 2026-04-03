package xyz.fkstrading.shared.data.websocket

import io.ktor.client.*
import io.ktor.client.plugins.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import kotlin.time.Duration.Companion.seconds

/**
 * Ktor-based implementation of WebSocketClient.
 *
 * This implementation provides:
 * - Automatic reconnection with exponential backoff
 * - Connection state management with StateFlow
 * - Heartbeat mechanism (ping/pong) to detect stale connections
 * - Thread-safe message sending and receiving
 * - Proper resource cleanup and structured concurrency
 *
 * @param httpClient Configured HttpClient with WebSockets plugin installed
 * @param reconnectConfig Configuration for reconnection behavior
 */
class WebSocketClientImpl(
    private val httpClient: HttpClient,
    private val reconnectConfig: ReconnectConfig = ReconnectConfig(),
) : WebSocketClient {
    // Coroutine scope for managing WebSocket lifecycle
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Reconnection strategy manager
    private val reconnectStrategy = ReconnectStrategy(reconnectConfig)

    // Connection state exposed to observers
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    // Shared flow for incoming messages
    private val _incomingMessages =
        MutableSharedFlow<String>(
            replay = 0,
            extraBufferCapacity = 100,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )

    // Current WebSocket session
    private var currentSession: WebSocketSession? = null

    // Current connection URL
    private var currentUrl: String? = null

    // Flag to track manual disconnection
    private var manualDisconnect = false

    // Heartbeat job for ping/pong
    private var heartbeatJob: Job? = null

    // Connection job
    private var connectionJob: Job? = null

    override suspend fun connect(url: String): Result<Unit> =
        runCatching {
            // If already connected to the same URL, return success
            if (currentUrl == url && _connectionState.value is ConnectionState.Connected) {
                return Result.success(Unit)
            }

            // If connecting to a different URL, disconnect first
            if (currentUrl != null && currentUrl != url) {
                disconnect()
            }

            currentUrl = url
            manualDisconnect = false
            reconnectStrategy.reset()

            // Start connection in background
            connectionJob =
                scope.launch {
                    connectInternal(url)
                }
        }

    /**
     * Internal connection logic with reconnection support.
     */
    private suspend fun connectInternal(url: String) {
        _connectionState.value = ConnectionState.Connecting

        try {
            httpClient.webSocket(url) {
                currentSession = this
                _connectionState.value = ConnectionState.Connected
                reconnectStrategy.reset()

                println("[WebSocket] Connected to $url")

                // Start heartbeat mechanism
                startHeartbeat()

                try {
                    // Listen for incoming frames
                    for (frame in incoming) {
                        when (frame) {
                            is Frame.Text -> {
                                val message = frame.readText()
                                _incomingMessages.emit(message)
                            }
                            is Frame.Ping -> {
                                // Respond to server ping with pong
                                send(Frame.Pong(frame.data))
                            }
                            is Frame.Pong -> {
                                // Server responded to our ping
                                println("[WebSocket] Received pong")
                            }
                            is Frame.Close -> {
                                val reason = frame.readReason()
                                println("[WebSocket] Connection closed: ${reason?.message}")
                                break
                            }
                            else -> {
                                // Ignore binary frames and other types
                            }
                        }
                    }
                } catch (e: Exception) {
                    println("[WebSocket] Error receiving messages: ${e.message}")
                    throw e
                }
            }
        } catch (e: Exception) {
            handleConnectionError(e)
        } finally {
            // Cleanup
            currentSession = null
            heartbeatJob?.cancel()
            heartbeatJob = null

            // Auto-reconnect if not manually disconnected
            if (!manualDisconnect && reconnectStrategy.shouldRetry()) {
                scheduleReconnect()
            } else if (!manualDisconnect && reconnectStrategy.isMaxAttemptsReached()) {
                _connectionState.value =
                    ConnectionState.Error(
                        message = "Max reconnection attempts (${reconnectStrategy.getMaxAttempts()}) reached",
                        error = null,
                        willRetry = false,
                    )
            } else if (manualDisconnect) {
                _connectionState.value = ConnectionState.Disconnected
            }
        }
    }

    /**
     * Handle connection errors and update state accordingly.
     */
    private fun handleConnectionError(error: Throwable) {
        val willRetry = !manualDisconnect && reconnectStrategy.shouldRetry()
        _connectionState.value =
            ConnectionState.Error(
                message = error.message ?: "Connection error",
                error = error,
                willRetry = willRetry,
            )
        println("[WebSocket] Connection error: ${error.message}, willRetry=$willRetry")
    }

    /**
     * Schedule a reconnection attempt with exponential backoff.
     */
    private fun scheduleReconnect() {
        reconnectStrategy.recordAttempt()
        val delay = reconnectStrategy.getNextDelay()

        println("[WebSocket] Scheduling reconnect in ${delay}ms (${reconnectStrategy.getStatusString()})")

        scope.launch {
            delay(delay)
            if (!manualDisconnect) {
                currentUrl?.let { url ->
                    println("[WebSocket] Attempting reconnect to $url")
                    connectInternal(url)
                }
            }
        }
    }

    /**
     * Start heartbeat mechanism to keep connection alive and detect stale connections.
     */
    private fun startHeartbeat() {
        heartbeatJob =
            scope.launch {
                while (isActive && !manualDisconnect) {
                    delay(30.seconds)
                    try {
                        currentSession?.send(Frame.Ping(byteArrayOf()))
                        println("[WebSocket] Sent ping")
                    } catch (e: Exception) {
                        println("[WebSocket] Heartbeat failed: ${e.message}")
                        // Cancel heartbeat, connection will be handled by the main loop
                        cancel()
                    }
                }
            }
    }

    override suspend fun disconnect() {
        println("[WebSocket] Manual disconnect requested")
        manualDisconnect = true

        // Cancel any ongoing connection attempts
        connectionJob?.cancel()
        connectionJob = null

        // Stop heartbeat
        heartbeatJob?.cancel()
        heartbeatJob = null

        // Close the WebSocket session
        try {
            currentSession?.close(CloseReason(CloseReason.Codes.NORMAL, "Client disconnect"))
        } catch (e: Exception) {
            println("[WebSocket] Error during disconnect: ${e.message}")
        }

        currentSession = null
        currentUrl = null
        _connectionState.value = ConnectionState.Disconnected
    }

    override suspend fun sendMessage(message: String): Result<Unit> =
        runCatching {
            val session =
                currentSession
                    ?: throw IllegalStateException("WebSocket not connected. Current state: ${_connectionState.value}")

            if (_connectionState.value !is ConnectionState.Connected) {
                throw IllegalStateException("Cannot send message in state: ${_connectionState.value}")
            }

            session.send(Frame.Text(message))
            println("[WebSocket] Sent message: ${message.take(100)}${if (message.length > 100) "..." else ""}")
        }

    override fun observeMessages(): Flow<String> = _incomingMessages.asSharedFlow()

    override fun getCurrentUrl(): String? = currentUrl

    /**
     * Cleanup resources when the client is no longer needed.
     * This should be called when the client is being destroyed.
     */
    fun close() {
        scope.cancel()
        // Launch disconnect in the remaining scope before cancellation completes
        scope.launch {
            disconnect()
        }
    }
}
