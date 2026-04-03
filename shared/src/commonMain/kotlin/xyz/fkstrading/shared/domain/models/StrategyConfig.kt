package xyz.fkstrading.shared.domain.models

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import xyz.fkstrading.shared.domain.strategy.models.ExecutionConfig

/**
 * Trading strategy configuration model
 */
@Serializable
data class StrategyConfig(
    val configId: String,
    val name: String,
    val description: String? = null,
    val strategyType: StrategyType? = null,
    val symbol: String? = null,
    val timeframe: Timeframe? = null,
    val enabled: Boolean = true,
    val isActive: Boolean = true,
    val isDefault: Boolean = false,
    val executionConfig: ExecutionConfig,
    val createdAt: Instant,
    val updatedAt: Instant,
    val parameters: Map<String, String> = emptyMap(),
    val riskParameters: RiskParameters? = null,
    val metadata: Map<String, String> = emptyMap(),
) {
    companion object
}

/**
 * Strategy type enumeration
 */
@Serializable
enum class StrategyType {
    MEAN_REVERSION,
    TREND_FOLLOWING,
    MOMENTUM,
    BREAKOUT,
    SCALPING,
    ARBITRAGE,
    CUSTOM,
}

/**
 * Risk parameters for strategy
 */
@Serializable
data class RiskParameters(
    val maxPositionSize: Double? = null,
    val maxDailyLoss: Double? = null,
    val maxDrawdown: Double? = null,
    val stopLossPercent: Double? = null,
    val takeProfitPercent: Double? = null,
)

/**
 * Extension functions for StrategyConfig
 */

/**
 * Validates the strategy configuration.
 * Returns a list of validation error messages (empty list if valid).
 */
fun StrategyConfig.validate(): List<String> {
    val errors = mutableListOf<String>()

    if (name.isBlank()) {
        errors.add("Strategy name cannot be blank")
    }

    // Delegate to ExecutionConfig validation
    errors.addAll(executionConfig.validate())

    return errors
}

/**
 * Returns a copy of this config with an updated timestamp.
 */
fun StrategyConfig.withUpdatedTimestamp(timestamp: Instant): StrategyConfig {
    return copy(updatedAt = timestamp)
}

/**
 * Returns a copy of this config with isDefault set to true.
 */
fun StrategyConfig.withDefaultStatus(isDefault: Boolean): StrategyConfig {
    return copy(isDefault = isDefault)
}

/**
 * Returns a copy of this config with isActive set to the specified value.
 */
fun StrategyConfig.withActiveStatus(isActive: Boolean): StrategyConfig {
    return copy(isActive = isActive)
}

/**
 * Creates a default StrategyConfig instance.
 */
fun StrategyConfig.Companion.default(
    configId: String = "default",
    timestamp: Instant = kotlinx.datetime.Clock.System.now(),
    name: String = "Default Strategy",
): StrategyConfig {
    return StrategyConfig(
        configId = configId,
        name = name,
        description = "Default trading strategy configuration",
        executionConfig = ExecutionConfig.default(),
        createdAt = timestamp,
        updatedAt = timestamp,
    )
}

/**
 * Creates a conservative StrategyConfig instance.
 * Conservative configs use lower risk settings for safer trading.
 */
fun StrategyConfig.Companion.conservative(
    configId: String = "conservative",
    timestamp: Instant = kotlinx.datetime.Clock.System.now(),
    name: String = "Conservative Strategy",
): StrategyConfig {
    return StrategyConfig(
        configId = configId,
        name = name,
        description = "Conservative trading strategy with low risk settings",
        executionConfig = ExecutionConfig.conservative(),
        createdAt = timestamp,
        updatedAt = timestamp,
    )
}

/**
 * Creates an aggressive StrategyConfig instance.
 * Aggressive configs use higher risk settings for more active trading.
 */
fun StrategyConfig.Companion.aggressive(
    configId: String = "aggressive",
    timestamp: Instant = kotlinx.datetime.Clock.System.now(),
    name: String = "Aggressive Strategy",
): StrategyConfig {
    return StrategyConfig(
        configId = configId,
        name = name,
        description = "Aggressive trading strategy with higher risk settings",
        executionConfig = ExecutionConfig.aggressive(),
        createdAt = timestamp,
        updatedAt = timestamp,
    )
}
