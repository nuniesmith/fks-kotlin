package xyz.fkstrading.shared.domain.strategy.models

/**
 * Type of order to be executed.
 */
enum class OrderType {
    /**
     * Market order - execute immediately at current market price.
     */
    MARKET,

    /**
     * Limit order - execute at specified price or better.
     */
    LIMIT,

    /**
     * Stop order - becomes market order when stop price is reached.
     */
    STOP,

    /**
     * Stop-limit order - becomes limit order when stop price is reached.
     */
    STOP_LIMIT,

    /**
     * Trailing stop order - stop price follows market at specified distance.
     */
    TRAILING_STOP,
}
