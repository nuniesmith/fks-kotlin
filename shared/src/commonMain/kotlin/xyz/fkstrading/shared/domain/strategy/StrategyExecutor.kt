package xyz.fkstrading.shared.domain.strategy

import kotlinx.datetime.Clock
import xyz.fkstrading.shared.data.repository.StrategyConfigRepository
import xyz.fkstrading.shared.domain.models.Order
import xyz.fkstrading.shared.domain.models.Position
import xyz.fkstrading.shared.domain.models.Signal
import xyz.fkstrading.shared.domain.strategy.models.BatchExecutionResult
import xyz.fkstrading.shared.domain.strategy.models.ExecutionConfig
import xyz.fkstrading.shared.domain.strategy.models.ExecutionMode
import xyz.fkstrading.shared.domain.strategy.models.ExecutionResult
import xyz.fkstrading.shared.domain.strategy.models.isPending
import xyz.fkstrading.shared.domain.strategy.models.isSuccess
import xyz.fkstrading.shared.domain.strategy.models.order
import xyz.fkstrading.shared.domain.strategy.models.positionSize
import xyz.fkstrading.shared.domain.strategy.models.rewardAmount
import xyz.fkstrading.shared.domain.strategy.models.riskAmount
import xyz.fkstrading.shared.domain.strategy.models.stopLossPrice
import xyz.fkstrading.shared.domain.strategy.models.takeProfitPrice
import kotlin.time.measureTimedValue

/**
 * Orchestrates the execution of trading signals.
 *
 * Coordinates the entire signal-to-order workflow:
 * 1. Validates signal and execution configuration
 * 2. Calculates position size based on risk parameters
 * 3. Calculates stop-loss and take-profit levels
 * 4. Builds order objects
 * 5. Performs pre-execution validation
 * 6. Submits orders (or returns for confirmation)
 *
 * Execution is always automatic (AUTO mode), but can be configured with:
 * - requireConfirmation: Order prepared and presented for user confirmation before submission
 * - dryRunMode: Simulated execution without real order placement
 */
class StrategyExecutor(
    private val configRepository: StrategyConfigRepository? = null,
    private val positionSizer: PositionSizer = PositionSizer(),
    private val orderBuilder: OrderBuilder = OrderBuilder(),
    private val validator: ExecutionValidator = ExecutionValidator(),
) {
    /**
     * Executes a trading signal using the default persisted configuration.
     * Falls back to ExecutionConfig.default() if repository is not available or no default exists.
     *
     * @param signal Signal to execute
     * @param accountBalance Current account balance
     * @param currentPrice Current market price of the asset
     * @param existingPositions List of currently open positions
     * @param existingOrders List of currently active orders
     * @param atr Average True Range for the asset (optional, for ATR-based stops)
     * @param winRate Historical win rate for Kelly Criterion (optional)
     * @param profitFactor Historical profit factor for Kelly Criterion (optional)
     * @param strategyId Strategy identifier (optional)
     * @return ExecutionResult with outcome
     */
    suspend fun executeWithDefaultConfig(
        signal: Signal,
        accountBalance: Double,
        currentPrice: Double,
        existingPositions: List<Position> = emptyList(),
        existingOrders: List<Order> = emptyList(),
        atr: Double? = null,
        winRate: Double? = null,
        profitFactor: Double? = null,
        strategyId: String? = null,
    ): ExecutionResult {
        val config =
            configRepository?.getDefaultConfig()?.executionConfig
                ?: ExecutionConfig()

        return execute(
            signal = signal,
            config = config,
            accountBalance = accountBalance,
            currentPrice = currentPrice,
            existingPositions = existingPositions,
            existingOrders = existingOrders,
            atr = atr,
            winRate = winRate,
            profitFactor = profitFactor,
            strategyId = strategyId,
        )
    }

    /**
     * Executes a trading signal using a specific persisted configuration.
     *
     * @param signal Signal to execute
     * @param configId ID of the configuration to use
     * @param accountBalance Current account balance
     * @param currentPrice Current market price of the asset
     * @param existingPositions List of currently open positions
     * @param existingOrders List of currently active orders
     * @param atr Average True Range for the asset (optional, for ATR-based stops)
     * @param winRate Historical win rate for Kelly Criterion (optional)
     * @param profitFactor Historical profit factor for Kelly Criterion (optional)
     * @return ExecutionResult with outcome
     */
    suspend fun executeWithConfig(
        signal: Signal,
        configId: String,
        accountBalance: Double,
        currentPrice: Double,
        existingPositions: List<Position> = emptyList(),
        existingOrders: List<Order> = emptyList(),
        atr: Double? = null,
        winRate: Double? = null,
        profitFactor: Double? = null,
    ): ExecutionResult {
        if (configRepository == null) {
            return ExecutionResult.failed(
                executionId = generateExecutionId(),
                signal = signal,
                error = "Config repository not available",
                config = ExecutionConfig(),
                executedAt = Clock.System.now(),
            )
        }

        val strategyConfig =
            configRepository.getConfigById(configId)
                ?: return ExecutionResult.failed(
                    executionId = generateExecutionId(),
                    signal = signal,
                    error = "Configuration '$configId' not found",
                    config = ExecutionConfig(),
                    executedAt = Clock.System.now(),
                )

        if (!strategyConfig.isActive) {
            return ExecutionResult.failed(
                executionId = generateExecutionId(),
                signal = signal,
                error = "Configuration '$configId' is not active",
                config = strategyConfig.executionConfig,
                executedAt = Clock.System.now(),
            )
        }

        return execute(
            signal = signal,
            config = strategyConfig.executionConfig,
            accountBalance = accountBalance,
            currentPrice = currentPrice,
            existingPositions = existingPositions,
            existingOrders = existingOrders,
            atr = atr,
            winRate = winRate,
            profitFactor = profitFactor,
            strategyId = configId, // Use config ID as strategy ID
        )
    }

    /**
     * Executes a trading signal with an explicit execution configuration.
     *
     * @param signal Signal to execute
     * @param config Execution configuration
     * @param accountBalance Current account balance
     * @param currentPrice Current market price of the asset
     * @param existingPositions List of currently open positions
     * @param existingOrders List of currently active orders
     * @param atr Average True Range for the asset (optional, for ATR-based stops)
     * @param winRate Historical win rate for Kelly Criterion (optional)
     * @param profitFactor Historical profit factor for Kelly Criterion (optional)
     * @param strategyId Strategy identifier (optional)
     * @return ExecutionResult with outcome
     */
    suspend fun execute(
        signal: Signal,
        config: ExecutionConfig,
        accountBalance: Double,
        currentPrice: Double,
        existingPositions: List<Position> = emptyList(),
        existingOrders: List<Order> = emptyList(),
        atr: Double? = null,
        winRate: Double? = null,
        profitFactor: Double? = null,
        strategyId: String? = null,
    ): ExecutionResult {
        val startTime = Clock.System.now()
        val executionId = generateExecutionId()

        // Measure execution time
        val (result, duration) =
            measureTimedValue {
                executeInternal(
                    executionId = executionId,
                    signal = signal,
                    config = config,
                    accountBalance = accountBalance,
                    currentPrice = currentPrice,
                    existingPositions = existingPositions,
                    existingOrders = existingOrders,
                    atr = atr,
                    winRate = winRate,
                    profitFactor = profitFactor,
                    strategyId = strategyId,
                )
            }

        // Add execution duration to result
        return when (result) {
            is ExecutionResult ->
                result.copy(
                    executedAt = startTime,
                    executionDurationMs = duration.inWholeMilliseconds,
                )
        }
    }

    /**
     * Internal execution logic.
     */
    private fun executeInternal(
        executionId: String,
        signal: Signal,
        config: ExecutionConfig,
        accountBalance: Double,
        currentPrice: Double,
        existingPositions: List<Position>,
        existingOrders: List<Order>,
        atr: Double?,
        winRate: Double?,
        profitFactor: Double?,
        strategyId: String?,
    ): ExecutionResult {
        val now = Clock.System.now()

        // Step 1: Validate execution configuration
        val configErrors = config.validate()
        if (configErrors.isNotEmpty()) {
            return ExecutionResult.failed(
                executionId = executionId,
                signal = signal,
                error = "Invalid configuration: ${configErrors.first()}",
                config = config,
                executedAt = now,
            )
        }

        // Step 2: Execution mode is always AUTO - proceed with automatic execution

        // Step 3: Calculate stop-loss
        val stopLossPrice =
            orderBuilder.calculateStopLoss(
                signal = signal,
                config = config,
                currentPrice = currentPrice,
                atr = atr,
            )

        // Step 4: Calculate position size
        val positionSizeResult =
            positionSizer.calculatePositionSize(
                signal = signal,
                config = config,
                accountBalance = accountBalance,
                currentPrice = currentPrice,
                stopLossPrice = stopLossPrice,
                winRate = winRate,
                profitFactor = profitFactor,
            )

        // Handle position sizing errors
        if (positionSizeResult is PositionSizeResult.Error) {
            return ExecutionResult.failed(
                executionId = executionId,
                signal = signal,
                error = "Position sizing failed: ${positionSizeResult.message}",
                config = config,
                executedAt = now,
            )
        }

        val positionSize = (positionSizeResult as PositionSizeResult.Success).quantity

        // Step 5: Perform pre-execution validation
        val validationResult =
            if (config.dryRunMode) {
                validator.validateForDryRun(signal, config)
            } else {
                validator.validate(
                    signal = signal,
                    config = config,
                    accountBalance = accountBalance,
                    currentPrice = currentPrice,
                    existingPositions = existingPositions,
                    existingOrders = existingOrders,
                    positionSize = positionSize,
                    stopLossPrice = stopLossPrice,
                )
            }

        // Handle validation errors
        if (validationResult is ValidationResult.Invalid) {
            return ExecutionResult.rejected(
                executionId = executionId,
                signal = signal,
                validationErrors = validationResult.errors,
                config = config,
                executedAt = now,
            )
        }

        // Get validation warnings
        val warnings = (validationResult as ValidationResult.Valid).warnings

        // Step 6: Calculate take-profit
        val takeProfitPrice =
            orderBuilder.calculateTakeProfit(
                signal = signal,
                config = config,
                currentPrice = currentPrice,
                stopLossPrice = stopLossPrice,
                atr = atr,
            )

        // Step 7: Calculate risk and reward amounts
        val riskAmount =
            if (stopLossPrice != null) {
                positionSize * kotlin.math.abs(currentPrice - stopLossPrice)
            } else {
                positionSizeResult.riskAmount ?: 0.0
            }

        val rewardAmount =
            if (takeProfitPrice != null) {
                positionSize * kotlin.math.abs(takeProfitPrice - currentPrice)
            } else {
                null
            }

        // Step 8: Check if confirmation is required
        if (config.requireConfirmation && !config.dryRunMode) {
            return ExecutionResult.pendingConfirmation(
                executionId = executionId,
                signal = signal,
                positionSize = positionSize,
                stopLossPrice = stopLossPrice,
                takeProfitPrice = takeProfitPrice,
                riskAmount = riskAmount,
                rewardAmount = rewardAmount,
                config = config,
                executedAt = now,
            )
        }

        // Step 9: Build order
        val orderBuildResult =
            orderBuilder.buildOrder(
                signal = signal,
                config = config,
                positionSize = positionSize,
                currentPrice = currentPrice,
                stopLossPrice = stopLossPrice,
                takeProfitPrice = takeProfitPrice,
                strategyId = strategyId,
            )

        // Handle order building errors
        if (orderBuildResult is OrderBuildResult.Error) {
            return ExecutionResult.failed(
                executionId = executionId,
                signal = signal,
                error = "Order building failed: ${orderBuildResult.message}",
                config = config,
                executedAt = now,
            )
        }

        val order = (orderBuildResult as OrderBuildResult.Success).order

        // Step 10: Return result based on dry-run mode
        return if (config.dryRunMode) {
            ExecutionResult.dryRunSuccess(
                executionId = executionId,
                signal = signal,
                positionSize = positionSize,
                stopLossPrice = stopLossPrice,
                takeProfitPrice = takeProfitPrice,
                riskAmount = riskAmount,
                rewardAmount = rewardAmount,
                config = config,
                executedAt = now,
            )
        } else {
            ExecutionResult.success(
                executionId = executionId,
                signal = signal,
                order = order,
                positionSize = positionSize,
                stopLossPrice = stopLossPrice,
                takeProfitPrice = takeProfitPrice,
                riskAmount = riskAmount,
                rewardAmount = rewardAmount,
                config = config,
                executedAt = now,
            )
        }
    }

    /**
     * Executes multiple signals in batch.
     *
     * @param signals List of signals to execute
     * @param config Execution configuration
     * @param accountBalance Current account balance
     * @param getCurrentPrice Function to get current price for a symbol
     * @param getExistingPositions Function to get existing positions
     * @param getExistingOrders Function to get existing orders
     * @param getAtr Function to get ATR for a symbol (optional)
     * @param winRate Historical win rate for Kelly Criterion (optional)
     * @param profitFactor Historical profit factor for Kelly Criterion (optional)
     * @param strategyId Strategy identifier (optional)
     * @return BatchExecutionResult with all individual results
     */
    suspend fun executeBatch(
        signals: List<Signal>,
        config: ExecutionConfig,
        accountBalance: Double,
        getCurrentPrice: suspend (String) -> Double?,
        getExistingPositions: suspend () -> List<Position> = { emptyList() },
        getExistingOrders: suspend () -> List<Order> = { emptyList() },
        getAtr: suspend (String) -> Double? = { null },
        winRate: Double? = null,
        profitFactor: Double? = null,
        strategyId: String? = null,
    ): BatchExecutionResult {
        val startTime = Clock.System.now()
        val results = mutableListOf<ExecutionResult>()

        // Get current state once for all signals
        val existingPositions = getExistingPositions()
        val existingOrders = getExistingOrders()

        for (signal in signals) {
            // Get current price for this signal
            val currentPrice = getCurrentPrice(signal.symbol)
            if (currentPrice == null) {
                results.add(
                    ExecutionResult.failed(
                        executionId = generateExecutionId(),
                        signal = signal,
                        error = "Failed to get current price for ${signal.symbol}",
                        config = config,
                        executedAt = Clock.System.now(),
                    ),
                )
                continue
            }

            // Get ATR if available
            val atr = getAtr(signal.symbol)

            // Execute this signal
            val result =
                execute(
                    signal = signal,
                    config = config,
                    accountBalance = accountBalance,
                    currentPrice = currentPrice,
                    existingPositions = existingPositions,
                    existingOrders = existingOrders,
                    atr = atr,
                    winRate = winRate,
                    profitFactor = profitFactor,
                    strategyId = strategyId,
                )

            results.add(result)

            // If execution was successful and not dry-run, update our local state
            // to account for this order in subsequent executions
            if (result.isSuccess && result.order != null && !config.dryRunMode) {
                // Note: In a real implementation, we'd update existingOrders list
                // For now, each signal is executed independently
            }
        }

        val endTime = Clock.System.now()

        return BatchExecutionResult(
            results = results,
            startedAt = startTime,
            completedAt = endTime,
            totalDurationMs = endTime.toEpochMilliseconds() - startTime.toEpochMilliseconds(),
        )
    }

    /**
     * Confirms a pending execution and submits the order.
     *
     * This is used when requireConfirmation is true in the execution config.
     *
     * @param pendingResult The pending execution result to confirm
     * @return Updated ExecutionResult with order created
     */
    fun confirmExecution(pendingResult: ExecutionResult): ExecutionResult {
        if (!pendingResult.isPending) {
            return ExecutionResult.failed(
                executionId = pendingResult.executionId,
                signal = pendingResult.signal,
                error = "Execution is not pending confirmation",
                config = pendingResult.config,
                executedAt = Clock.System.now(),
            )
        }

        // Build the order
        val orderBuildResult =
            orderBuilder.buildOrder(
                signal = pendingResult.signal,
                config = pendingResult.config,
                positionSize = pendingResult.positionSize ?: 0.0,
                currentPrice = pendingResult.signal.entryPrice,
                stopLossPrice = pendingResult.stopLossPrice,
                takeProfitPrice = pendingResult.takeProfitPrice,
            )

        return if (orderBuildResult is OrderBuildResult.Success) {
            ExecutionResult.success(
                executionId = pendingResult.executionId,
                signal = pendingResult.signal,
                order = orderBuildResult.order,
                positionSize = pendingResult.positionSize ?: 0.0,
                stopLossPrice = pendingResult.stopLossPrice,
                takeProfitPrice = pendingResult.takeProfitPrice,
                riskAmount = pendingResult.riskAmount ?: 0.0,
                rewardAmount = pendingResult.rewardAmount,
                config = pendingResult.config,
                executedAt = Clock.System.now(),
            )
        } else {
            ExecutionResult.failed(
                executionId = pendingResult.executionId,
                signal = pendingResult.signal,
                error = "Order building failed: ${(orderBuildResult as OrderBuildResult.Error).message}",
                config = pendingResult.config,
                executedAt = Clock.System.now(),
            )
        }
    }

    /**
     * Cancels a pending execution.
     *
     * @param pendingResult The pending execution result to cancel
     * @return Skipped ExecutionResult
     */
    fun cancelExecution(pendingResult: ExecutionResult): ExecutionResult {
        return ExecutionResult.skipped(
            executionId = pendingResult.executionId,
            signal = pendingResult.signal,
            reason = "Execution cancelled by user",
            config = pendingResult.config,
            executedAt = Clock.System.now(),
        )
    }

    /**
     * Generates a unique execution ID.
     */
    private fun generateExecutionId(): String {
        val timestamp = Clock.System.now().toEpochMilliseconds()
        val random = kotlin.random.Random.nextInt(1000, 9999)
        return "EXEC-$timestamp-$random"
    }

    companion object {
        /**
         * Creates a StrategyExecutor with default components.
         */
        fun create(): StrategyExecutor {
            return StrategyExecutor()
        }
    }
}
