package xyz.fkstrading.shared.domain.models

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/**
 * Trading signal model
 */
@Serializable
data class Signal(
    val signalId: String,
    val symbol: String,
    val signalType: SignalType,
    val direction: Direction,
    val strength: Double,
    val confidence: Double,
    val price: Double,
    val entryPrice: Double = price,
    val stopLoss: Double,
    val takeProfit: Double,
    val timestamp: Instant,
    val strategyId: String? = null,
    val strategyName: String? = null,
    val strategyType: StrategyType? = null,
    val timeframe: Timeframe? = null,
    val riskRewardRatio: Double? = null,
    val expiresAt: Instant? = null,
    val metadata: Map<String, String> = emptyMap(),
)

/**
 * Signal type enumeration
 */
@Serializable
enum class SignalType {
    ENTRY,
    EXIT,
    STOP_LOSS,
    TAKE_PROFIT,
    REVERSAL,
    CONTINUATION,
}

/**
 * Trading direction
 */
@Serializable
enum class Direction {
    LONG,
    SHORT,
}

/**
 * Trading timeframe
 */
@Serializable
enum class Timeframe {
    M1,
    M5,
    M15,
    M30,
    H1,
    H4,
    D1,
    W1,
}
