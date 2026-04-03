package xyz.fkstrading.shared.data.websocket

import kotlin.math.min
import kotlin.math.pow

/**
 * Configuration for WebSocket reconnection behavior.
 *
 * @param initialDelayMs Initial delay before the first reconnection attempt (default: 1000ms)
 * @param maxDelayMs Maximum delay between reconnection attempts (default: 60000ms)
 * @param multiplier Multiplier for exponential backoff (default: 2.0)
 * @param maxAttempts Maximum number of reconnection attempts (null = infinite retries)
 * @param connectionTimeoutMs Timeout for connection establishment (default: 30000ms)
 */
data class ReconnectConfig(
    val initialDelayMs: Long = 1000,
    val maxDelayMs: Long = 60000,
    val multiplier: Double = 2.0,
    val maxAttempts: Int? = null,
    val connectionTimeoutMs: Long = 30000,
) {
    init {
        require(initialDelayMs > 0) { "initialDelayMs must be positive" }
        require(maxDelayMs >= initialDelayMs) { "maxDelayMs must be >= initialDelayMs" }
        require(multiplier > 1.0) { "multiplier must be > 1.0" }
        require(maxAttempts == null || maxAttempts > 0) { "maxAttempts must be null or positive" }
        require(connectionTimeoutMs > 0) { "connectionTimeoutMs must be positive" }
    }
}

/**
 * Manages reconnection logic with exponential backoff.
 *
 * This class calculates delays between reconnection attempts using an exponential
 * backoff strategy. The delay increases exponentially with each failed attempt
 * up to a maximum value.
 *
 * Example progression with default config:
 * - Attempt 0: 1s delay
 * - Attempt 1: 2s delay
 * - Attempt 2: 4s delay
 * - Attempt 3: 8s delay
 * - Attempt 4: 16s delay
 * - Attempt 5: 32s delay
 * - Attempt 6+: 60s delay (max)
 *
 * @param config Configuration for reconnection behavior
 */
class ReconnectStrategy(
    private val config: ReconnectConfig = ReconnectConfig(),
) {
    private var currentAttempt = 0

    /**
     * Calculate the delay before the next reconnection attempt.
     *
     * The delay is calculated using exponential backoff:
     * delay = min(initialDelay * (multiplier ^ attempt), maxDelay)
     *
     * @return Delay in milliseconds before the next reconnection attempt
     */
    fun getNextDelay(): Long {
        val exponentialDelay =
            config.initialDelayMs *
                config.multiplier.pow(currentAttempt.toDouble()).toLong()
        return min(exponentialDelay, config.maxDelayMs)
    }

    /**
     * Check if we should attempt another reconnection.
     *
     * @return True if another reconnection attempt should be made, false otherwise
     */
    fun shouldRetry(): Boolean {
        return config.maxAttempts?.let { currentAttempt < it } ?: true
    }

    /**
     * Record a reconnection attempt.
     *
     * This increments the internal attempt counter, which affects the
     * calculated delay for the next attempt.
     */
    fun recordAttempt() {
        currentAttempt++
    }

    /**
     * Reset the strategy after a successful connection.
     *
     * This resets the attempt counter to 0, so the next reconnection
     * (if needed) will start from the initial delay.
     */
    fun reset() {
        currentAttempt = 0
    }

    /**
     * Get the current attempt number (0-indexed).
     *
     * @return The number of reconnection attempts that have been made
     */
    fun getCurrentAttempt(): Int = currentAttempt

    /**
     * Get the configured maximum number of attempts.
     *
     * @return Maximum attempts (null if unlimited)
     */
    fun getMaxAttempts(): Int? = config.maxAttempts

    /**
     * Check if the maximum number of attempts has been reached.
     *
     * @return True if max attempts reached, false otherwise
     */
    fun isMaxAttemptsReached(): Boolean {
        return config.maxAttempts?.let { currentAttempt >= it } ?: false
    }

    /**
     * Get a human-readable status string.
     *
     * @return Status description including attempt count and next delay
     */
    fun getStatusString(): String {
        val maxAttemptsStr = config.maxAttempts?.toString() ?: "∞"
        val nextDelay = getNextDelay()
        return "Attempt $currentAttempt/$maxAttemptsStr, next delay: ${nextDelay}ms"
    }
}
