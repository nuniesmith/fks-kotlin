package xyz.fkstrading.client.features.realtime

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import xyz.fkstrading.client.config.AppConfig
import xyz.fkstrading.shared.data.bridge.WebSocketRepositoryBridge
import xyz.fkstrading.shared.data.repository.SignalRepository
import xyz.fkstrading.shared.data.sync.SyncEngine
import xyz.fkstrading.shared.data.sync.SyncState
import xyz.fkstrading.shared.data.websocket.ConnectionState
import xyz.fkstrading.shared.data.websocket.SubscriptionManager
import xyz.fkstrading.shared.data.websocket.WebSocketClient
import xyz.fkstrading.shared.data.websocket.WebSocketDataStream
import xyz.fkstrading.shared.domain.models.Signal

/**
 * ViewModel for Real-Time Signals Screen
 *
 * Manages WebSocket connection, channel subscriptions, and real-time signal updates.
 * Integrates with offline-first repository pattern for automatic persistence.
 * Exposes UI state as StateFlow for Compose consumption.
 */
class RealTimeSignalsViewModel(
    private val webSocketClient: WebSocketClient,
    private val dataStream: WebSocketDataStream,
    private val subscriptionManager: SubscriptionManager,
    private val signalRepository: SignalRepository,
    private val bridge: WebSocketRepositoryBridge,
    private val syncEngine: SyncEngine,
) {
    private val viewModelScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Connection state from WebSocket client
    val connectionState: StateFlow<ConnectionState> = webSocketClient.connectionState

    // Active subscriptions from SubscriptionManager
    val activeSubscriptions: StateFlow<Set<String>> = subscriptionManager.activeSubscriptions

    // Signals from repository (includes offline cache + real-time updates)
    val signals: StateFlow<List<Signal>> =
        signalRepository
            .observeRecentSignals(limit = AppConfig.UI.MAX_SIGNALS_DISPLAYED)
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // Sync state
    val syncState: StateFlow<SyncState> = syncEngine.syncState

    // UI state
    private val _uiState = MutableStateFlow(RealTimeUiState())
    val uiState: StateFlow<RealTimeUiState> = _uiState.asStateFlow()

    // Statistics
    private val _statistics = MutableStateFlow(ConnectionStatistics())
    val statistics: StateFlow<ConnectionStatistics> = _statistics.asStateFlow()

    init {
        // Start the bridge to auto-save WebSocket data to repository
        bridge.start()

        // Start sync engine for background synchronization
        syncEngine.start()

        trackConnectionStatistics()
    }

    /**
     * Connect to WebSocket server
     */
    fun connect() {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(error = null) }
                webSocketClient.connect(AppConfig.WebSocket.DEFAULT_URL)
                // Bridge will automatically save incoming data to repository
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Failed to connect: ${e.message}") }
            }
        }
    }

    /**
     * Disconnect from WebSocket server
     * Note: Cached data remains available offline
     */
    fun disconnect() {
        viewModelScope.launch {
            try {
                webSocketClient.disconnect()
                _statistics.value = ConnectionStatistics()
                // Signals remain in repository for offline access
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Failed to disconnect: ${e.message}") }
            }
        }
    }

    /**
     * Subscribe to a channel
     */
    fun subscribe(channel: String) {
        viewModelScope.launch {
            try {
                subscriptionManager.subscribe(channel)
                _uiState.update { it.copy(error = null) }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Failed to subscribe to $channel: ${e.message}") }
            }
        }
    }

    /**
     * Unsubscribe from a channel
     */
    fun unsubscribe(channel: String) {
        viewModelScope.launch {
            try {
                subscriptionManager.unsubscribe(channel)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Failed to unsubscribe from $channel: ${e.message}") }
            }
        }
    }

    /**
     * Toggle subscription for a channel
     */
    fun toggleSubscription(channel: String) {
        viewModelScope.launch {
            if (subscriptionManager.isSubscribed(channel)) {
                unsubscribe(channel)
            } else {
                subscribe(channel)
            }
        }
    }

    /**
     * Clear all signals from the repository
     */
    fun clearSignals() {
        viewModelScope.launch {
            try {
                signalRepository.clearAll()
                _statistics.update { it.copy(messagesReceived = 0) }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Failed to clear signals: ${e.message}") }
            }
        }
    }

    /**
     * Manually trigger sync with server
     */
    fun syncNow() {
        viewModelScope.launch {
            try {
                val success = syncEngine.sync()
                if (!success) {
                    _uiState.update { it.copy(error = "Sync failed - check connection") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Sync error: ${e.message}") }
            }
        }
    }

    /**
     * Refresh data from server
     */
    fun refreshData() {
        viewModelScope.launch {
            try {
                val success = syncEngine.refresh()
                if (!success) {
                    _uiState.update { it.copy(error = "Refresh failed - check connection") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Refresh error: ${e.message}") }
            }
        }
    }

    /**
     * Dismiss error message
     */
    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }

    /**
     * Track incoming messages for statistics
     */
    private fun trackIncomingMessages() {
        viewModelScope.launch {
            dataStream.signalsFlow.collect { _ ->
                _statistics.update { it.copy(messagesReceived = it.messagesReceived + 1) }
            }
        }
    }

    /**
     * Track connection statistics
     */
    private fun trackConnectionStatistics() {
        viewModelScope.launch {
            var connectionStartTime: Long? = null

            connectionState.collect { state ->
                when (state) {
                    is ConnectionState.Connected -> {
                        if (connectionStartTime == null) {
                            connectionStartTime = System.currentTimeMillis()
                            startUptimeCounter(connectionStartTime!!)
                        }
                    }
                    is ConnectionState.Disconnected -> {
                        connectionStartTime = null
                        _statistics.update { it.copy(uptimeSeconds = 0) }
                    }
                    else -> { /* Connecting/Reconnecting - do nothing */ }
                }
            }
        }
    }

    /**
     * Update connection uptime counter
     */
    private fun startUptimeCounter(startTime: Long) {
        viewModelScope.launch {
            while (connectionState.value is ConnectionState.Connected) {
                val uptime = (System.currentTimeMillis() - startTime) / 1000
                _statistics.update { it.copy(uptimeSeconds = uptime) }
                delay(1000) // Update every second
            }
        }
    }

    /**
     * Cleanup when ViewModel is cleared
     */
    fun onCleared() {
        bridge.stop()
        syncEngine.stop()
        viewModelScope.cancel()
    }
}

/**
 * UI state for the Real-Time screen
 */
data class RealTimeUiState(
    val error: String? = null,
    val autoScroll: Boolean = AppConfig.UI.AUTO_SCROLL_ENABLED,
    val isOffline: Boolean = false,
)

/**
 * Connection statistics
 */
data class ConnectionStatistics(
    val messagesReceived: Int = 0,
    val uptimeSeconds: Long = 0,
)

/**
 * Available WebSocket channels
 */
object Channels {
    const val SIGNALS = "signals"
    const val ORDERS = "orders"
    const val POSITIONS = "positions"
    const val MARKET_DATA = "market_data"

    val ALL = listOf(SIGNALS, ORDERS, POSITIONS, MARKET_DATA)
}
