package xyz.fkstrading.shared.data.websocket

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import xyz.fkstrading.shared.data.websocket.messages.*
import xyz.fkstrading.shared.domain.models.*

/**
 * Manages real-time data streams from WebSocket messages.
 *
 * This class processes incoming WebSocket messages and distributes them to
 * appropriate SharedFlow streams based on message type. Subscribers can
 * collect from these flows to receive real-time updates.
 *
 * Features:
 * - Type-safe message parsing and distribution
 * - Separate flows for each data type (signals, orders, positions, market data)
 * - Automatic error handling and recovery
 * - Buffer overflow protection with DROP_OLDEST strategy
 * - Replay buffer for late subscribers
 *
 * Example usage:
 * ```
 * val dataStream: WebSocketDataStream = get() // from DI
 *
 * // Collect signals in real-time
 * dataStream.signalsFlow.collect { signal ->
 *     println("New signal: ${signal.symbol} ${signal.direction}")
 * }
 *
 * // Collect orders
 * dataStream.ordersFlow.collect { order ->
 *     println("Order update: ${order.orderId} ${order.status}")
 * }
 * ```
 *
 * @param webSocketClient The WebSocket client to receive messages from
 */
class WebSocketDataStream(
    private val webSocketClient: WebSocketClient,
) {
    // JSON parser configured for lenient parsing
    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
        }

    // Coroutine scope for processing messages
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Signals stream - replay last 10 signals for late subscribers
    private val _signalsFlow =
        MutableSharedFlow<Signal>(
            replay = 10,
            extraBufferCapacity = 100,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )
    val signalsFlow: SharedFlow<Signal> = _signalsFlow.asSharedFlow()

    // Orders stream - replay last 10 orders for late subscribers
    private val _ordersFlow =
        MutableSharedFlow<Order>(
            replay = 10,
            extraBufferCapacity = 100,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )
    val ordersFlow: SharedFlow<Order> = _ordersFlow.asSharedFlow()

    // Positions stream - replay last 10 positions for late subscribers
    private val _positionsFlow =
        MutableSharedFlow<Position>(
            replay = 10,
            extraBufferCapacity = 100,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )
    val positionsFlow: SharedFlow<Position> = _positionsFlow.asSharedFlow()

    // Market data stream - replay last 1 tick per symbol
    private val _marketDataFlow =
        MutableSharedFlow<MarketData>(
            replay = 1,
            extraBufferCapacity = 100,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )
    val marketDataFlow: SharedFlow<MarketData> = _marketDataFlow.asSharedFlow()

    // Error stream for message processing errors
    private val _errorFlow =
        MutableSharedFlow<WebSocketError>(
            replay = 5,
            extraBufferCapacity = 50,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )
    val errorFlow: SharedFlow<WebSocketError> = _errorFlow.asSharedFlow()

    init {
        // Start processing incoming messages
        scope.launch {
            webSocketClient.observeMessages()
                .catch { e ->
                    println("[WebSocketDataStream] Error in message stream: ${e.message}")
                    _errorFlow.emit(WebSocketError.StreamError(e.message ?: "Unknown error", e))
                }
                .collect { message ->
                    processMessage(message)
                }
        }
    }

    /**
     * Process an incoming WebSocket message and route it to the appropriate flow.
     */
    private suspend fun processMessage(rawMessage: String) {
        try {
            // Quick extraction of message type without full parsing
            val messageType = extractMessageType(rawMessage)

            when (messageType) {
                "signal" -> {
                    val msg = json.decodeFromString<SignalMessage>(rawMessage)
                    val signal = msg.data.toSignal()
                    _signalsFlow.emit(signal)
                    println("[WebSocketDataStream] Signal received: ${signal.symbol} ${signal.direction}")
                }

                "order" -> {
                    val msg = json.decodeFromString<OrderMessage>(rawMessage)
                    val order = msg.data.toOrder()
                    _ordersFlow.emit(order)
                    println("[WebSocketDataStream] Order update: ${order.orderId} ${order.status}")
                }

                "position" -> {
                    val msg = json.decodeFromString<PositionMessage>(rawMessage)
                    val position = msg.data.toPosition()
                    _positionsFlow.emit(position)
                    println("[WebSocketDataStream] Position update: ${position.positionId} ${position.symbol}")
                }

                "market_data" -> {
                    val msg = json.decodeFromString<MarketDataMessage>(rawMessage)
                    _marketDataFlow.emit(msg.data)
                    println("[WebSocketDataStream] Market data: ${msg.data.symbol} @ ${msg.data.last}")
                }

                "heartbeat" -> {
                    // Heartbeat messages are informational only
                    println("[WebSocketDataStream] Heartbeat received")
                }

                "subscription" -> {
                    val msg = json.decodeFromString<SubscriptionMessage>(rawMessage)
                    println("[WebSocketDataStream] Subscription ${msg.status}: ${msg.channel}")
                }

                "error" -> {
                    val msg = json.decodeFromString<ErrorMessage>(rawMessage)
                    _errorFlow.emit(WebSocketError.ServerError(msg.code, msg.message, msg.details))
                    println("[WebSocketDataStream] Server error: ${msg.code} - ${msg.message}")
                }

                null -> {
                    println("[WebSocketDataStream] Unknown message type: ${rawMessage.take(100)}")
                    _errorFlow.emit(WebSocketError.ParseError("Unknown message type", null))
                }

                else -> {
                    println("[WebSocketDataStream] Unhandled message type: $messageType")
                }
            }
        } catch (e: Exception) {
            println("[WebSocketDataStream] Error processing message: ${e.message}")
            _errorFlow.emit(WebSocketError.ParseError(e.message ?: "Parse error", e))
        }
    }

    /**
     * Extract message type from raw JSON without full parsing.
     * This is faster than deserializing the entire message.
     */
    private fun extractMessageType(rawMessage: String): String? {
        return try {
            val typeRegex = """"type"\s*:\s*"([^"]+)"""".toRegex()
            typeRegex.find(rawMessage)?.groupValues?.get(1)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Cleanup resources when the stream is no longer needed.
     */
    fun close() {
        scope.cancel()
    }
}

/**
 * WebSocket-specific errors.
 */
sealed class WebSocketError {
    /**
     * Error parsing a message.
     */
    data class ParseError(val message: String, val cause: Throwable?) : WebSocketError()

    /**
     * Error from the backend server.
     */
    data class ServerError(val code: String, val message: String, val details: String?) : WebSocketError()

    /**
     * Error in the message stream itself.
     */
    data class StreamError(val message: String, val cause: Throwable?) : WebSocketError()
}
// Extension functions to convert DTOs to domain models

/**
 * Convert SignalData DTO to Signal domain model.
 */
private fun SignalData.toSignal(): Signal {
    return Signal(
        signalId = this.id,
        signalType = SignalType.ENTRY, // Default to ENTRY, adjust based on your data
        symbol = this.symbol,
        timeframe = this.timeframe?.let { Timeframe.valueOf(it) } ?: Timeframe.M15,
        direction =
            when (this.type.uppercase()) {
                "BUY" -> Direction.LONG
                "SELL" -> Direction.SHORT
                else -> Direction.LONG
            },
        strength = this.confidence, // Use confidence as strength
        confidence = this.confidence,
        price = this.price,
        entryPrice = this.price,
        stopLoss = this.price * 0.98, // 2% stop loss as default
        takeProfit = this.price * 1.04, // 4% take profit as default
        timestamp = kotlinx.datetime.Clock.System.now(),
        metadata = this.indicators ?: emptyMap(),
        strategyName = this.strategy,
    )
}

/**
 * Convert OrderData DTO to Order domain model.
 */
private fun OrderData.toOrder(): Order {
    return Order(
        orderId = this.id,
        symbol = this.symbol,
        side = OrderSide.valueOf(this.side.uppercase()),
        orderType = OrderType.valueOf(this.orderType.uppercase()),
        quantity = this.quantity,
        price = this.price,
        stopPrice = this.stopPrice,
        status = OrderStatus.valueOf(this.status.uppercase()),
        filledQuantity = this.filledQuantity ?: 0.0,
        averageFillPrice = this.averageFillPrice,
        timestamp = kotlinx.datetime.Clock.System.now(),
    )
}

/**
 * Convert PositionData DTO to Position domain model.
 */
private fun PositionData.toPosition(): Position {
    return Position(
        positionId = this.id,
        symbol = this.symbol,
        side = this.side?.let { OrderSide.valueOf(it.uppercase()) } ?: OrderSide.BUY,
        quantity = this.quantity,
        entryPrice = this.entryPrice,
        currentPrice = this.currentPrice,
        stopLoss = null, // Not provided in DTO
        takeProfit = null, // Not provided in DTO
        status = PositionStatus.OPEN,
        openedAt =
            this.openedAt?.let {
                kotlinx.datetime.Instant.parse(it)
            } ?: kotlinx.datetime.Clock.System.now(),
        unrealizedPnL = this.unrealizedPnl,
        realizedPnL = this.realizedPnl ?: 0.0,
    )
}
