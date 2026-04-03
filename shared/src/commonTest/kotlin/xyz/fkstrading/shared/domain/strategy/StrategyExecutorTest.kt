package xyz.fkstrading.shared.domain.strategy

import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import xyz.fkstrading.shared.domain.models.*
import xyz.fkstrading.shared.domain.strategy.models.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for StrategyExecutor.
 */
class StrategyExecutorTest {
    private val executor = StrategyExecutor()

    @Test
    fun `test successful execution in auto mode`() =
        runTest {
            val signal = createTestSignal(direction = Direction.LONG, confidence = 0.85)
            val config =
                ExecutionConfig(
                    mode = ExecutionMode.AUTO,
                    minSignalConfidence = 0.70,
                    requireConfirmation = false,
                    stopLossMethod = StopLossMethod.FIXED_PERCENTAGE,
                    stopLossPercentage = 0.02,
                    takeProfitMethod = TakeProfitMethod.FIXED_PERCENTAGE,
                    takeProfitPercentage = 0.05,
                )
            val accountBalance = 10000.0
            val currentPrice = 100.0

            val result =
                executor.execute(
                    signal = signal,
                    config = config,
                    accountBalance = accountBalance,
                    currentPrice = currentPrice,
                    existingPositions = emptyList(),
                    existingOrders = emptyList(),
                )

            assertEquals(ExecutionStatus.SUCCESS, result.status)
            assertNotNull(result.order)
            assertNotNull(result.positionSize)
            assertTrue(result.positionSize!! > 0)
            assertEquals(signal.signalId, result.signal.signalId)
            assertNotNull(result.executedAt)
        }

    @Test
    fun `test execution pending confirmation when required`() =
        runTest {
            val signal = createTestSignal(confidence = 0.85)
            val config =
                ExecutionConfig(
                    mode = ExecutionMode.AUTO,
                    requireConfirmation = true,
                    minSignalConfidence = 0.70,
                    stopLossMethod = StopLossMethod.FIXED_PERCENTAGE,
                    stopLossPercentage = 0.02,
                    takeProfitMethod = TakeProfitMethod.FIXED_PERCENTAGE,
                    takeProfitPercentage = 0.05,
                )
            val accountBalance = 10000.0
            val currentPrice = 100.0

            val result =
                executor.execute(
                    signal = signal,
                    config = config,
                    accountBalance = accountBalance,
                    currentPrice = currentPrice,
                    existingPositions = emptyList(),
                    existingOrders = emptyList(),
                )

            assertEquals(ExecutionStatus.PENDING_CONFIRMATION, result.status)
            assertNotNull(result.positionSize)
            assertNotNull(result.riskAmount)
            assertNull(result.order) // Order not created yet, waiting for confirmation
        }

    @Test
    fun `test dry-run mode does not create real order`() =
        runTest {
            val signal = createTestSignal(confidence = 0.85)
            val config =
                ExecutionConfig(
                    mode = ExecutionMode.AUTO,
                    dryRunMode = true,
                    minSignalConfidence = 0.70,
                    stopLossMethod = StopLossMethod.FIXED_PERCENTAGE,
                    stopLossPercentage = 0.02,
                    takeProfitMethod = TakeProfitMethod.FIXED_PERCENTAGE,
                    takeProfitPercentage = 0.05,
                )
            val accountBalance = 10000.0
            val currentPrice = 100.0

            val result =
                executor.execute(
                    signal = signal,
                    config = config,
                    accountBalance = accountBalance,
                    currentPrice = currentPrice,
                    existingPositions = emptyList(),
                    existingOrders = emptyList(),
                )

            assertEquals(ExecutionStatus.DRY_RUN, result.status)
            assertTrue(result is ExecutionResult.DryRunSuccess)
            val dryRunResult = result as ExecutionResult.DryRunSuccess
            assertNotNull(dryRunResult.positionSize)
            assertNotNull(dryRunResult.riskAmount)
            // In dry-run, we simulate but don't create actual order
        }

    @Test
    fun `test execution rejected due to low confidence`() =
        runTest {
            val signal = createTestSignal(confidence = 0.50)
            val config =
                ExecutionConfig(
                    mode = ExecutionMode.AUTO,
                    minSignalConfidence = 0.70,
                    stopLossMethod = StopLossMethod.FIXED_PERCENTAGE,
                    stopLossPercentage = 0.02,
                    takeProfitMethod = TakeProfitMethod.FIXED_PERCENTAGE,
                    takeProfitPercentage = 0.05,
                )
            val accountBalance = 10000.0
            val currentPrice = 100.0

            val result =
                executor.execute(
                    signal = signal,
                    config = config,
                    accountBalance = accountBalance,
                    currentPrice = currentPrice,
                    existingPositions = emptyList(),
                    existingOrders = emptyList(),
                )

            assertEquals(ExecutionStatus.REJECTED, result.status)
            assertTrue(result is ExecutionResult.Rejected)
            val rejectedResult = result as ExecutionResult.Rejected
            assertTrue(rejectedResult.validationErrors.isNotEmpty())
            assertTrue(rejectedResult.validationErrors.any { it.contains("confidence") })
        }

    @Test
    fun `test execution rejected due to insufficient balance`() =
        runTest {
            val signal = createTestSignal(confidence = 0.85)
            val config =
                ExecutionConfig(
                    mode = ExecutionMode.AUTO,
                    minSignalConfidence = 0.70,
                    positionSizingMethod = PositionSizingMethod.RISK_BASED,
                    riskPerTrade = 0.10, // 10% risk requires larger position
                    stopLossMethod = StopLossMethod.FIXED_PERCENTAGE,
                    stopLossPercentage = 0.01, // 1% stop = tight stop = large position
                    takeProfitMethod = TakeProfitMethod.FIXED_PERCENTAGE,
                    takeProfitPercentage = 0.05,
                )
            val accountBalance = 1000.0 // Only have $1,000 but need much more
            val currentPrice = 100.0

            val result =
                executor.execute(
                    signal = signal,
                    config = config,
                    accountBalance = accountBalance,
                    currentPrice = currentPrice,
                    existingPositions = emptyList(),
                    existingOrders = emptyList(),
                )

            assertEquals(ExecutionStatus.REJECTED, result.status)
            assertTrue(result is ExecutionResult.Rejected)
            val rejectedResult = result as ExecutionResult.Rejected
            assertTrue(rejectedResult.validationErrors.any { it.contains("balance") })
        }

    @Test
    fun `test execution with stop-loss and take-profit calculation`() =
        runTest {
            val signal = createTestSignal(direction = Direction.LONG, confidence = 0.85)
            val config =
                ExecutionConfig(
                    mode = ExecutionMode.AUTO,
                    minSignalConfidence = 0.70,
                    requireConfirmation = false,
                    stopLossMethod = StopLossMethod.FIXED_PERCENTAGE,
                    stopLossPercentage = 0.02,
                    takeProfitMethod = TakeProfitMethod.RISK_REWARD_RATIO,
                    riskRewardRatio = 2.0,
                    dryRunMode = false,
                )
            val accountBalance = 10000.0
            val currentPrice = 100.0

            val result =
                executor.execute(
                    signal = signal,
                    config = config,
                    accountBalance = accountBalance,
                    currentPrice = currentPrice,
                    existingPositions = emptyList(),
                    existingOrders = emptyList(),
                )

            assertEquals(ExecutionStatus.SUCCESS, result.status)
            assertNotNull(result.stopLossPrice)
            assertNotNull(result.takeProfitPrice)

            // LONG with 2% SL: stop should be at 98
            assertEquals(98.0, result.stopLossPrice!!, 0.1)

            // With 2:1 R/R, TP should be at 104 (2 * 2 points risk)
            assertEquals(104.0, result.takeProfitPrice!!, 0.1)

            assertNotNull(result.riskAmount)
            assertNotNull(result.rewardAmount)
        }

    @Test
    fun `test execution with risk-based position sizing`() =
        runTest {
            val signal = createTestSignal(direction = Direction.LONG, confidence = 0.85)
            val config =
                ExecutionConfig(
                    mode = ExecutionMode.AUTO,
                    minSignalConfidence = 0.70,
                    requireConfirmation = false,
                    positionSizingMethod = PositionSizingMethod.RISK_BASED,
                    riskPerTrade = 0.01, // Risk 1% of account
                    stopLossMethod = StopLossMethod.FIXED_PERCENTAGE,
                    stopLossPercentage = 0.05, // 5% stop-loss
                )
            val accountBalance = 10000.0
            val currentPrice = 100.0

            val result =
                executor.execute(
                    signal = signal,
                    config = config,
                    accountBalance = accountBalance,
                    currentPrice = currentPrice,
                    existingPositions = emptyList(),
                    existingOrders = emptyList(),
                )

            assertEquals(ExecutionStatus.SUCCESS, result.status)
            assertNotNull(result.positionSize)

            // Risk amount = 1% of $10,000 = $100
            // Stop distance = $100 * 0.05 = $5
            // Position size = $100 / $5 = 20 units
            // Position value = 20 * $100 = $2,000 (20% of account - reasonable)
            assertEquals(20.0, result.positionSize!!, 2.0)
        }

    @Test
    fun `test execution rejected when max positions reached`() =
        runTest {
            val signal = createTestSignal(symbol = "SOL/USD", confidence = 0.85)
            val config =
                ExecutionConfig(
                    mode = ExecutionMode.AUTO,
                    minSignalConfidence = 0.70,
                    maxPositions = 2,
                )
            val accountBalance = 10000.0
            val currentPrice = 100.0

            // Already have 2 positions (at limit)
            val existingPositions =
                listOf(
                    createTestPosition(symbol = "BTC/USD"),
                    createTestPosition(symbol = "ETH/USD"),
                )

            val result =
                executor.execute(
                    signal = signal,
                    config = config,
                    accountBalance = accountBalance,
                    currentPrice = currentPrice,
                    existingPositions = existingPositions,
                    existingOrders = emptyList(),
                )

            assertEquals(ExecutionStatus.REJECTED, result.status)
            assertTrue(result is ExecutionResult.Rejected)
            assertTrue((result as ExecutionResult.Rejected).validationErrors.any { it.contains("Maximum positions limit reached") })
        }

    @Test
    fun `test execution rejected when duplicate signal order exists`() =
        runTest {
            val signal = createTestSignal(signalId = "SIGNAL-123", confidence = 0.85)
            val config =
                ExecutionConfig(
                    mode = ExecutionMode.AUTO,
                    minSignalConfidence = 0.70,
                    stopLossMethod = StopLossMethod.FIXED_PERCENTAGE,
                    stopLossPercentage = 0.02,
                    takeProfitMethod = TakeProfitMethod.FIXED_PERCENTAGE,
                    takeProfitPercentage = 0.05,
                )
            val accountBalance = 10000.0
            val currentPrice = 100.0

            // Order already exists for this signal
            val existingOrders =
                listOf(
                    createTestOrder(signalId = "SIGNAL-123", status = OrderStatus.PENDING),
                )

            val result =
                executor.execute(
                    signal = signal,
                    config = config,
                    accountBalance = accountBalance,
                    currentPrice = currentPrice,
                    existingPositions = emptyList(),
                    existingOrders = existingOrders,
                )

            assertEquals(ExecutionStatus.REJECTED, result.status)
            assertTrue(result is ExecutionResult.Rejected)
            assertTrue((result as ExecutionResult.Rejected).validationErrors.any { it.contains("Order already exists for signal") })
        }

    @Test
    fun `test execution with ATR-based stops`() =
        runTest {
            val signal = createTestSignal(direction = Direction.LONG, confidence = 0.85)
            val config =
                ExecutionConfig(
                    mode = ExecutionMode.AUTO,
                    minSignalConfidence = 0.70,
                    requireConfirmation = false,
                    stopLossMethod = StopLossMethod.ATR_BASED,
                    stopLossAtrMultiple = 2.0,
                    takeProfitMethod = TakeProfitMethod.ATR_BASED,
                    takeProfitAtrMultiple = 3.0,
                )
            val accountBalance = 10000.0
            val currentPrice = 100.0
            val atr = 1.5

            val result =
                executor.execute(
                    signal = signal,
                    config = config,
                    accountBalance = accountBalance,
                    currentPrice = currentPrice,
                    existingPositions = emptyList(),
                    existingOrders = emptyList(),
                    atr = atr,
                )

            assertEquals(ExecutionStatus.SUCCESS, result.status)
            assertNotNull(result.stopLossPrice)
            assertNotNull(result.takeProfitPrice)

            // LONG with ATR: SL = 100 - (1.5 * 2) = 97
            assertEquals(97.0, result.stopLossPrice!!, 0.1)

            // TP = 100 + (1.5 * 3) = 104.5
            assertEquals(104.5, result.takeProfitPrice!!, 0.1)
        }

    @Test
    fun `test execution with Kelly Criterion sizing`() =
        runTest {
            val signal = createTestSignal(confidence = 0.85)
            val config =
                ExecutionConfig(
                    mode = ExecutionMode.AUTO,
                    minSignalConfidence = 0.70,
                    requireConfirmation = false,
                    positionSizingMethod = PositionSizingMethod.KELLY_CRITERION,
                    kellyFraction = 0.25, // Quarter Kelly
                    stopLossMethod = StopLossMethod.FIXED_PERCENTAGE,
                    stopLossPercentage = 0.02,
                    takeProfitMethod = TakeProfitMethod.FIXED_PERCENTAGE,
                    takeProfitPercentage = 0.05,
                )
            val accountBalance = 10000.0
            val currentPrice = 100.0
            val winRate = 0.60
            val profitFactor = 2.0

            val result =
                executor.execute(
                    signal = signal,
                    config = config,
                    accountBalance = accountBalance,
                    currentPrice = currentPrice,
                    existingPositions = emptyList(),
                    existingOrders = emptyList(),
                    winRate = winRate,
                    profitFactor = profitFactor,
                )

            assertEquals(ExecutionStatus.SUCCESS, result.status)
            assertNotNull(result.positionSize)
            // Kelly fraction calculation verified in PositionSizerTest
            assertTrue(result.positionSize!! > 0)
        }

    @Test
    fun `test execution failed with invalid config`() =
        runTest {
            val signal = createTestSignal(confidence = 0.85)
            val config =
                ExecutionConfig(
                    mode = ExecutionMode.AUTO,
                    minSignalConfidence = 1.5, // Invalid: > 1.0
                    requireConfirmation = false,
                )
            val accountBalance = 10000.0
            val currentPrice = 100.0

            val result =
                executor.execute(
                    signal = signal,
                    config = config,
                    accountBalance = accountBalance,
                    currentPrice = currentPrice,
                )

            assertEquals(ExecutionStatus.FAILED, result.status)
            assertTrue(result is ExecutionResult.Failed)
            assertTrue((result as ExecutionResult.Failed).error.contains("Invalid configuration"))
        }

    @Test
    fun `test execution with blacklisted asset`() =
        runTest {
            val signal = createTestSignal(symbol = "SHIB/USD", confidence = 0.85)
            val config =
                ExecutionConfig(
                    mode = ExecutionMode.AUTO,
                    minSignalConfidence = 0.70,
                    assetBlacklist = listOf("SHIB/USD", "DOGE/USD"),
                )
            val accountBalance = 10000.0
            val currentPrice = 100.0

            val result =
                executor.execute(
                    signal = signal,
                    config = config,
                    accountBalance = accountBalance,
                    currentPrice = currentPrice,
                    existingPositions = emptyList(),
                    existingOrders = emptyList(),
                )

            assertEquals(ExecutionStatus.REJECTED, result.status)
            assertTrue(result is ExecutionResult.Rejected)
            val rejectedResult = result as ExecutionResult.Rejected
            assertTrue(rejectedResult.validationErrors.any { it.contains("blacklist") })
        }

    @Test
    fun `test batch execution processes multiple signals`() =
        runTest {
            val signals =
                listOf(
                    createTestSignal(signalId = "SIG-1", symbol = "BTC/USD", confidence = 0.85),
                    createTestSignal(signalId = "SIG-2", symbol = "ETH/USD", confidence = 0.80),
                    createTestSignal(signalId = "SIG-3", symbol = "SOL/USD", confidence = 0.90),
                )
            val config =
                ExecutionConfig(
                    mode = ExecutionMode.AUTO,
                    minSignalConfidence = 0.70,
                    requireConfirmation = false,
                    stopLossMethod = StopLossMethod.FIXED_PERCENTAGE,
                    stopLossPercentage = 0.02,
                    takeProfitMethod = TakeProfitMethod.FIXED_PERCENTAGE,
                    takeProfitPercentage = 0.05,
                )
            val accountBalance = 100000.0

            val result =
                executor.executeBatch(
                    signals = signals,
                    config = config,
                    accountBalance = accountBalance,
                    getCurrentPrice = { symbol -> 100.0 }, // Mock price provider
                    getExistingPositions = { emptyList() },
                    getExistingOrders = { emptyList() },
                )

            assertEquals(3, result.results.size)
            // Verify batch completed
            assertTrue(result.results.all { it.executedAt != null })
        }

    @Test
    fun `test batch execution handles price fetch failure`() =
        runTest {
            val signals =
                listOf(
                    createTestSignal(signalId = "SIG-1", symbol = "BTC/USD", confidence = 0.85),
                    createTestSignal(signalId = "SIG-2", symbol = "UNKNOWN/USD", confidence = 0.80),
                )
            val config =
                ExecutionConfig(
                    mode = ExecutionMode.AUTO,
                    minSignalConfidence = 0.70,
                    requireConfirmation = false,
                    stopLossMethod = StopLossMethod.FIXED_PERCENTAGE,
                    stopLossPercentage = 0.02,
                    takeProfitMethod = TakeProfitMethod.FIXED_PERCENTAGE,
                    takeProfitPercentage = 0.05,
                )
            val accountBalance = 100000.0

            val result =
                executor.executeBatch(
                    signals = signals,
                    config = config,
                    accountBalance = accountBalance,
                    getCurrentPrice = { symbol ->
                        if (symbol == "BTC/USD") 100.0 else null // UNKNOWN/USD fails
                    },
                    getExistingPositions = { emptyList() },
                    getExistingOrders = { emptyList() },
                )

            assertNotNull(result)
            assertEquals(2, result.results.size)

            // First signal should succeed
            assertEquals(ExecutionStatus.SUCCESS, result.results[0].status)

            // Second signal should fail due to no price
            assertEquals(ExecutionStatus.FAILED, result.results[1].status)
            assertTrue(result.results[1] is ExecutionResult.Failed)
            assertTrue((result.results[1] as ExecutionResult.Failed).error.contains("Failed to get current price"))
        }

    @Test
    fun `test execution tracks duration`() =
        runTest {
            val signal = createTestSignal(confidence = 0.85)
            val config =
                ExecutionConfig(
                    mode = ExecutionMode.AUTO,
                    stopLossMethod = StopLossMethod.FIXED_PERCENTAGE,
                    stopLossPercentage = 0.02,
                    takeProfitMethod = TakeProfitMethod.FIXED_PERCENTAGE,
                    takeProfitPercentage = 0.05,
                )
            val accountBalance = 10000.0
            val currentPrice = 100.0

            val result =
                executor.execute(
                    signal = signal,
                    config = config,
                    accountBalance = accountBalance,
                    currentPrice = currentPrice,
                    existingPositions = emptyList(),
                    existingOrders = emptyList(),
                )

            assertNotNull(result.executionDurationMs)
            assertTrue(result.executionDurationMs!! >= 0)
        }

    // Helper functions to create test data

    private fun createTestSignal(
        signalId: String = "TEST-SIGNAL-001",
        symbol: String = "BTC/USD",
        direction: Direction = Direction.LONG,
        confidence: Double = 0.85,
    ): Signal {
        return Signal(
            signalId = signalId,
            signalType = SignalType.ENTRY,
            symbol = symbol,
            timeframe = Timeframe.H1,
            direction = direction,
            strength = 0.75,
            price = 100.0,
            stopLoss = if (direction == Direction.LONG) 95.0 else 105.0,
            takeProfit = if (direction == Direction.LONG) 110.0 else 90.0,
            confidence = confidence,
            timestamp = Clock.System.now(),
            strategyName = "TestStrategy",
        )
    }

    private fun createTestPosition(
        symbol: String = "BTC/USD",
        quantity: Double = 10.0,
        entryPrice: Double = 100.0,
    ): Position {
        return Position(
            positionId = "POS-${symbol.hashCode()}",
            symbol = symbol,
            side = OrderSide.BUY,
            quantity = quantity,
            entryPrice = entryPrice,
            currentPrice = entryPrice,
            status = PositionStatus.OPEN,
            openedAt = Clock.System.now(),
            orderId = "ORDER-001",
            unrealizedPnL = 0.0,
            realizedPnL = 0.0,
        )
    }

    private fun createTestOrder(
        symbol: String = "BTC/USD",
        side: OrderSide = OrderSide.BUY,
        status: OrderStatus = OrderStatus.PENDING,
        signalId: String? = null,
    ): Order {
        return Order(
            orderId = "ORDER-${signalId ?: "001"}",
            symbol = symbol,
            side = side,
            orderType = xyz.fkstrading.shared.domain.models.OrderType.MARKET,
            quantity = 10.0,
            status = status,
            timeInForce = TimeInForce.GTC,
            timestamp = Clock.System.now(),
            signalId = signalId,
        )
    }
}
