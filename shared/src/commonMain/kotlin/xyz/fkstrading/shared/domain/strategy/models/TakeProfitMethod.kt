package xyz.fkstrading.shared.domain.strategy.models

/**
 * Method for calculating take-profit price.
 */
enum class TakeProfitMethod {
    /**
     * No take-profit.
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
     * Risk-reward ratio based take-profit.
     */
    RISK_REWARD_RATIO,

    /**
     * ATR-based take-profit (multiple of Average True Range).
     */
    ATR_BASED,

    /**
     * Support/resistance level.
     */
    SUPPORT_RESISTANCE,

    /**
     * Trailing take-profit that follows price movement.
     */
    TRAILING,

    /**
     * Multiple targets with partial exits.
     */
    MULTIPLE_TARGETS,
}
