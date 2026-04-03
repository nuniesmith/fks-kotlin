package xyz.fkstrading.shared.domain.strategy.models

import kotlinx.datetime.Instant
import xyz.fkstrading.shared.domain.strategy.format

/**
 * Result of executing a batch of signals.
 *
 * Contains all individual execution results plus aggregate statistics
 * about the batch execution.
 */
data class BatchExecutionResult(
    /**
     * Individual execution results for each signal.
     */
    val results: List<ExecutionResult>,
    /**
     * When the batch execution started.
     */
    val startedAt: Instant,
    /**
     * When the batch execution completed.
     */
    val completedAt: Instant,
    /**
     * Total duration of batch execution in milliseconds.
     */
    val totalDurationMs: Long,
) {
    /**
     * Number of signals in the batch.
     */
    val totalCount: Int = results.size

    /**
     * Number of successful executions.
     */
    val successCount: Int =
        results.count {
            it is ExecutionResult.Success || it is ExecutionResult.DryRunSuccess
        }

    /**
     * Number of failed executions.
     */
    val failedCount: Int = results.count { it is ExecutionResult.Failed }

    /**
     * Number of rejected executions.
     */
    val rejectedCount: Int = results.count { it is ExecutionResult.Rejected }

    /**
     * Number of skipped executions.
     */
    val skippedCount: Int = results.count { it is ExecutionResult.Skipped }

    /**
     * Number of executions pending confirmation.
     */
    val pendingCount: Int = results.count { it is ExecutionResult.PendingConfirmation }

    /**
     * Success rate as percentage (0-100).
     */
    val successRate: Double =
        if (totalCount > 0) {
            (successCount.toDouble() / totalCount.toDouble()) * 100.0
        } else {
            0.0
        }

    /**
     * Whether all executions were successful.
     */
    val allSuccessful: Boolean = successCount == totalCount && totalCount > 0

    /**
     * Whether any executions were successful.
     */
    val anySuccessful: Boolean = successCount > 0

    /**
     * Whether the batch has any failures.
     */
    val hasFailures: Boolean = failedCount > 0 || rejectedCount > 0

    /**
     * Get all successful results.
     */
    fun getSuccessful(): List<ExecutionResult> =
        results.filter {
            it is ExecutionResult.Success || it is ExecutionResult.DryRunSuccess
        }

    /**
     * Get all failed results.
     */
    fun getFailed(): List<ExecutionResult.Failed> = results.filterIsInstance<ExecutionResult.Failed>()

    /**
     * Get all rejected results.
     */
    fun getRejected(): List<ExecutionResult.Rejected> = results.filterIsInstance<ExecutionResult.Rejected>()

    /**
     * Get all skipped results.
     */
    fun getSkipped(): List<ExecutionResult.Skipped> = results.filterIsInstance<ExecutionResult.Skipped>()

    /**
     * Get all pending confirmation results.
     */
    fun getPending(): List<ExecutionResult.PendingConfirmation> = results.filterIsInstance<ExecutionResult.PendingConfirmation>()

    /**
     * Get all error messages from failed and rejected executions.
     */
    fun getAllErrors(): List<String> {
        val errors = mutableListOf<String>()

        results.forEach { result ->
            when (result) {
                is ExecutionResult.Failed -> errors.add(result.error)
                is ExecutionResult.Rejected -> errors.addAll(result.validationErrors)
                else -> {}
            }
        }

        return errors
    }

    /**
     * Summary string of the batch execution.
     */
    fun summary(): String =
        buildString {
            append("Batch Execution Summary: ")
            append("$successCount/$totalCount successful")
            if (failedCount > 0) append(", $failedCount failed")
            if (rejectedCount > 0) append(", $rejectedCount rejected")
            if (skippedCount > 0) append(", $skippedCount skipped")
            if (pendingCount > 0) append(", $pendingCount pending")
            append(" (${(successRate * 100).format(1)}% success rate)")
            append(" in ${totalDurationMs}ms")
        }

    companion object {
        /**
         * Creates an empty batch execution result.
         */
        fun empty(
            startedAt: Instant,
            completedAt: Instant,
        ): BatchExecutionResult =
            BatchExecutionResult(
                results = emptyList(),
                startedAt = startedAt,
                completedAt = completedAt,
                totalDurationMs = completedAt.toEpochMilliseconds() - startedAt.toEpochMilliseconds(),
            )
    }
}
