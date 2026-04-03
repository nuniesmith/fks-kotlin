package xyz.fkstrading.shared.domain.strategy.models

import kotlinx.serialization.Serializable

/**
 * Configuration for signal execution.
 *
 * Contains all parameters needed to execute a trading signal, including:
 * - Execution mode and order type
 * - Position sizing configuration
 * - Stop-loss and take-profit settings
 * - Risk management parameters
 * - Validation and confirmation settings
 */
@Serializable
data class ExecutionConfig(
    /**
     * Execution mode - always AUTO for automatic execution.
     */
    val mode: ExecutionMode = ExecutionMode.AUTO,
    /**
     * Type of order to place.
     */
    val orderType: OrderType = OrderType.MARKET,
    /**
     * Default order type (alternative name for orderType).
     */
    val defaultOrderType: OrderType = orderType,
    /**
     * Offset for limit orders as a percentage.
     */
    val limitOrderOffset: Double = 0.001,
    /**
     * Time in force for orders.
     */
    val timeInForce: xyz.fkstrading.shared.domain.models.TimeInForce = xyz.fkstrading.shared.domain.models.TimeInForce.GTC,
    /**
     * Whether to run in dry-run mode (simulate execution without placing real orders).
     */
    val dryRunMode: Boolean = false,
    /**
     * Whether user confirmation is required before execution.
     */
    val requireConfirmation: Boolean = false,
    // Position Sizing
    /**
     * Method for calculating position size.
     */
    val positionSizingMethod: PositionSizingMethod = PositionSizingMethod.RISK_BASED,
    /**
     * Fixed dollar amount per position (for FIXED_AMOUNT method).
     */
    val fixedAmount: Double? = null,
    /**
     * Fixed percentage of account balance (for FIXED_PERCENTAGE method).
     */
    val fixedPercentage: Double? = null,
    /**
     * Fixed position size (alternative name for fixedAmount).
     */
    val fixedPositionSize: Double? = null,
    /**
     * Account percentage for position sizing (alternative name for fixedPercentage).
     */
    val accountPercentage: Double? = null,
    /**
     * Percentage of account to risk per trade (for RISK_BASED method).
     * Value should be between 0.0 and 1.0 (e.g., 0.01 = 1%, 0.02 = 2%).
     */
    val riskPercentage: Double = 0.01,
    /**
     * Risk per trade as a percentage (alternative name for riskPercentage).
     */
    val riskPerTrade: Double = riskPercentage,
    /**
     * Kelly fraction multiplier (for KELLY_CRITERION method).
     * Values < 1.0 are more conservative (e.g., 0.5 for half-Kelly).
     */
    val kellyFraction: Double = 0.5,
    /**
     * Target ATR multiple for position sizing (for ATR_BASED method).
     */
    val targetAtrMultiple: Double? = null,
    // Stop Loss
    /**
     * Method for calculating stop-loss.
     */
    val stopLossMethod: StopLossMethod = StopLossMethod.ATR_BASED,
    /**
     * Stop-loss percentage from entry (for FIXED_PERCENTAGE method).
     */
    val stopLossPercentage: Double? = null,
    /**
     * Stop-loss dollar amount from entry (for FIXED_AMOUNT method).
     */
    val stopLossAmount: Double? = null,
    /**
     * ATR multiple for stop-loss (for ATR_BASED method).
     */
    val stopLossAtrMultiple: Double = 2.0,
    /**
     * Trailing stop distance as percentage (for TRAILING method).
     */
    val trailingStopPercentage: Double? = null,
    // Take Profit
    /**
     * Method for calculating take-profit.
     */
    val takeProfitMethod: TakeProfitMethod = TakeProfitMethod.RISK_REWARD_RATIO,
    /**
     * Take-profit percentage from entry (for FIXED_PERCENTAGE method).
     */
    val takeProfitPercentage: Double? = null,
    /**
     * Take-profit dollar amount from entry (for FIXED_AMOUNT method).
     */
    val takeProfitAmount: Double? = null,
    /**
     * Risk-reward ratio for take-profit (for RISK_REWARD_RATIO method).
     */
    val riskRewardRatio: Double = 2.0,
    /**
     * ATR multiple for take-profit (for ATR_BASED method).
     */
    val takeProfitAtrMultiple: Double? = null,
    // Risk Management
    /**
     * Maximum position size as percentage of account.
     */
    val maxPositionSizePercent: Double = 10.0,
    /**
     * Minimum position size (in base currency or contracts).
     */
    val minPositionSize: Double = 0.0,
    /**
     * Maximum number of concurrent positions.
     */
    val maxConcurrentPositions: Int = 5,
    /**
     * Maximum positions (alternative name for maxConcurrentPositions).
     */
    val maxPositions: Int = maxConcurrentPositions,
    /**
     * Maximum positions per asset.
     */
    val maxPositionsPerAsset: Int = 1,
    /**
     * Maximum exposure per symbol as percentage of account.
     */
    val maxExposurePerSymbol: Double = 20.0,
    /**
     * Maximum total portfolio exposure as percentage of account.
     */
    val maxTotalExposure: Double = 100.0,
    // Validation
    /**
     * Minimum required confidence score for signal execution (0.0 - 1.0).
     */
    val minConfidence: Double = 0.0,
    /**
     * Minimum signal confidence (alternative name for minConfidence).
     */
    val minSignalConfidence: Double = minConfidence,
    /**
     * Minimum risk-reward ratio required for execution.
     */
    val minRiskRewardRatio: Double = 1.0,
    /**
     * Whether to validate against existing positions.
     */
    val checkExistingPositions: Boolean = true,
    /**
     * Whether to validate against pending orders.
     */
    val checkPendingOrders: Boolean = true,
    /**
     * Configuration name/identifier.
     */
    val name: String = "Default",
    /**
     * Whether to close all positions at end of day.
     */
    val closePositionsEOD: Boolean = false,
    /**
     * Maximum allowed slippage as a percentage.
     */
    val maxSlippagePercent: Double = 0.5,
    /**
     * Whitelist of allowed assets (empty = all allowed).
     */
    val assetWhitelist: List<String> = emptyList(),
    /**
     * Blacklist of prohibited assets.
     */
    val assetBlacklist: List<String> = emptyList(),
) {
    /**
     * Validates the configuration and returns a list of error messages.
     * Empty list means the configuration is valid.
     */
    fun validate(): List<String> {
        val errors = mutableListOf<String>()

        // Validate position sizing
        // Note: accountPercentage is an alias for fixedPercentage, fixedPositionSize is an alias for fixedAmount
        val effectiveFixedAmount = fixedAmount ?: fixedPositionSize
        val effectiveFixedPercentage = fixedPercentage ?: accountPercentage

        when (positionSizingMethod) {
            PositionSizingMethod.FIXED_AMOUNT -> {
                if (effectiveFixedAmount == null || effectiveFixedAmount <= 0.0) {
                    errors.add("fixedAmount (or fixedPositionSize) must be positive for FIXED_AMOUNT method")
                }
            }

            PositionSizingMethod.FIXED_PERCENTAGE -> {
                if (effectiveFixedPercentage == null || effectiveFixedPercentage <= 0.0 || effectiveFixedPercentage > 1.0) {
                    errors.add(
                        "fixedPercentage (or accountPercentage) must be between 0 and 1 (e.g., 0.10 = 10%) for FIXED_PERCENTAGE method",
                    )
                }
            }

            PositionSizingMethod.RISK_BASED -> {
                if (riskPercentage <= 0.0 || riskPercentage > 1.0) {
                    errors.add("riskPercentage must be between 0 and 1 (e.g., 0.01 = 1%)")
                }
            }

            PositionSizingMethod.KELLY_CRITERION -> {
                if (kellyFraction <= 0.0 || kellyFraction > 1.0) {
                    errors.add("kellyFraction must be between 0 and 1")
                }
            }

            PositionSizingMethod.ATR_BASED -> {
                if (targetAtrMultiple == null || targetAtrMultiple <= 0.0) {
                    errors.add("targetAtrMultiple must be positive for ATR_BASED method")
                }
            }

            else -> {}
        }

        // Validate stop loss
        // Note: OrderBuilder has sensible defaults (2% for FIXED_PERCENTAGE), so we only validate
        // when explicit values are provided to ensure they're within valid ranges
        when (stopLossMethod) {
            StopLossMethod.FIXED_PERCENTAGE -> {
                // stopLossPercentage is optional - OrderBuilder defaults to 2% if not provided
                if (stopLossPercentage != null && (stopLossPercentage <= 0.0 || stopLossPercentage >= 1.0)) {
                    errors.add("stopLossPercentage must be between 0 and 1 (e.g., 0.02 = 2%)")
                }
            }

            StopLossMethod.FIXED_AMOUNT -> {
                if (stopLossAmount != null && stopLossAmount <= 0.0) {
                    errors.add("stopLossAmount must be positive")
                }
            }

            StopLossMethod.ATR_BASED -> {
                if (stopLossAtrMultiple <= 0.0) {
                    errors.add("stopLossAtrMultiple must be positive")
                }
            }

            StopLossMethod.TRAILING -> {
                // trailingStopPercentage is optional
                if (trailingStopPercentage != null && (trailingStopPercentage <= 0.0 || trailingStopPercentage >= 1.0)) {
                    errors.add("trailingStopPercentage must be between 0 and 1 (e.g., 0.05 = 5%)")
                }
            }

            else -> {}
        }

        // Validate take profit
        // Note: OrderBuilder has sensible defaults (2% for FIXED_PERCENTAGE), so we only validate
        // when explicit values are provided to ensure they're within valid ranges
        when (takeProfitMethod) {
            TakeProfitMethod.FIXED_PERCENTAGE -> {
                // takeProfitPercentage is optional - OrderBuilder defaults to 2% if not provided
                if (takeProfitPercentage != null && takeProfitPercentage <= 0.0) {
                    errors.add("takeProfitPercentage must be positive")
                }
            }

            TakeProfitMethod.FIXED_AMOUNT -> {
                if (takeProfitAmount != null && takeProfitAmount <= 0.0) {
                    errors.add("takeProfitAmount must be positive")
                }
            }

            TakeProfitMethod.RISK_REWARD_RATIO -> {
                if (riskRewardRatio <= 0.0) {
                    errors.add("riskRewardRatio must be positive")
                }
            }

            TakeProfitMethod.ATR_BASED -> {
                // takeProfitAtrMultiple is optional - OrderBuilder defaults to 3x ATR
                if (takeProfitAtrMultiple != null && takeProfitAtrMultiple <= 0.0) {
                    errors.add("takeProfitAtrMultiple must be positive")
                }
            }

            else -> {}
        }

        // Validate risk management
        if (maxPositionSizePercent <= 0.0 || maxPositionSizePercent > 100.0) {
            errors.add("maxPositionSizePercent must be between 0 and 100")
        }
        if (minPositionSize < 0.0) {
            errors.add("minPositionSize cannot be negative")
        }
        if (maxConcurrentPositions <= 0) {
            errors.add("maxConcurrentPositions must be positive")
        }
        if (maxExposurePerSymbol <= 0.0 || maxExposurePerSymbol > 100.0) {
            errors.add("maxExposurePerSymbol must be between 0 and 100")
        }
        if (maxTotalExposure <= 0.0 || maxTotalExposure > 100.0) {
            errors.add("maxTotalExposure must be between 0 and 100")
        }

        // Validate confidence and ratios
        if (minConfidence < 0.0 || minConfidence > 1.0) {
            errors.add("minConfidence must be between 0 and 1")
        }
        // Also validate minSignalConfidence (alias) if it differs from minConfidence
        if (minSignalConfidence < 0.0 || minSignalConfidence > 1.0) {
            errors.add("minSignalConfidence must be between 0 and 1")
        }
        if (minRiskRewardRatio <= 0.0) {
            errors.add("minRiskRewardRatio must be positive")
        }

        return errors
    }

    companion object {
        /**
         * Default conservative configuration.
         */
        fun default(): ExecutionConfig = ExecutionConfig()

        /**
         * Aggressive configuration for higher risk tolerance.
         * Uses higher risk per trade and larger position sizes.
         */
        fun aggressive(): ExecutionConfig =
            ExecutionConfig(
                mode = ExecutionMode.AUTO,
                riskPercentage = 0.02, // 2% risk per trade
                maxPositionSizePercent = 20.0,
                maxConcurrentPositions = 10,
                minRiskRewardRatio = 1.5,
                requireConfirmation = false,
            )

        /**
         * Conservative configuration for lower risk tolerance.
         * Uses lower risk per trade and smaller position sizes.
         */
        fun conservative(): ExecutionConfig =
            ExecutionConfig(
                mode = ExecutionMode.AUTO,
                riskPercentage = 0.005, // 0.5% risk per trade
                maxPositionSizePercent = 5.0,
                maxConcurrentPositions = 3,
                minRiskRewardRatio = 2.5,
                requireConfirmation = false,
            )

        /**
         * Dry-run configuration for testing.
         */
        fun dryRun(): ExecutionConfig =
            ExecutionConfig(
                dryRunMode = true,
                mode = ExecutionMode.AUTO,
            )
    }
}
