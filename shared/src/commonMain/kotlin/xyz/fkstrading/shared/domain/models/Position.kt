package xyz.fkstrading.shared.domain.models

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/**
 * Trading position model
 */
@Serializable
data class Position(
    val positionId: String,
    val symbol: String,
    val side: OrderSide,
    val quantity: Double,
    val entryPrice: Double,
    val currentPrice: Double,
    val status: PositionStatus,
    val openedAt: Instant,
    val updatedAt: Instant? = null,
    val closedAt: Instant? = null,
    val unrealizedPnL: Double = 0.0,
    val realizedPnL: Double = 0.0,
    val unrealizedPnl: Double = unrealizedPnL,
    val realizedPnl: Double = realizedPnL,
    val value: Double = quantity * currentPrice,
    val fees: Double? = null,
    val commission: Double? = null,
    val stopLoss: Double? = null,
    val takeProfit: Double? = null,
    val strategyId: String? = null,
    val signalId: String? = null,
    val orderId: String? = null,
    val metadata: Map<String, String> = emptyMap(),
)

/**
 * Position status enumeration
 */
@Serializable
enum class PositionStatus {
    OPEN,
    CLOSED,
    PARTIALLY_CLOSED,
}
