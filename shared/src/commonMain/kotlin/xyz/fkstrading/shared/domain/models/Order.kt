package xyz.fkstrading.shared.domain.models

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/**
 * Trading order model
 */
@Serializable
data class Order(
    val orderId: String,
    val symbol: String,
    val side: OrderSide,
    val orderType: OrderType,
    val quantity: Double,
    val price: Double? = null,
    val stopPrice: Double? = null,
    val status: OrderStatus,
    val timeInForce: TimeInForce = TimeInForce.GTC,
    val timestamp: Instant,
    val submittedAt: Instant? = null,
    val updatedAt: Instant? = null,
    val completedAt: Instant? = null,
    val filledQuantity: Double = 0.0,
    val averageFillPrice: Double? = null,
    val fees: Double? = null,
    val commission: Double? = null,
    val strategyId: String? = null,
    val signalId: String? = null,
    val clientOrderId: String? = null,
    val errorMessage: String? = null,
    val metadata: Map<String, String> = emptyMap(),
)

/**
 * Order side enumeration
 */
@Serializable
enum class OrderSide {
    BUY,
    SELL,
}

/**
 * Order type enumeration
 */
@Serializable
enum class OrderType {
    MARKET,
    LIMIT,
    STOP,
    STOP_LIMIT,
    TRAILING_STOP,
}

/**
 * Order status enumeration
 */
@Serializable
enum class OrderStatus {
    PENDING,
    SUBMITTED,
    ACCEPTED,
    PARTIALLY_FILLED,
    FILLED,
    CANCELLED,
    REJECTED,
    EXPIRED,
}

/**
 * Time in force enumeration
 */
@Serializable
enum class TimeInForce {
    GTC, // Good Till Cancel
    IOC, // Immediate or Cancel
    FOK, // Fill or Kill
    DAY, // Day order
}
