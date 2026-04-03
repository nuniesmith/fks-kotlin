package xyz.fkstrading.shared.domain.strategy.models

/**
 * Method used for calculating position size.
 */
enum class PositionSizingMethod {
    /**
     * Fixed dollar amount per position.
     */
    FIXED_AMOUNT,

    /**
     * Fixed percentage of account balance.
     */
    FIXED_PERCENTAGE,

    /**
     * Risk-based sizing - percentage of account at risk per trade.
     */
    RISK_BASED,

    /**
     * Kelly Criterion - optimal position size based on win rate and payoff ratio.
     */
    KELLY_CRITERION,

    /**
     * ATR-based sizing - normalize position size by Average True Range.
     */
    ATR_BASED,

    /**
     * Volatility-adjusted sizing - scale position by market volatility.
     */
    VOLATILITY_ADJUSTED,
}
