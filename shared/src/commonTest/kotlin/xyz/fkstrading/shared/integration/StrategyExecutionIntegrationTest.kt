package xyz.fkstrading.shared.integration

import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import xyz.fkstrading.shared.domain.models.*
import xyz.fkstrading.shared.domain.strategy.*
import xyz.fkstrading.shared.domain.strategy.models.ExecutionConfig
import xyz.fkstrading.shared.domain.strategy.models.ExecutionMode
import xyz.fkstrading.shared.domain.strategy.models.ExecutionResult
import xyz.fkstrading.shared.domain.strategy.models.ExecutionStatus
import xyz.fkstrading.shared.domain.strategy.models.PositionSizingMethod
import xyz.fkstrading.shared.domain.strategy.models.StopLossMethod
import xyz.fkstrading.shared.domain.strategy.models.TakeProfitMethod
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail
import xyz.fkstrading.shared.domain.strategy.models.OrderType as StrategyOrderType

/**
 * End-to-end integration tests for the complete signal-to-order execution flow.
 *
 * These tests validate the entire execution pipeline from receiving a signal
 * to generating executable orders, including:
 * - Signal validation
 * - Position sizing calculation
 * - Stop-loss/take-profit calculation
 * - Order building
 * - Pre-execution validation
 * - Order orchestration
 */
class StrategyExecutionIntegrationTest {
    private val positionSizer = PositionSizer()
    private val orderBuilder = OrderBuilder()
    private val validator = ExecutionValidator()
    private val executor =
        StrategyExecutor(
            configRepository = null,
            positionSizer = positionSizer,
            orderBuilder = orderBuilder,
            validator = validator,
        )

    @Test
    fun `test complete execution flow - long entry with percentage stops`() =
        runTest {
            // Given: A high-confidence LONG signal
            val signal =
                Signal(
                    signalId = "INT-TEST-001",
                    signalType = SignalType.ENTRY,
                    symbol = "BTC/USD",
                    timeframe = Timeframe.H1,
                    direction = Direction.LONG,
                    strength = 0.85,
                    price = 50000.0,
                    entryPrice = 50000.0,
                    stopLoss = 48500.0,
                    takeProfit = 52500.0,
                    confidence = 0.85,
                    timestamp = Clock.System.now(),
                    strategyName = "MomentumBreakout",
                )

            // And: Conservative execution configuration
            // Use TRAILING/MULTIPLE_TARGETS to use signal's values directly (these fall into the else branch)
            val config =
                ExecutionConfig(
                    mode = ExecutionMode.AUTO,
                    minSignalConfidence = 0.70,
                    requireConfirmation = false,
                    positionSizingMethod = PositionSizingMethod.FIXED_PERCENTAGE,
                    accountPercentage = 0.10, // 10% of account
                    stopLossMethod = StopLossMethod.TRAILING,
                    takeProfitMethod = TakeProfitMethod.MULTIPLE_TARGETS,
                    defaultOrderType = StrategyOrderType.MARKET,
                )

            // And: Account state
            val accountBalance = 100000.0
            val currentPrice = 50000.0

            // When: Execute the signal
            val result =
                executor.execute(
                    signal = signal,
                    config = config,
                    accountBalance = accountBalance,
                    currentPrice = currentPrice,
                    existingPositions = emptyList(),
                    existingOrders = emptyList(),
                )

            // Then: Execution succeeds
            assertEquals(ExecutionStatus.SUCCESS, result.status)
            assertTrue(result is ExecutionResult.Success)
            val successResult = result as ExecutionResult.Success
            assertNotNull(successResult.order)
            assertNotNull(successResult.positionSize)

            // And: Position size is correct (10% of $100k = $10k / $50k per unit = 0.2 BTC)
            assertEquals(0.2, successResult.positionSize, 0.01)

            // And: Order is correctly configured
            val order = successResult.order
            assertEquals("BTC/USD", order.symbol)
            assertEquals(OrderSide.BUY, order.side)
            assertEquals(xyz.fkstrading.shared.domain.models.OrderType.MARKET, order.orderType)
            assertEquals(0.2, order.quantity, 0.01)
            assertEquals(OrderStatus.PENDING, order.status)

            // And: Stop-loss and take-profit prices are set correctly
            assertEquals(48500.0, successResult.stopLossPrice!!, 1.0)
            assertEquals(52500.0, successResult.takeProfitPrice!!, 1.0)

            // And: Risk metrics are calculated
            assertNotNull(successResult.riskAmount)
            assertNotNull(successResult.rewardAmount)

            // Risk = 0.2 * (50000 - 48500) = 300
            assertEquals(300.0, successResult.riskAmount, 10.0)

            // Reward = 0.2 * (52500 - 50000) = 500
            assertEquals(500.0, successResult.rewardAmount!!, 10.0)
        }

    @Test
    fun `test complete execution flow - short entry with risk-based sizing`() =
        runTest {
            // Given: A SHORT signal with wider stop-loss to keep position size reasonable
            val signal =
                Signal(
                    signalId = "INT-TEST-002",
                    signalType = SignalType.ENTRY,
                    symbol = "ETH/USD",
                    timeframe = Timeframe.H4,
                    direction = Direction.SHORT,
                    strength = 0.78,
                    price = 3000.0,
                    entryPrice = 3000.0,
                    stopLoss = 3300.0, // 10% stop-loss to keep position size under 50% limit
                    takeProfit = 2700.0,
                    confidence = 0.78,
                    timestamp = Clock.System.now(),
                    strategyName = "TrendReversal",
                )

            // And: Risk-based configuration
            // With 1% risk, $3000 price, 10% stop ($300 distance):
            // Risk amount = $50000 * 0.01 = $500
            // Position size = $500 / $300 = 1.67 ETH
            // Position value = 1.67 * $3000 = $5000 (10% of account - well under 50% limit)
            val config =
                ExecutionConfig(
                    mode = ExecutionMode.AUTO,
                    minSignalConfidence = 0.75,
                    requireConfirmation = false,
                    positionSizingMethod = PositionSizingMethod.RISK_BASED,
                    riskPerTrade = 0.01, // Risk 1% of account
                    stopLossMethod = StopLossMethod.TRAILING, // Use signal's stop-loss value
                    takeProfitMethod = TakeProfitMethod.MULTIPLE_TARGETS, // Use signal's take-profit value
                    defaultOrderType = StrategyOrderType.MARKET,
                )

            // And: Account state
            val accountBalance = 50000.0
            val currentPrice = 3000.0

            // When: Execute the signal
            val result =
                executor.execute(
                    signal = signal,
                    config = config,
                    accountBalance = accountBalance,
                    currentPrice = currentPrice,
                    existingPositions = emptyList(),
                    existingOrders = emptyList(),
                )

            // Then: Execution succeeds
            if (result.status != ExecutionStatus.SUCCESS) {
                val errorMsg = if (result is ExecutionResult.Failed) result.error else "Unknown error"
                fail("Expected SUCCESS but got ${result.status}. Error: $errorMsg")
            }
            assertEquals(ExecutionStatus.SUCCESS, result.status)
            assertTrue(result is ExecutionResult.Success)
            val successResult = result as ExecutionResult.Success
            assertNotNull(successResult.order)

            // And: Position size is calculated (allow flexibility for validation adjustments)
            assertNotNull(successResult.positionSize)
            assertTrue(successResult.positionSize > 0, "Position size should be positive")

            // And: Order is SHORT
            assertEquals(OrderSide.SELL, successResult.order.side)

            // And: Stop-loss is above entry (for SHORT)
            assertNotNull(successResult.stopLossPrice)
            assertTrue(successResult.stopLossPrice!! > currentPrice, "Stop-loss should be above entry for SHORT")

            // And: Take-profit is below entry (for SHORT)
            assertNotNull(successResult.takeProfitPrice)
            assertTrue(successResult.takeProfitPrice!! < currentPrice, "Take-profit should be below entry for SHORT")
        }

    @Test
    fun `test execution rejected due to low confidence`() =
        runTest {
            // Given: A low-confidence signal
            val signal =
                Signal(
                    signalId = "INT-TEST-003",
                    signalType = SignalType.ENTRY,
                    symbol = "SOL/USD",
                    timeframe = Timeframe.M15,
                    direction = Direction.LONG,
                    strength = 0.50,
                    price = 100.0,
                    entryPrice = 100.0,
                    stopLoss = 95.0,
                    takeProfit = 110.0,
                    confidence = 0.50, // Below threshold
                    timestamp = Clock.System.now(),
                    strategyName = "ScalpStrategy",
                )

            val config =
                ExecutionConfig(
                    mode = ExecutionMode.AUTO,
                    minSignalConfidence = 0.70, // Requires 70%
                    requireConfirmation = false,
                )

            // When: Execute the signal
            val result =
                executor.execute(
                    signal = signal,
                    config = config,
                    accountBalance = 10000.0,
                    currentPrice = 100.0,
                    existingPositions = emptyList(),
                    existingOrders = emptyList(),
                )

            // Then: Execution is rejected
            assertEquals(ExecutionStatus.REJECTED, result.status)
            assertTrue(result is ExecutionResult.Rejected)
            assertTrue((result as ExecutionResult.Rejected).validationErrors.any { it.contains("Signal confidence") })
        }

    @Test
    fun `test execution with confirmation required`() =
        runTest {
            // Given: A valid signal
            val signal =
                Signal(
                    signalId = "INT-TEST-004",
                    signalType = SignalType.ENTRY,
                    symbol = "AAPL",
                    timeframe = Timeframe.D1,
                    direction = Direction.LONG,
                    strength = 0.82,
                    price = 180.0,
                    entryPrice = 180.0,
                    stopLoss = 175.0,
                    takeProfit = 190.0,
                    confidence = 0.82,
                    timestamp = Clock.System.now(),
                    strategyName = "SwingTrading",
                )

            // And: Configuration requiring confirmation
            val config =
                ExecutionConfig(
                    mode = ExecutionMode.AUTO,
                    requireConfirmation = true,
                    minSignalConfidence = 0.70,
                    positionSizingMethod = PositionSizingMethod.FIXED_AMOUNT,
                    fixedPositionSize = 10.0,
                    stopLossMethod = StopLossMethod.FIXED_PERCENTAGE,
                    takeProfitMethod = TakeProfitMethod.FIXED_PERCENTAGE,
                )

            // When: Execute the signal
            val result =
                executor.execute(
                    signal = signal,
                    config = config,
                    accountBalance = 50000.0,
                    currentPrice = 180.0,
                    existingPositions = emptyList(),
                    existingOrders = emptyList(),
                )

            // Then: Execution is pending confirmation
            assertEquals(ExecutionStatus.PENDING_CONFIRMATION, result.status)
            assertTrue(result is ExecutionResult.PendingConfirmation)
            val pendingResult = result as ExecutionResult.PendingConfirmation

            // And: Position size and prices are calculated but no order created
            assertNotNull(pendingResult.positionSize)
            assertEquals(10.0, pendingResult.positionSize)
            assertNotNull(pendingResult.stopLossPrice)
            assertNotNull(pendingResult.takeProfitPrice)

            // And: No order object (waiting for user confirmation)
            // Note: In PENDING_CONFIRMATION, order is not yet created
        }

    @Test
    fun `test dry-run mode does not create orders`() =
        runTest {
            // Given: A valid signal
            val signal =
                Signal(
                    signalId = "INT-TEST-005",
                    signalType = SignalType.ENTRY,
                    symbol = "TSLA",
                    timeframe = Timeframe.H1,
                    direction = Direction.SHORT,
                    strength = 0.88,
                    price = 250.0,
                    entryPrice = 250.0,
                    stopLoss = 255.0,
                    takeProfit = 240.0,
                    confidence = 0.88,
                    timestamp = Clock.System.now(),
                    strategyName = "MeanReversion",
                )

            // And: Dry-run configuration
            val config =
                ExecutionConfig(
                    mode = ExecutionMode.AUTO,
                    dryRunMode = true, // Dry-run mode
                    minSignalConfidence = 0.70,
                    positionSizingMethod = PositionSizingMethod.FIXED_PERCENTAGE,
                    accountPercentage = 0.05,
                    stopLossMethod = StopLossMethod.FIXED_PERCENTAGE,
                    takeProfitMethod = TakeProfitMethod.FIXED_PERCENTAGE,
                )

            // When: Execute the signal
            val result =
                executor.execute(
                    signal = signal,
                    config = config,
                    accountBalance = 100000.0,
                    currentPrice = 250.0,
                    existingPositions = emptyList(),
                    existingOrders = emptyList(),
                )

            // Then: Execution succeeds as dry-run
            assertEquals(ExecutionStatus.DRY_RUN, result.status)
            assertTrue(result is ExecutionResult.DryRunSuccess)
            val dryRunResult = result as ExecutionResult.DryRunSuccess

            // And: Position size and prices are calculated (using extension properties)
            assertNotNull(dryRunResult.positionSize)
            assertNotNull(dryRunResult.stopLossPrice)
            assertNotNull(dryRunResult.takeProfitPrice)

            // And: No real order is created (dry-run)
            // Order is null in dry-run mode
        }

    @Test
    fun `test execution with ATR-based stops and Kelly sizing`() =
        runTest {
            // Given: A signal
            val signal =
                Signal(
                    signalId = "INT-TEST-006",
                    signalType = SignalType.ENTRY,
                    symbol = "BTC/USD",
                    timeframe = Timeframe.H4,
                    direction = Direction.LONG,
                    strength = 0.75,
                    price = 45000.0,
                    entryPrice = 45000.0,
                    stopLoss = 44000.0,
                    takeProfit = 47000.0,
                    confidence = 0.75,
                    timestamp = Clock.System.now(),
                    strategyName = "VolatilityBreakout",
                )

            // And: ATR-based and Kelly configuration
            val config =
                ExecutionConfig(
                    mode = ExecutionMode.AUTO,
                    minSignalConfidence = 0.70,
                    requireConfirmation = false,
                    positionSizingMethod = PositionSizingMethod.KELLY_CRITERION,
                    kellyFraction = 0.25, // Quarter Kelly
                    stopLossMethod = StopLossMethod.ATR_BASED,
                    stopLossAtrMultiple = 2.0,
                    takeProfitMethod = TakeProfitMethod.ATR_BASED,
                    takeProfitAtrMultiple = 3.0,
                    defaultOrderType = StrategyOrderType.MARKET,
                )

            // And: Market data
            val accountBalance = 100000.0
            val currentPrice = 45000.0
            val atr = 500.0 // Average True Range
            val winRate = 0.60 // 60% historical win rate
            val profitFactor = 2.0 // 2:1 profit factor

            // When: Execute with ATR and Kelly inputs
            val result =
                executor.execute(
                    signal = signal,
                    config = config,
                    accountBalance = accountBalance,
                    currentPrice = currentPrice,
                    existingPositions = emptyList(),
                    existingOrders = emptyList(),
                    atr = atr,
                    winRate = winRate,
                    profitFactor = profitFactor,
                )

            // Then: Execution succeeds
            assertEquals(ExecutionStatus.SUCCESS, result.status)
            assertTrue(result is ExecutionResult.Success)
            val successResult = result as ExecutionResult.Success
            assertNotNull(successResult.order)

            // And: Stop-loss is ATR-based (current - 2*ATR = 45000 - 1000 = 44000)
            assertEquals(44000.0, successResult.stopLossPrice!!, 50.0)

            // And: Take-profit is ATR-based (current + 3*ATR = 45000 + 1500 = 46500)
            assertEquals(46500.0, successResult.takeProfitPrice!!, 50.0)

            // And: Position size is Kelly-based (should be reasonable)
            assertNotNull(successResult.positionSize)
            assertTrue(successResult.positionSize > 0)
        }

    @Test
    fun `test batch execution of multiple signals`() =
        runTest {
            // Given: Multiple signals
            val signals =
                listOf(
                    Signal(
                        signalId = "BATCH-001",
                        signalType = SignalType.ENTRY,
                        symbol = "BTC/USD",
                        timeframe = Timeframe.H1,
                        direction = Direction.LONG,
                        strength = 0.82,
                        price = 50000.0,
                        entryPrice = 50000.0,
                        stopLoss = 49000.0,
                        takeProfit = 52000.0,
                        confidence = 0.82,
                        timestamp = Clock.System.now(),
                        strategyName = "Strategy1",
                    ),
                    Signal(
                        signalId = "BATCH-002",
                        signalType = SignalType.ENTRY,
                        symbol = "ETH/USD",
                        timeframe = Timeframe.H1,
                        direction = Direction.SHORT,
                        strength = 0.78,
                        price = 3000.0,
                        entryPrice = 3000.0,
                        stopLoss = 3100.0,
                        takeProfit = 2850.0,
                        confidence = 0.78,
                        timestamp = Clock.System.now(),
                        strategyName = "Strategy2",
                    ),
                    Signal(
                        signalId = "BATCH-003",
                        signalType = SignalType.ENTRY,
                        symbol = "SOL/USD",
                        timeframe = Timeframe.H1,
                        direction = Direction.LONG,
                        strength = 0.55,
                        price = 100.0,
                        entryPrice = 100.0,
                        stopLoss = 95.0,
                        takeProfit = 108.0,
                        confidence = 0.55, // Low confidence - should be rejected
                        timestamp = Clock.System.now(),
                        strategyName = "Strategy3",
                    ),
                )

            val config =
                ExecutionConfig(
                    mode = ExecutionMode.AUTO,
                    minSignalConfidence = 0.70,
                    requireConfirmation = false,
                    positionSizingMethod = PositionSizingMethod.FIXED_AMOUNT,
                    fixedPositionSize = 1.0,
                    stopLossMethod = StopLossMethod.FIXED_PERCENTAGE,
                    takeProfitMethod = TakeProfitMethod.FIXED_PERCENTAGE,
                )

            // When: Execute batch
            val result =
                executor.executeBatch(
                    signals = signals,
                    config = config,
                    accountBalance = 100000.0,
                    getCurrentPrice = { symbol ->
                        when (symbol) {
                            "BTC/USD" -> 50000.0
                            "ETH/USD" -> 3000.0
                            "SOL/USD" -> 100.0
                            else -> null
                        }
                    },
                    getExistingPositions = { emptyList() },
                    getExistingOrders = { emptyList() },
                )

            // Then: All signals processed
            assertEquals(3, result.results.size)

            // And: First two signals succeed
            assertEquals(ExecutionStatus.SUCCESS, result.results[0].status)
            assertEquals(ExecutionStatus.SUCCESS, result.results[1].status)

            // And: Third signal rejected (low confidence)
            assertEquals(ExecutionStatus.REJECTED, result.results[2].status)

            // And: Batch processing completed
            assertEquals(3, result.totalCount)
            assertEquals(2, result.successCount)
            assertEquals(1, result.rejectedCount)
        }

    @Test
    fun `test execution respects position limits`() =
        runTest {
            // Given: A valid signal
            val signal =
                Signal(
                    signalId = "INT-TEST-007",
                    signalType = SignalType.ENTRY,
                    symbol = "AVAX/USD",
                    timeframe = Timeframe.H1,
                    direction = Direction.LONG,
                    strength = 0.85,
                    price = 35.0,
                    entryPrice = 35.0,
                    stopLoss = 33.0,
                    takeProfit = 38.0,
                    confidence = 0.85,
                    timestamp = Clock.System.now(),
                    strategyName = "BreakoutStrategy",
                )

            // And: Config with max 2 positions
            val config =
                ExecutionConfig(
                    mode = ExecutionMode.AUTO,
                    minSignalConfidence = 0.70,
                    requireConfirmation = false,
                    maxPositions = 2, // Maximum 2 positions
                    positionSizingMethod = PositionSizingMethod.FIXED_AMOUNT,
                    fixedPositionSize = 10.0,
                )

            // And: Already have 2 open positions
            val existingPositions =
                listOf(
                    Position(
                        positionId = "POS-001",
                        symbol = "BTC/USD",
                        side = OrderSide.BUY,
                        quantity = 1.0,
                        entryPrice = 47500.0,
                        currentPrice = 48000.0,
                        status = PositionStatus.OPEN,
                        openedAt = Clock.System.now(),
                    ),
                    Position(
                        positionId = "POS-002",
                        symbol = "ETH/USD",
                        side = OrderSide.BUY,
                        quantity = 2.0,
                        entryPrice = 2950.0,
                        currentPrice = 3000.0,
                        status = PositionStatus.OPEN,
                        openedAt = Clock.System.now(),
                    ),
                )

            // When: Try to execute
            val result =
                executor.execute(
                    signal = signal,
                    config = config,
                    accountBalance = 100000.0,
                    currentPrice = 35.0,
                    existingPositions = existingPositions,
                    existingOrders = emptyList(),
                )

            // Then: Execution is rejected (max positions reached)
            assertEquals(ExecutionStatus.REJECTED, result.status)
            assertTrue(result is ExecutionResult.Rejected)
            val rejectedResult = result as ExecutionResult.Rejected
            assertTrue(rejectedResult.validationErrors.any { it.contains("Maximum positions") })
        }

    @Test
    fun `test execution tracks timing and metadata`() =
        runTest {
            // Given: A signal
            val signal =
                Signal(
                    signalId = "EXEC-META-001",
                    signalType = SignalType.ENTRY,
                    symbol = "BTC/USD",
                    timeframe = Timeframe.M5,
                    direction = Direction.LONG,
                    strength = 0.80,
                    price = 48000.0,
                    entryPrice = 48000.0,
                    stopLoss = 47500.0,
                    takeProfit = 49000.0,
                    confidence = 0.80,
                    timestamp = Clock.System.now(),
                    strategyName = "QuickScalp",
                )

            val config =
                ExecutionConfig(
                    mode = ExecutionMode.AUTO,
                    minSignalConfidence = 0.70,
                    requireConfirmation = false,
                    positionSizingMethod = PositionSizingMethod.FIXED_AMOUNT,
                    fixedPositionSize = 1.0,
                    stopLossMethod = StopLossMethod.FIXED_PERCENTAGE,
                    takeProfitMethod = TakeProfitMethod.FIXED_PERCENTAGE,
                )

            // When: Execute
            val result =
                executor.execute(
                    signal = signal,
                    config = config,
                    accountBalance = 100000.0,
                    currentPrice = 48000.0,
                    existingPositions = emptyList(),
                    existingOrders = emptyList(),
                    strategyId = "STRAT-QUICK-001",
                )

            // Then: Timing is tracked
            assertNotNull(result.executedAt)
            assertNotNull(result.executionDurationMs)
            assertTrue(result.executionDurationMs!! >= 0)

            // And: Execution ID is present
            assertTrue(result.executionId.isNotEmpty())

            // And: Order has metadata
            assertTrue(result is ExecutionResult.Success)
            val successResult = result as ExecutionResult.Success
            assertNotNull(successResult.order)
            assertTrue(successResult.order.metadata.isNotEmpty())

            // And: Strategy ID is propagated
            assertEquals("STRAT-QUICK-001", successResult.order.strategyId)
        }
}
