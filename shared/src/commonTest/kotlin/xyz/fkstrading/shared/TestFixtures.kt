package xyz.fkstrading.shared

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import xyz.fkstrading.shared.domain.models.*

/**
 * Test fixture helpers for creating sample domain models
 * These extension functions provide convenient defaults for testing
 */

/**
 * Creates a sample Signal with sensible defaults for testing
 */
fun Signal.Companion.sample(
    signalId: String = "TEST-SIGNAL-001",
    symbol: String = "BTC/USD",
    signalType: SignalType = SignalType.ENTRY,
    direction: Direction = Direction.LONG,
    strength: Double = 0.8,
    confidence: Double = 0.85,
    price: Double = 50000.0,
    entryPrice: Double = price,
    stopLoss: Double = price * 0.98,
    takeProfit: Double = price * 1.05,
    timestamp: Instant = Clock.System.now(),
    strategyId: String? = "STRATEGY-001",
    strategyName: String? = "Test Strategy",
    strategyType: StrategyType? = StrategyType.TREND_FOLLOWING,
    timeframe: Timeframe? = Timeframe.H1,
    riskRewardRatio: Double? = 2.5,
    expiresAt: Instant? = null,
    metadata: Map<String, String> = emptyMap(),
): Signal =
    Signal(
        signalId = signalId,
        symbol = symbol,
        signalType = signalType,
        direction = direction,
        strength = strength,
        confidence = confidence,
        price = price,
        entryPrice = entryPrice,
        stopLoss = stopLoss,
        takeProfit = takeProfit,
        timestamp = timestamp,
        strategyId = strategyId,
        strategyName = strategyName,
        strategyType = strategyType,
        timeframe = timeframe,
        riskRewardRatio = riskRewardRatio,
        expiresAt = expiresAt,
        metadata = metadata,
    )

/**
 * Creates a sample Order with sensible defaults for testing
 */
fun Order.Companion.sample(
    orderId: String = "TEST-ORDER-001",
    symbol: String = "BTC/USD",
    side: OrderSide = OrderSide.BUY,
    orderType: OrderType = OrderType.MARKET,
    quantity: Double = 0.1,
    price: Double? = null,
    stopPrice: Double? = null,
    status: OrderStatus = OrderStatus.PENDING,
    timeInForce: TimeInForce = TimeInForce.GTC,
    timestamp: Instant = Clock.System.now(),
    submittedAt: Instant? = null,
    updatedAt: Instant? = null,
    completedAt: Instant? = null,
    filledQuantity: Double = 0.0,
    averageFillPrice: Double? = null,
    fees: Double? = null,
    commission: Double? = null,
    strategyId: String? = "STRATEGY-001",
    signalId: String? = "SIGNAL-001",
    clientOrderId: String? = null,
    errorMessage: String? = null,
    metadata: Map<String, String> = emptyMap(),
): Order =
    Order(
        orderId = orderId,
        symbol = symbol,
        side = side,
        orderType = orderType,
        quantity = quantity,
        price = price,
        stopPrice = stopPrice,
        status = status,
        timeInForce = timeInForce,
        timestamp = timestamp,
        submittedAt = submittedAt,
        updatedAt = updatedAt,
        completedAt = completedAt,
        filledQuantity = filledQuantity,
        averageFillPrice = averageFillPrice,
        fees = fees,
        commission = commission,
        strategyId = strategyId,
        signalId = signalId,
        clientOrderId = clientOrderId,
        errorMessage = errorMessage,
        metadata = metadata,
    )

/**
 * Creates a sample Position with sensible defaults for testing
 */
fun Position.Companion.sample(
    positionId: String = "TEST-POSITION-001",
    symbol: String = "BTC/USD",
    side: OrderSide = OrderSide.BUY,
    quantity: Double = 0.1,
    entryPrice: Double = 50000.0,
    currentPrice: Double = entryPrice,
    status: PositionStatus = PositionStatus.OPEN,
    openedAt: Instant = Clock.System.now(),
    updatedAt: Instant? = null,
    closedAt: Instant? = null,
    unrealizedPnL: Double = (currentPrice - entryPrice) * quantity,
    realizedPnL: Double = 0.0,
    fees: Double? = null,
    commission: Double? = null,
    stopLoss: Double? = entryPrice * 0.98,
    takeProfit: Double? = entryPrice * 1.05,
    strategyId: String? = "STRATEGY-001",
    signalId: String? = "SIGNAL-001",
    orderId: String? = "ORDER-001",
    metadata: Map<String, String> = emptyMap(),
): Position =
    Position(
        positionId = positionId,
        symbol = symbol,
        side = side,
        quantity = quantity,
        entryPrice = entryPrice,
        currentPrice = currentPrice,
        status = status,
        openedAt = openedAt,
        updatedAt = updatedAt,
        closedAt = closedAt,
        unrealizedPnL = unrealizedPnL,
        realizedPnL = realizedPnL,
        fees = fees,
        commission = commission,
        stopLoss = stopLoss,
        takeProfit = takeProfit,
        strategyId = strategyId,
        signalId = signalId,
        orderId = orderId,
        metadata = metadata,
    )
