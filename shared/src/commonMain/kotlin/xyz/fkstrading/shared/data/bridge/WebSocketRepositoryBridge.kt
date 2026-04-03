package xyz.fkstrading.shared.data.bridge

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import xyz.fkstrading.shared.data.repository.OrderRepository
import xyz.fkstrading.shared.data.repository.PositionRepository
import xyz.fkstrading.shared.data.repository.SignalRepository
import xyz.fkstrading.shared.data.websocket.WebSocketDataStream
import xyz.fkstrading.shared.domain.models.Order
import xyz.fkstrading.shared.domain.models.Position
import xyz.fkstrading.shared.domain.models.Signal
import xyz.fkstrading.shared.domain.models.calculateUnrealizedPnL

/**
 * Bridge between WebSocket real-time data and local repositories
 *
 * Automatically persists WebSocket updates to local database for offline access.
 * Handles deduplication and ensures data consistency between real-time and cached data.
 *
 * Usage:
 * ```kotlin
 * val bridge = WebSocketRepositoryBridge(
 *     dataStream = webSocketDataStream,
 *     signalRepository = signalRepository,
 *     orderRepository = orderRepository,
 *     positionRepository = positionRepository,
 *     scope = coroutineScope
 * )
 *
 * // Start bridging
 * bridge.start()
 *
 * // Stop when done
 * bridge.stop()
 * ```
 */
class WebSocketRepositoryBridge(
    private val dataStream: WebSocketDataStream,
    private val signalRepository: SignalRepository,
    private val orderRepository: OrderRepository,
    private val positionRepository: PositionRepository,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob()),
) {
    private var signalsJob: Job? = null
    private var ordersJob: Job? = null
    private var positionsJob: Job? = null
    private var marketDataJob: Job? = null

    private val _isActive = MutableStateFlow(false)
    val isActive: StateFlow<Boolean> = _isActive.asStateFlow()

    private val _stats = MutableStateFlow(BridgeStats())
    val stats: StateFlow<BridgeStats> = _stats.asStateFlow()

    // Deduplication cache to prevent duplicate saves
    private val recentSignalIds = mutableSetOf<String>()
    private val recentOrderIds = mutableSetOf<String>()
    private val recentPositionIds = mutableSetOf<String>()

    private val maxCacheSize = 1000

    /**
     * Starts the bridge - begins listening to WebSocket data and saving to repositories
     */
    fun start() {
        if (_isActive.value) {
            println("WebSocketRepositoryBridge: Already active")
            return
        }

        println("WebSocketRepositoryBridge: Starting...")
        _isActive.value = true

        // Bridge signals
        signalsJob =
            scope.launch {
                dataStream.signalsFlow
                    .collect { signal ->
                        try {
                            saveSignal(signal)
                        } catch (e: Exception) {
                            println("WebSocketRepositoryBridge: Error processing signal: ${e.message}")
                            _stats.value = _stats.value.copy(signalErrors = _stats.value.signalErrors + 1)
                        }
                    }
            }

        // Bridge orders
        ordersJob =
            scope.launch {
                dataStream.ordersFlow
                    .collect { order ->
                        try {
                            saveOrder(order)
                        } catch (e: Exception) {
                            println("WebSocketRepositoryBridge: Error processing order: ${e.message}")
                            _stats.value = _stats.value.copy(orderErrors = _stats.value.orderErrors + 1)
                        }
                    }
            }

        // Bridge positions
        positionsJob =
            scope.launch {
                dataStream.positionsFlow
                    .collect { position ->
                        try {
                            savePosition(position)
                        } catch (e: Exception) {
                            println("WebSocketRepositoryBridge: Error processing position: ${e.message}")
                            _stats.value = _stats.value.copy(positionErrors = _stats.value.positionErrors + 1)
                        }
                    }
            }

        // Bridge market data (for updating position prices)
        marketDataJob =
            scope.launch {
                dataStream.marketDataFlow
                    .collect { marketData ->
                        try {
                            updatePositionPrices(marketData.symbol, marketData.last)
                        } catch (e: Exception) {
                            println("WebSocketRepositoryBridge: Error updating position prices: ${e.message}")
                        }
                    }
            }

        println("WebSocketRepositoryBridge: Started successfully")
    }

    /**
     * Stops the bridge - stops listening to WebSocket data
     */
    fun stop() {
        if (!_isActive.value) {
            println("WebSocketRepositoryBridge: Already stopped")
            return
        }

        println("WebSocketRepositoryBridge: Stopping...")
        _isActive.value = false

        signalsJob?.cancel()
        ordersJob?.cancel()
        positionsJob?.cancel()
        marketDataJob?.cancel()

        signalsJob = null
        ordersJob = null
        positionsJob = null
        marketDataJob = null

        // Clear deduplication caches
        recentSignalIds.clear()
        recentOrderIds.clear()
        recentPositionIds.clear()

        println("WebSocketRepositoryBridge: Stopped")
    }

    /**
     * Restarts the bridge (stop then start)
     */
    fun restart() {
        stop()
        start()
    }

    /**
     * Checks if this is a duplicate signal (already processed recently)
     */
    private fun isDuplicateSignal(signalId: String): Boolean {
        return signalId in recentSignalIds
    }

    /**
     * Checks if this is a duplicate order
     */
    private fun isDuplicateOrder(orderId: String): Boolean {
        return orderId in recentOrderIds
    }

    /**
     * Checks if this is a duplicate position
     */
    private fun isDuplicatePosition(positionId: String): Boolean {
        return positionId in recentPositionIds
    }

    /**
     * Saves a signal to the repository with deduplication
     */
    private suspend fun saveSignal(signal: Signal) {
        try {
            // Check for duplicates
            if (isDuplicateSignal(signal.signalId)) {
                return
            }

            // Save to repository
            signalRepository.saveSignal(signal)

            // Update deduplication cache
            recentSignalIds.add(signal.signalId)
            if (recentSignalIds.size > maxCacheSize) {
                // Remove oldest entries (simple FIFO)
                val toRemove = recentSignalIds.take(100)
                recentSignalIds.removeAll(toRemove.toSet())
            }

            // Update stats
            _stats.value =
                _stats.value.copy(
                    signalsSaved = _stats.value.signalsSaved + 1,
                    lastSignalTime = kotlinx.datetime.Clock.System.now().toEpochMilliseconds(),
                )

            println("WebSocketRepositoryBridge: Saved signal ${signal.signalId}")
        } catch (e: Exception) {
            println("WebSocketRepositoryBridge: Failed to save signal ${signal.signalId}: ${e.message}")
            _stats.value = _stats.value.copy(signalErrors = _stats.value.signalErrors + 1)
        }
    }

    /**
     * Saves an order to the repository with deduplication
     */
    private suspend fun saveOrder(order: Order) {
        try {
            // Check for duplicates
            if (isDuplicateOrder(order.orderId)) {
                // For orders, we always update since status might have changed
                orderRepository.saveOrder(order)
                println("WebSocketRepositoryBridge: Updated order ${order.orderId}")
                return
            }

            // Save to repository
            orderRepository.saveOrder(order)

            // Update deduplication cache
            recentOrderIds.add(order.orderId)
            if (recentOrderIds.size > maxCacheSize) {
                val toRemove = recentOrderIds.take(100)
                recentOrderIds.removeAll(toRemove.toSet())
            }

            // Update stats
            _stats.value =
                _stats.value.copy(
                    ordersSaved = _stats.value.ordersSaved + 1,
                    lastOrderTime = kotlinx.datetime.Clock.System.now().toEpochMilliseconds(),
                )

            println("WebSocketRepositoryBridge: Saved order ${order.orderId}")
        } catch (e: Exception) {
            println("WebSocketRepositoryBridge: Failed to save order ${order.orderId}: ${e.message}")
            _stats.value = _stats.value.copy(orderErrors = _stats.value.orderErrors + 1)
        }
    }

    /**
     * Saves a position to the repository with deduplication
     */
    private suspend fun savePosition(position: Position) {
        try {
            // Check for duplicates
            if (isDuplicatePosition(position.positionId)) {
                // For positions, we always update since P&L might have changed
                positionRepository.savePosition(position)
                println("WebSocketRepositoryBridge: Updated position ${position.positionId}")
                return
            }

            // Save to repository
            positionRepository.savePosition(position)

            // Update deduplication cache
            recentPositionIds.add(position.positionId)
            if (recentPositionIds.size > maxCacheSize) {
                val toRemove = recentPositionIds.take(100)
                recentPositionIds.removeAll(toRemove.toSet())
            }

            // Update stats
            _stats.value =
                _stats.value.copy(
                    positionsSaved = _stats.value.positionsSaved + 1,
                    lastPositionTime = kotlinx.datetime.Clock.System.now().toEpochMilliseconds(),
                )

            println("WebSocketRepositoryBridge: Saved position ${position.positionId}")
        } catch (e: Exception) {
            println("WebSocketRepositoryBridge: Failed to save position ${position.positionId}: ${e.message}")
            _stats.value = _stats.value.copy(positionErrors = _stats.value.positionErrors + 1)
        }
    }

    /**
     * Updates position prices based on market data
     */
    private suspend fun updatePositionPrices(
        symbol: String,
        price: Double,
    ) {
        try {
            // Get all open positions for this symbol
            val positions =
                positionRepository.getOpenPositions()
                    .filter { it.symbol == symbol }

            // Update each position's current price and P&L
            positions.forEach { position ->
                val unrealizedPnL = position.calculateUnrealizedPnL(price)
                positionRepository.updatePositionPrice(
                    positionId = position.positionId,
                    currentPrice = price,
                    unrealizedPnL = unrealizedPnL,
                )
            }

            if (positions.isNotEmpty()) {
                println("WebSocketRepositoryBridge: Updated ${positions.size} positions for $symbol @ $price")
            }
        } catch (e: Exception) {
            println("WebSocketRepositoryBridge: Failed to update position prices for $symbol: ${e.message}")
        }
    }

    /**
     * Clears all deduplication caches
     */
    fun clearCaches() {
        recentSignalIds.clear()
        recentOrderIds.clear()
        recentPositionIds.clear()
        println("WebSocketRepositoryBridge: Cleared deduplication caches")
    }

    /**
     * Resets statistics
     */
    fun resetStats() {
        _stats.value = BridgeStats()
        println("WebSocketRepositoryBridge: Reset statistics")
    }

    /**
     * Cleanup resources
     */
    fun shutdown() {
        stop()
        scope.cancel()
        println("WebSocketRepositoryBridge: Shutdown complete")
    }
}

/**
 * Statistics about the bridge's operation
 */
data class BridgeStats(
    val signalsSaved: Long = 0,
    val ordersSaved: Long = 0,
    val positionsSaved: Long = 0,
    val signalErrors: Long = 0,
    val orderErrors: Long = 0,
    val positionErrors: Long = 0,
    val lastSignalTime: Long? = null,
    val lastOrderTime: Long? = null,
    val lastPositionTime: Long? = null,
) {
    val totalSaved: Long get() = signalsSaved + ordersSaved + positionsSaved
    val totalErrors: Long get() = signalErrors + orderErrors + positionErrors
    val successRate: Double
        get() =
            if (totalSaved + totalErrors > 0) {
                totalSaved.toDouble() / (totalSaved + totalErrors)
            } else {
                0.0
            }
}
