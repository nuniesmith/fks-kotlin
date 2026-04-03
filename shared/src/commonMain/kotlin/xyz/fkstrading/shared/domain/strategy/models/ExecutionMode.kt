package xyz.fkstrading.shared.domain.strategy.models

/**
 * Execution mode for trading signals.
 *
 * The system operates in fully automatic mode. Signals are executed
 * automatically after passing validation checks.
 */
enum class ExecutionMode {
    /**
     * Fully automatic mode - automatic execution (still subject to validation).
     * This is the default and only supported mode.
     */
    AUTO,
}
