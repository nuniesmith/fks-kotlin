package xyz.fkstrading.shared.domain.strategy.models

/**
 * Method for calculating stop-loss price.
 */
enum class StopLossMethod {
    /**
     * No stop-loss.
     */
    NONE,

    /**
     * Fixed percentage from entry price.
     */
    FIXED_PERCENTAGE,

    /**
     * Fixed dollar amount from entry price.
     */
    FIXED_AMOUNT,

    /**
     * ATR-based stop-loss (multiple of Average True Range).
     */
    ATR_BASED,

    /**
     * Support/resistance level.
     */
    SUPPORT_RESISTANCE,

    /**
     * Trailing stop that follows price movement.
     */
    TRAILING,

    /**
     * Volatility-adjusted stop-loss.
     */
    VOLATILITY_ADJUSTED,
}
