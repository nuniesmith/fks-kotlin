package xyz.fkstrading.shared.domain.strategy

import xyz.fkstrading.shared.domain.models.Signal
import xyz.fkstrading.shared.domain.strategy.models.ExecutionConfig
import xyz.fkstrading.shared.domain.strategy.models.PositionSizingMethod
import kotlin.math.abs
import kotlin.math.min

/**
 * Calculates position sizes based on risk parameters and account balance.
 *
 * Supports multiple position sizing methods:
 * - Fixed quantity
 * - Fixed percentage of account
 * - Risk-based (accounts for stop-loss distance)
 * - Kelly Criterion (requires performance statistics)
 */
class PositionSizer {
    /**
     * Calculates position size for a signal based on execution configuration.
     *
     * @param signal The trading signal
     * @param config Execution configuration with sizing parameters
     * @param accountBalance Current account balance
     * @param currentPrice Current market price of the asset
     * @param stopLossPrice Calculated stop-loss price
     * @param winRate Historical win rate (0.0 to 1.0) - required for Kelly Criterion
     * @param profitFactor Historical profit factor - required for Kelly Criterion
     * @return Calculated position size (quantity)
     */
    fun calculatePositionSize(
        signal: Signal,
        config: ExecutionConfig,
        accountBalance: Double,
        currentPrice: Double,
        stopLossPrice: Double? = null,
        winRate: Double? = null,
        profitFactor: Double? = null,
    ): PositionSizeResult {
        if (accountBalance <= 0) {
            return PositionSizeResult.error("Account balance must be positive")
        }

        if (currentPrice <= 0) {
            return PositionSizeResult.error("Current price must be positive")
        }

        return when (config.positionSizingMethod) {
            PositionSizingMethod.FIXED_AMOUNT -> calculateFixed(config)
            PositionSizingMethod.FIXED_PERCENTAGE -> calculatePercentage(config, accountBalance, currentPrice)
            PositionSizingMethod.RISK_BASED -> {
                if (stopLossPrice == null) {
                    return PositionSizeResult.error("Stop-loss price required for risk-based sizing")
                }
                calculateRiskBased(signal, config, accountBalance, currentPrice, stopLossPrice)
            }

            PositionSizingMethod.KELLY_CRITERION -> {
                if (winRate == null || profitFactor == null) {
                    return PositionSizeResult.error("Win rate and profit factor required for Kelly Criterion")
                }
                calculateKelly(config, accountBalance, currentPrice, winRate, profitFactor)
            }

            PositionSizingMethod.ATR_BASED -> {
                // Fallback to fixed percentage for now
                calculatePercentage(config, accountBalance, currentPrice)
            }

            PositionSizingMethod.VOLATILITY_ADJUSTED -> {
                // Fallback to fixed percentage for now
                calculatePercentage(config, accountBalance, currentPrice)
            }
        }
    }

    /**
     * Fixed position size (constant quantity).
     */
    private fun calculateFixed(config: ExecutionConfig): PositionSizeResult {
        return PositionSizeResult.success(
            quantity = config.fixedPositionSize ?: 0.0,
            method = PositionSizingMethod.FIXED_AMOUNT,
            reasoning = "Fixed position size: ${config.fixedPositionSize} units",
        )
    }

    /**
     * Percentage of account balance.
     * positionValue = accountBalance * percentage
     * quantity = positionValue / currentPrice
     */
    private fun calculatePercentage(
        config: ExecutionConfig,
        accountBalance: Double,
        currentPrice: Double,
    ): PositionSizeResult {
        val percentage = config.accountPercentage ?: 0.01 // Default 1%
        val positionValue = accountBalance * percentage
        val quantity = positionValue / currentPrice

        return PositionSizeResult.success(
            quantity = quantity,
            method = PositionSizingMethod.FIXED_PERCENTAGE,
            reasoning =
                "Using ${(percentage * 100).toInt()}% of account " +
                    "($${positionValue.toInt()}) = ${quantity.format()} units",
        )
    }

    /**
     * Risk-based position sizing.
     *
     * Formula:
     * riskAmount = accountBalance * riskPerTrade
     * stopDistance = abs(currentPrice - stopLossPrice)
     * riskPerUnit = stopDistance
     * quantity = riskAmount / riskPerUnit
     *
     * This ensures we risk exactly riskPerTrade % of account on this trade.
     */
    private fun calculateRiskBased(
        signal: Signal,
        config: ExecutionConfig,
        accountBalance: Double,
        currentPrice: Double,
        stopLossPrice: Double,
    ): PositionSizeResult {
        // Calculate risk amount in dollars
        val riskAmount = accountBalance * config.riskPerTrade

        // Calculate stop-loss distance
        val stopDistance = abs(currentPrice - stopLossPrice)

        if (stopDistance <= 0) {
            return PositionSizeResult.error("Stop-loss distance must be positive")
        }

        // Risk per unit (how much we lose per unit if stopped out)
        val riskPerUnit = stopDistance

        // Calculate quantity
        val quantity = riskAmount / riskPerUnit

        // Validate quantity is reasonable
        if (quantity <= 0) {
            return PositionSizeResult.error("Calculated quantity must be positive")
        }

        // Calculate total position value
        val positionValue = quantity * currentPrice
        val positionValuePercent = (positionValue / accountBalance) * 100

        return PositionSizeResult.success(
            quantity = quantity,
            method = PositionSizingMethod.RISK_BASED,
            reasoning =
                "Risking ${(config.riskPerTrade * 100).format()}% of account " +
                    "($${riskAmount.toInt()}) with stop ${stopDistance.format()} points away " +
                    "= ${quantity.format()} units (${positionValuePercent.toInt()}% of account)",
            riskAmount = riskAmount,
            positionValue = positionValue,
        )
    }

    /**
     * Kelly Criterion for optimal position sizing.
     *
     * Formula:
     * f = (p * b - q) / b
     * where:
     *   f = fraction of bankroll to bet
     *   p = win rate (probability of winning)
     *   q = 1 - p (probability of losing)
     *   b = profit factor (average win / average loss)
     *
     * We apply a Kelly fraction (typically 0.25 or "quarter Kelly") to be conservative.
     */
    private fun calculateKelly(
        config: ExecutionConfig,
        accountBalance: Double,
        currentPrice: Double,
        winRate: Double,
        profitFactor: Double,
    ): PositionSizeResult {
        if (winRate < 0.0 || winRate > 1.0) {
            return PositionSizeResult.error("Win rate must be between 0.0 and 1.0")
        }

        if (profitFactor <= 0.0) {
            return PositionSizeResult.error("Profit factor must be positive")
        }

        // Kelly formula
        val p = winRate
        val q = 1.0 - p
        val b = profitFactor

        val fullKelly = (p * b - q) / b

        // If Kelly is negative or zero, don't trade
        if (fullKelly <= 0.0) {
            return PositionSizeResult.error(
                "Kelly Criterion suggests no trade (edge is negative). " +
                    "Win rate: ${(winRate * 100).toInt()}%, Profit factor: ${profitFactor.format()}",
            )
        }

        // Apply Kelly fraction for safety
        val fractionalKelly = fullKelly * config.kellyFraction

        // Clamp to reasonable limits (never risk more than 20% even if Kelly suggests it)
        val safeFraction = min(fractionalKelly, 0.20)

        // Calculate position value and quantity
        val positionValue = accountBalance * safeFraction
        val quantity = positionValue / currentPrice

        val kellyPercent = (fullKelly * 100).format()
        val fractionalPercent = (fractionalKelly * 100).format()
        val safePercent = (safeFraction * 100).format()

        return PositionSizeResult.success(
            quantity = quantity,
            method = PositionSizingMethod.KELLY_CRITERION,
            reasoning =
                "Full Kelly: $kellyPercent%, " +
                    "Fractional (${(config.kellyFraction * 100).toInt()}%): $fractionalPercent%, " +
                    "Safe: $safePercent% = ${quantity.format()} units",
            positionValue = positionValue,
        )
    }

    /**
     * Validates position size against account and risk limits.
     */
    fun validatePositionSize(
        quantity: Double,
        currentPrice: Double,
        accountBalance: Double,
        maxPositionPercent: Double = 0.30, // Max 30% of account in single position
    ): List<String> {
        val errors = mutableListOf<String>()

        if (quantity <= 0) {
            errors.add("Position size must be positive")
        }

        val positionValue = quantity * currentPrice
        val positionPercent = positionValue / accountBalance

        if (positionPercent > maxPositionPercent) {
            errors.add(
                "Position size (${(positionPercent * 100).toInt()}% of account) " +
                    "exceeds maximum allowed (${(maxPositionPercent * 100).toInt()}%)",
            )
        }

        if (positionValue > accountBalance) {
            errors.add("Position value exceeds account balance (need margin/leverage)")
        }

        return errors
    }
}

/**
 * Result of position size calculation.
 */
sealed class PositionSizeResult {
    data class Success(
        val quantity: Double,
        val method: PositionSizingMethod,
        val reasoning: String,
        val riskAmount: Double? = null,
        val positionValue: Double? = null,
    ) : PositionSizeResult()

    data class Error(
        val message: String,
    ) : PositionSizeResult()

    companion object {
        fun success(
            quantity: Double,
            method: PositionSizingMethod,
            reasoning: String,
            riskAmount: Double? = null,
            positionValue: Double? = null,
        ) = Success(quantity, method, reasoning, riskAmount, positionValue)

        fun error(message: String) = Error(message)
    }
}
