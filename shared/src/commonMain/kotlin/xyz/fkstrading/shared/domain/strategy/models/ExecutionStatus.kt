package xyz.fkstrading.shared.domain.strategy.models

/**
 * Status of a signal execution.
 */
enum class ExecutionStatus {
    /**
     * Execution was successful and order was created.
     */
    SUCCESS,

    /**
     * Execution failed due to an error.
     */
    FAILED,

    /**
     * Execution was rejected due to validation errors.
     */
    REJECTED,

    /**
     * Execution was skipped (e.g., disabled strategy or other reason).
     */
    SKIPPED,

    /**
     * Execution is pending user confirmation.
     */
    PENDING_CONFIRMATION,

    /**
     * Execution completed in dry-run mode (no actual order).
     */
    DRY_RUN,
}
