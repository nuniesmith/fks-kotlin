package xyz.fkstrading.shared.domain.strategy.models

import kotlinx.datetime.Instant
import xyz.fkstrading.shared.domain.models.Order
import xyz.fkstrading.shared.domain.models.Signal

/**
 * Result of a signal execution attempt.
 *
 * This sealed class represents all possible outcomes of executing a trading signal:
 * - Success: Order created and ready for submission
 * - Failed: Execution failed due to an error
 * - Rejected: Execution rejected due to validation failures
 * - Skipped: Execution skipped (e.g., disabled strategy or other reason)
 * - PendingConfirmation: Awaiting user confirmation (when requireConfirmation is true)
 * - DryRunSuccess: Successfully simulated in dry-run mode
 */
sealed class ExecutionResult {
    abstract val executionId: String
    abstract val signal: Signal
    abstract val status: ExecutionStatus
    abstract val config: ExecutionConfig
    abstract val executedAt: Instant
    abstract val executionDurationMs: Long?

    /**
     * Successful execution with order created.
     */
    data class Success(
        override val executionId: String,
        override val signal: Signal,
        override val config: ExecutionConfig,
        override val executedAt: Instant,
        override val executionDurationMs: Long? = null,
        val order: Order,
        val positionSize: Double,
        val stopLossPrice: Double?,
        val takeProfitPrice: Double?,
        val riskAmount: Double,
        val rewardAmount: Double?,
    ) : ExecutionResult() {
        override val status: ExecutionStatus = ExecutionStatus.SUCCESS
        val isSuccess: Boolean = true
    }

    /**
     * Execution failed due to an error.
     */
    data class Failed(
        override val executionId: String,
        override val signal: Signal,
        override val config: ExecutionConfig,
        override val executedAt: Instant,
        override val executionDurationMs: Long? = null,
        val error: String,
    ) : ExecutionResult() {
        override val status: ExecutionStatus = ExecutionStatus.FAILED
        val isSuccess: Boolean = false
    }

    /**
     * Execution rejected due to validation errors.
     */
    data class Rejected(
        override val executionId: String,
        override val signal: Signal,
        override val config: ExecutionConfig,
        override val executedAt: Instant,
        override val executionDurationMs: Long? = null,
        val validationErrors: List<String>,
    ) : ExecutionResult() {
        override val status: ExecutionStatus = ExecutionStatus.REJECTED
        val isSuccess: Boolean = false
    }

    /**
     * Execution skipped (e.g., disabled strategy or other reason).
     */
    data class Skipped(
        override val executionId: String,
        override val signal: Signal,
        override val config: ExecutionConfig,
        override val executedAt: Instant,
        override val executionDurationMs: Long? = null,
        val reason: String,
    ) : ExecutionResult() {
        override val status: ExecutionStatus = ExecutionStatus.SKIPPED
        val isSuccess: Boolean = false
    }

    /**
     * Execution pending user confirmation.
     */
    data class PendingConfirmation(
        override val executionId: String,
        override val signal: Signal,
        override val config: ExecutionConfig,
        override val executedAt: Instant,
        override val executionDurationMs: Long? = null,
        val positionSize: Double,
        val stopLossPrice: Double?,
        val takeProfitPrice: Double?,
        val riskAmount: Double,
        val rewardAmount: Double?,
    ) : ExecutionResult() {
        override val status: ExecutionStatus = ExecutionStatus.PENDING_CONFIRMATION
        val isPending: Boolean = true
        val isSuccess: Boolean = false
    }

    /**
     * Dry-run execution (simulated, no actual order).
     */
    data class DryRunSuccess(
        override val executionId: String,
        override val signal: Signal,
        override val config: ExecutionConfig,
        override val executedAt: Instant,
        override val executionDurationMs: Long? = null,
        val positionSize: Double,
        val stopLossPrice: Double?,
        val takeProfitPrice: Double?,
        val riskAmount: Double,
        val rewardAmount: Double?,
    ) : ExecutionResult() {
        override val status: ExecutionStatus = ExecutionStatus.DRY_RUN
        val isSuccess: Boolean = true
        val dryRunMode: Boolean = true
    }

    /**
     * Copy this result with updated execution metadata.
     */
    fun copy(
        executedAt: Instant? = null,
        executionDurationMs: Long? = null,
    ): ExecutionResult {
        return when (this) {
            is Success ->
                this.copy(
                    executedAt = executedAt ?: this.executedAt,
                    executionDurationMs = executionDurationMs ?: this.executionDurationMs,
                )

            is Failed ->
                this.copy(
                    executedAt = executedAt ?: this.executedAt,
                    executionDurationMs = executionDurationMs ?: this.executionDurationMs,
                )

            is Rejected ->
                this.copy(
                    executedAt = executedAt ?: this.executedAt,
                    executionDurationMs = executionDurationMs ?: this.executionDurationMs,
                )

            is Skipped ->
                this.copy(
                    executedAt = executedAt ?: this.executedAt,
                    executionDurationMs = executionDurationMs ?: this.executionDurationMs,
                )

            is PendingConfirmation ->
                this.copy(
                    executedAt = executedAt ?: this.executedAt,
                    executionDurationMs = executionDurationMs ?: this.executionDurationMs,
                )

            is DryRunSuccess ->
                this.copy(
                    executedAt = executedAt ?: this.executedAt,
                    executionDurationMs = executionDurationMs ?: this.executionDurationMs,
                )
        }
    }

    companion object {
        /**
         * Creates a successful execution result.
         */
        fun success(
            executionId: String,
            signal: Signal,
            order: Order,
            positionSize: Double,
            stopLossPrice: Double?,
            takeProfitPrice: Double?,
            riskAmount: Double,
            rewardAmount: Double?,
            config: ExecutionConfig,
            executedAt: Instant,
            executionDurationMs: Long? = null,
        ): ExecutionResult =
            Success(
                executionId = executionId,
                signal = signal,
                order = order,
                positionSize = positionSize,
                stopLossPrice = stopLossPrice,
                takeProfitPrice = takeProfitPrice,
                riskAmount = riskAmount,
                rewardAmount = rewardAmount,
                config = config,
                executedAt = executedAt,
                executionDurationMs = executionDurationMs,
            )

        /**
         * Creates a failed execution result.
         */
        fun failed(
            executionId: String,
            signal: Signal,
            error: String,
            config: ExecutionConfig,
            executedAt: Instant,
            executionDurationMs: Long? = null,
        ): ExecutionResult =
            Failed(
                executionId = executionId,
                signal = signal,
                error = error,
                config = config,
                executedAt = executedAt,
                executionDurationMs = executionDurationMs,
            )

        /**
         * Creates a rejected execution result.
         */
        fun rejected(
            executionId: String,
            signal: Signal,
            validationErrors: List<String>,
            config: ExecutionConfig,
            executedAt: Instant,
            executionDurationMs: Long? = null,
        ): ExecutionResult =
            Rejected(
                executionId = executionId,
                signal = signal,
                validationErrors = validationErrors,
                config = config,
                executedAt = executedAt,
                executionDurationMs = executionDurationMs,
            )

        /**
         * Creates a skipped execution result.
         */
        fun skipped(
            executionId: String,
            signal: Signal,
            reason: String,
            config: ExecutionConfig,
            executedAt: Instant,
            executionDurationMs: Long? = null,
        ): ExecutionResult =
            Skipped(
                executionId = executionId,
                signal = signal,
                reason = reason,
                config = config,
                executedAt = executedAt,
                executionDurationMs = executionDurationMs,
            )

        /**
         * Creates a pending confirmation result.
         */
        fun pendingConfirmation(
            executionId: String,
            signal: Signal,
            positionSize: Double,
            stopLossPrice: Double?,
            takeProfitPrice: Double?,
            riskAmount: Double,
            rewardAmount: Double?,
            config: ExecutionConfig,
            executedAt: Instant,
            executionDurationMs: Long? = null,
        ): ExecutionResult =
            PendingConfirmation(
                executionId = executionId,
                signal = signal,
                positionSize = positionSize,
                stopLossPrice = stopLossPrice,
                takeProfitPrice = takeProfitPrice,
                riskAmount = riskAmount,
                rewardAmount = rewardAmount,
                config = config,
                executedAt = executedAt,
                executionDurationMs = executionDurationMs,
            )

        /**
         * Creates a dry-run success result.
         */
        fun dryRunSuccess(
            executionId: String,
            signal: Signal,
            positionSize: Double,
            stopLossPrice: Double?,
            takeProfitPrice: Double?,
            riskAmount: Double,
            rewardAmount: Double?,
            config: ExecutionConfig,
            executedAt: Instant,
            executionDurationMs: Long? = null,
        ): ExecutionResult =
            DryRunSuccess(
                executionId = executionId,
                signal = signal,
                positionSize = positionSize,
                stopLossPrice = stopLossPrice,
                takeProfitPrice = takeProfitPrice,
                riskAmount = riskAmount,
                rewardAmount = rewardAmount,
                config = config,
                executedAt = executedAt,
                executionDurationMs = executionDurationMs,
            )
    }
}

/**
 * Extension properties for ExecutionResult to provide convenient access to common fields.
 */

/**
 * Returns true if this execution result represents a successful execution.
 */
val ExecutionResult.isSuccess: Boolean
    get() = this is ExecutionResult.Success

/**
 * Returns true if this execution result is pending user confirmation.
 */
val ExecutionResult.isPending: Boolean
    get() = this is ExecutionResult.PendingConfirmation

/**
 * Returns the order if this is a successful execution, null otherwise.
 */
val ExecutionResult.order: Order?
    get() = (this as? ExecutionResult.Success)?.order

/**
 * Returns the position size if available, null otherwise.
 */
val ExecutionResult.positionSize: Double?
    get() =
        when (this) {
            is ExecutionResult.Success -> this.positionSize
            is ExecutionResult.PendingConfirmation -> this.positionSize
            is ExecutionResult.DryRunSuccess -> this.positionSize
            else -> null
        }

/**
 * Returns the stop-loss price if available, null otherwise.
 */
val ExecutionResult.stopLossPrice: Double?
    get() =
        when (this) {
            is ExecutionResult.Success -> this.stopLossPrice
            is ExecutionResult.PendingConfirmation -> this.stopLossPrice
            is ExecutionResult.DryRunSuccess -> this.stopLossPrice
            else -> null
        }

/**
 * Returns the take-profit price if available, null otherwise.
 */
val ExecutionResult.takeProfitPrice: Double?
    get() =
        when (this) {
            is ExecutionResult.Success -> this.takeProfitPrice
            is ExecutionResult.PendingConfirmation -> this.takeProfitPrice
            is ExecutionResult.DryRunSuccess -> this.takeProfitPrice
            else -> null
        }

/**
 * Returns the risk amount if available, null otherwise.
 */
val ExecutionResult.riskAmount: Double?
    get() =
        when (this) {
            is ExecutionResult.Success -> this.riskAmount
            is ExecutionResult.PendingConfirmation -> this.riskAmount
            is ExecutionResult.DryRunSuccess -> this.riskAmount
            else -> null
        }

/**
 * Returns the reward amount if available, null otherwise.
 */
val ExecutionResult.rewardAmount: Double?
    get() =
        when (this) {
            is ExecutionResult.Success -> this.rewardAmount
            is ExecutionResult.PendingConfirmation -> this.rewardAmount
            is ExecutionResult.DryRunSuccess -> this.rewardAmount
            else -> null
        }

/**
 * Returns true if this execution result represents a failed or rejected execution.
 */
val ExecutionResult.isFailure: Boolean
    get() = this is ExecutionResult.Failed || this is ExecutionResult.Rejected

/**
 * Returns true if this execution result was skipped.
 */
val ExecutionResult.isSkipped: Boolean
    get() = this is ExecutionResult.Skipped

/**
 * Returns the error message if this is a failed execution, null otherwise.
 */
val ExecutionResult.errorMessage: String?
    get() = (this as? ExecutionResult.Failed)?.error

/**
 * Returns the validation errors if this is a rejected execution, empty list otherwise.
 */
val ExecutionResult.validationErrors: List<String>
    get() = (this as? ExecutionResult.Rejected)?.validationErrors ?: emptyList()
