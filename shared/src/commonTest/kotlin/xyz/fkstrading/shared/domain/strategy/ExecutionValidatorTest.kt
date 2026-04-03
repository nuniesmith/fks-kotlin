package xyz.fkstrading.shared.domain.strategy

import kotlinx.datetime.Clock
import xyz.fkstrading.shared.domain.models.*
import xyz.fkstrading.shared.domain.strategy.models.*
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Unit tests for ExecutionValidator.
 */
class ExecutionValidatorTest {
    private val validator = ExecutionValidator()

    @Test
    fun `test validation passes with valid inputs`() {
        val signal = createTestSignal(confidence = 0.80)
        val config = ExecutionConfig(minSignalConfidence = 0.70)
        val accountBalance = 10000.0
        val currentPrice = 100.0
        val positionSize = 10.0
        val stopLossPrice = 95.0

        val result =
            validator.validate(
                signal = signal,
                config = config,
                accountBalance = accountBalance,
                currentPrice = currentPrice,
                existingPositions = emptyList(),
                existingOrders = emptyList(),
                positionSize = positionSize,
                stopLossPrice = stopLossPrice,
            )

        assertTrue(result.isValid())
        assertTrue(result is ValidationResult.Valid)
    }

    @Test
    fun `test validation fails with low signal confidence`() {
        val signal = createTestSignal(confidence = 0.50)
        val config = ExecutionConfig(minSignalConfidence = 0.70)
        val accountBalance = 10000.0
        val currentPrice = 100.0
        val positionSize = 10.0

        val result =
            validator.validate(
                signal = signal,
                config = config,
                accountBalance = accountBalance,
                currentPrice = currentPrice,
                existingPositions = emptyList(),
                existingOrders = emptyList(),
                positionSize = positionSize,
            )

        assertTrue(result.isInvalid())
        assertTrue(result is ValidationResult.Invalid)
        val errors = (result as ValidationResult.Invalid).errors
        assertTrue(errors.any { it.contains("confidence") && it.contains("below minimum") })
    }

    @Test
    fun `test validation warns when confidence is borderline`() {
        val signal = createTestSignal(confidence = 0.75)
        val config = ExecutionConfig(minSignalConfidence = 0.70)
        val accountBalance = 10000.0
        val currentPrice = 100.0
        val positionSize = 10.0

        val result =
            validator.validate(
                signal = signal,
                config = config,
                accountBalance = accountBalance,
                currentPrice = currentPrice,
                existingPositions = emptyList(),
                existingOrders = emptyList(),
                positionSize = positionSize,
            )

        assertTrue(result.isValid())
        assertTrue(result is ValidationResult.Valid)
        val warnings = (result as ValidationResult.Valid).warnings
        assertTrue(warnings.any { it.contains("close to minimum threshold") })
    }

    @Test
    fun `test validation fails with blank symbol`() {
        val signal = createTestSignal(symbol = "")
        val config = ExecutionConfig()
        val accountBalance = 10000.0
        val currentPrice = 100.0
        val positionSize = 10.0

        val result =
            validator.validate(
                signal = signal,
                config = config,
                accountBalance = accountBalance,
                currentPrice = currentPrice,
                existingPositions = emptyList(),
                existingOrders = emptyList(),
                positionSize = positionSize,
            )

        assertTrue(result.isInvalid())
        val errors = (result as ValidationResult.Invalid).errors
        assertTrue(errors.any { it.contains("symbol is blank") })
    }

    @Test
    fun `test validation fails with insufficient balance`() {
        val signal = createTestSignal()
        val config = ExecutionConfig()
        val accountBalance = 500.0 // Not enough for 10 units @ $100
        val currentPrice = 100.0
        val positionSize = 10.0

        val result =
            validator.validate(
                signal = signal,
                config = config,
                accountBalance = accountBalance,
                currentPrice = currentPrice,
                existingPositions = emptyList(),
                existingOrders = emptyList(),
                positionSize = positionSize,
            )

        assertTrue(result.isInvalid())
        val errors = (result as ValidationResult.Invalid).errors
        assertTrue(errors.any { it.contains("Insufficient balance") })
    }

    @Test
    fun `test validation fails with zero or negative balance`() {
        val signal = createTestSignal()
        val config = ExecutionConfig()
        val accountBalance = 0.0
        val currentPrice = 100.0
        val positionSize = 10.0

        val result =
            validator.validate(
                signal = signal,
                config = config,
                accountBalance = accountBalance,
                currentPrice = currentPrice,
                existingPositions = emptyList(),
                existingOrders = emptyList(),
                positionSize = positionSize,
            )

        assertTrue(result.isInvalid())
        val errors = (result as ValidationResult.Invalid).errors
        assertTrue(errors.any { it.contains("balance is zero or negative") })
    }

    @Test
    fun `test validation fails when max positions reached`() {
        val signal = createTestSignal(symbol = "ETH/USD")
        val config = ExecutionConfig(maxPositions = 3)
        val accountBalance = 10000.0
        val currentPrice = 100.0
        val positionSize = 10.0

        // Create 3 existing positions (at limit)
        val existingPositions =
            listOf(
                createTestPosition(symbol = "BTC/USD"),
                createTestPosition(symbol = "SOL/USD"),
                createTestPosition(symbol = "AVAX/USD"),
            )

        val result =
            validator.validate(
                signal = signal,
                config = config,
                accountBalance = accountBalance,
                currentPrice = currentPrice,
                existingPositions = existingPositions,
                existingOrders = emptyList(),
                positionSize = positionSize,
            )

        assertTrue(result.isInvalid())
        val errors = (result as ValidationResult.Invalid).errors
        assertTrue(errors.any { it.contains("Maximum positions limit reached") })
    }

    @Test
    fun `test validation fails when max positions per asset reached`() {
        val signal = createTestSignal(symbol = "BTC/USD")
        val config = ExecutionConfig(maxPositionsPerAsset = 2)
        val accountBalance = 10000.0
        val currentPrice = 100.0
        val positionSize = 10.0

        // Create 2 existing BTC positions (at limit)
        val existingPositions =
            listOf(
                createTestPosition(symbol = "BTC/USD"),
                createTestPosition(symbol = "BTC/USD"),
            )

        val result =
            validator.validate(
                signal = signal,
                config = config,
                accountBalance = accountBalance,
                currentPrice = currentPrice,
                existingPositions = existingPositions,
                existingOrders = emptyList(),
                positionSize = positionSize,
            )

        assertTrue(result.isInvalid())
        val errors = (result as ValidationResult.Invalid).errors
        assertTrue(errors.any { it.contains("Maximum positions in BTC/USD reached") })
    }

    @Test
    fun `test validation warns on large position size`() {
        val signal = createTestSignal()
        val config = ExecutionConfig()
        val accountBalance = 10000.0
        val currentPrice = 100.0
        val positionSize = 25.0 // $2,500 = 25% of account

        val result =
            validator.validate(
                signal = signal,
                config = config,
                accountBalance = accountBalance,
                currentPrice = currentPrice,
                existingPositions = emptyList(),
                existingOrders = emptyList(),
                positionSize = positionSize,
            )

        assertTrue(result.isValid())
        val warnings = (result as ValidationResult.Valid).warnings
        assertTrue(warnings.any { it.contains("Large position") })
    }

    @Test
    fun `test validation fails on excessive position size`() {
        val signal = createTestSignal()
        val config = ExecutionConfig()
        val accountBalance = 10000.0
        val currentPrice = 100.0
        val positionSize = 60.0 // $6,000 = 60% of account (exceeds 50% max)

        val result =
            validator.validate(
                signal = signal,
                config = config,
                accountBalance = accountBalance,
                currentPrice = currentPrice,
                existingPositions = emptyList(),
                existingOrders = emptyList(),
                positionSize = positionSize,
            )

        assertTrue(result.isInvalid())
        val errors = (result as ValidationResult.Invalid).errors
        assertTrue(errors.any { it.contains("Position too large") })
    }

    @Test
    fun `test validation checks risk per trade`() {
        val signal = createTestSignal(direction = Direction.LONG)
        val config = ExecutionConfig(riskPerTrade = 0.01) // 1% max risk
        val accountBalance = 10000.0
        val currentPrice = 100.0
        val positionSize = 100.0
        val stopLossPrice = 95.0 // $5 risk per unit = $500 total (5% risk)

        val result =
            validator.validate(
                signal = signal,
                config = config,
                accountBalance = accountBalance,
                currentPrice = currentPrice,
                existingPositions = emptyList(),
                existingOrders = emptyList(),
                positionSize = positionSize,
                stopLossPrice = stopLossPrice,
            )

        assertTrue(result.isInvalid())
        val errors = (result as ValidationResult.Invalid).errors
        assertTrue(errors.any { it.contains("Risk per trade") && it.contains("exceeds") })
    }

    @Test
    fun `test validation warns when risk is above configured limit`() {
        val signal = createTestSignal(direction = Direction.LONG)
        val config = ExecutionConfig(riskPerTrade = 0.01) // 1% max risk (error at 1.5%)
        val accountBalance = 10000.0
        val currentPrice = 100.0
        val positionSize = 27.0
        val stopLossPrice = 96.0 // $4 risk per unit = $108 total (1.08% risk, above 1% but below 1.5%)

        val result =
            validator.validate(
                signal = signal,
                config = config,
                accountBalance = accountBalance,
                currentPrice = currentPrice,
                existingPositions = emptyList(),
                existingOrders = emptyList(),
                positionSize = positionSize,
                stopLossPrice = stopLossPrice,
            )

        // Should be valid but with warnings (1.08% > 1% limit but < 1.5% error threshold)
        // Position size = 27 * $100 = $2,700 (27% of account, under 50% limit)
        assertTrue(result.isValid())
        val warnings = (result as ValidationResult.Valid).warnings
        assertTrue(warnings.any { it.contains("Risk per trade") && it.contains("above configured limit") })
    }

    @Test
    fun `test validation warns on high total exposure`() {
        val signal = createTestSignal(direction = Direction.LONG)
        val config =
            ExecutionConfig(
                maxPositionsPerAsset = 3, // Allow multiple positions per asset for this test
            )
        val accountBalance = 10000.0
        val currentPrice = 100.0
        val positionSize = 30.0 // $3,000 (30% of account, under 50% limit)
        val stopLossPrice = 98.0 // Valid stop-loss for LONG (2% risk = $60, 0.6% of account)

        // Existing positions totaling $14,000 (1.4x account)
        // Total with new position = $17,000 (1.7x account, clearly above 1.5x threshold)
        val existingPositions =
            listOf(
                createTestPosition(symbol = "BTC/USD", quantity = 70.0, entryPrice = 100.0),
                createTestPosition(symbol = "ETH/USD", quantity = 35.0, entryPrice = 200.0),
            )

        val result =
            validator.validate(
                signal = signal,
                config = config,
                accountBalance = accountBalance,
                currentPrice = currentPrice,
                existingPositions = existingPositions,
                existingOrders = emptyList(),
                positionSize = positionSize,
                stopLossPrice = stopLossPrice,
            )

        // Should be valid with warnings
        assertTrue(result.isValid())
        val warnings = (result as ValidationResult.Valid).warnings

        // Should have exposure warning since 17000/10000 = 1.7 > 1.5 threshold
        assertTrue(
            warnings.any { it.contains("High total exposure") },
            "Expected 'High total exposure' warning",
        )
    }

    @Test
    fun `test validation fails on excessive total exposure`() {
        val signal = createTestSignal()
        val config = ExecutionConfig()
        val accountBalance = 10000.0
        val currentPrice = 100.0
        val positionSize = 50.0 // $5,000

        // Existing positions totaling $26,000 (2.6x account)
        // Total would be $31,000 (3.1x account, exceeds 3.0x max)
        val existingPositions =
            listOf(
                createTestPosition(symbol = "BTC/USD", quantity = 100.0, entryPrice = 100.0),
                createTestPosition(symbol = "ETH/USD", quantity = 80.0, entryPrice = 200.0),
            )

        val result =
            validator.validate(
                signal = signal,
                config = config,
                accountBalance = accountBalance,
                currentPrice = currentPrice,
                existingPositions = existingPositions,
                existingOrders = emptyList(),
                positionSize = positionSize,
            )

        assertTrue(result.isInvalid())
        val errors = (result as ValidationResult.Invalid).errors
        assertTrue(errors.any { it.contains("Excessive total exposure") })
    }

    @Test
    fun `test validation fails with duplicate signal order`() {
        val signal = createTestSignal(signalId = "SIGNAL-123")
        val config = ExecutionConfig()
        val accountBalance = 10000.0
        val currentPrice = 100.0
        val positionSize = 10.0

        // Order already exists for this signal
        val existingOrders =
            listOf(
                createTestOrder(signalId = "SIGNAL-123", status = OrderStatus.PENDING),
            )

        val result =
            validator.validate(
                signal = signal,
                config = config,
                accountBalance = accountBalance,
                currentPrice = currentPrice,
                existingPositions = emptyList(),
                existingOrders = existingOrders,
                positionSize = positionSize,
            )

        assertTrue(result.isInvalid())
        val errors = (result as ValidationResult.Invalid).errors
        assertTrue(errors.any { it.contains("Order already exists for signal") })
    }

    @Test
    fun `test validation warns when similar order exists`() {
        val signal = createTestSignal(symbol = "BTC/USD", direction = Direction.LONG)
        val config = ExecutionConfig()
        val accountBalance = 10000.0
        val currentPrice = 100.0
        val positionSize = 10.0

        // Existing BUY order for same symbol (different signal)
        val existingOrders =
            listOf(
                createTestOrder(
                    symbol = "BTC/USD",
                    side = OrderSide.BUY,
                    status = OrderStatus.PENDING,
                    signalId = "OTHER-SIGNAL",
                ),
            )

        val result =
            validator.validate(
                signal = signal,
                config = config,
                accountBalance = accountBalance,
                currentPrice = currentPrice,
                existingPositions = emptyList(),
                existingOrders = existingOrders,
                positionSize = positionSize,
            )

        assertTrue(result.isValid())
        val warnings = (result as ValidationResult.Valid).warnings
        assertTrue(warnings.any { it.contains("Active BUY order(s) already exist") })
    }

    @Test
    fun `test validation fails with blacklisted asset`() {
        val signal = createTestSignal(symbol = "DOGE/USD")
        val config =
            ExecutionConfig(
                assetBlacklist = listOf("DOGE/USD", "SHIB/USD"),
            )
        val accountBalance = 10000.0
        val currentPrice = 100.0
        val positionSize = 10.0

        val result =
            validator.validate(
                signal = signal,
                config = config,
                accountBalance = accountBalance,
                currentPrice = currentPrice,
                existingPositions = emptyList(),
                existingOrders = emptyList(),
                positionSize = positionSize,
            )

        assertTrue(result.isInvalid())
        val errors = (result as ValidationResult.Invalid).errors
        assertTrue(errors.any { it.contains("DOGE/USD is blacklisted") })
    }

    @Test
    fun `test validation fails when asset not in whitelist`() {
        val signal = createTestSignal(symbol = "XRP/USD")
        val config =
            ExecutionConfig(
                assetWhitelist = listOf("BTC/USD", "ETH/USD", "SOL/USD"),
            )
        val accountBalance = 10000.0
        val currentPrice = 100.0
        val positionSize = 10.0

        val result =
            validator.validate(
                signal = signal,
                config = config,
                accountBalance = accountBalance,
                currentPrice = currentPrice,
                existingPositions = emptyList(),
                existingOrders = emptyList(),
                positionSize = positionSize,
            )

        assertTrue(result.isInvalid())
        val errors = (result as ValidationResult.Invalid).errors
        assertTrue(errors.any { it.contains("not in whitelist") })
    }

    @Test
    fun `test validation fails with wrong stop-loss direction for long`() {
        val signal = createTestSignal(direction = Direction.LONG)
        val config = ExecutionConfig()
        val accountBalance = 10000.0
        val currentPrice = 100.0
        val positionSize = 10.0
        val stopLossPrice = 105.0 // Wrong: SL should be below entry for LONG

        val result =
            validator.validate(
                signal = signal,
                config = config,
                accountBalance = accountBalance,
                currentPrice = currentPrice,
                existingPositions = emptyList(),
                existingOrders = emptyList(),
                positionSize = positionSize,
                stopLossPrice = stopLossPrice,
            )

        assertTrue(result.isInvalid())
        val errors = (result as ValidationResult.Invalid).errors
        assertTrue(errors.any { it.contains("Stop-loss on wrong side") })
    }

    @Test
    fun `test validation fails with wrong stop-loss direction for short`() {
        val signal = createTestSignal(direction = Direction.SHORT)
        val config = ExecutionConfig()
        val accountBalance = 10000.0
        val currentPrice = 100.0
        val positionSize = 10.0
        val stopLossPrice = 95.0 // Wrong: SL should be above entry for SHORT

        val result =
            validator.validate(
                signal = signal,
                config = config,
                accountBalance = accountBalance,
                currentPrice = currentPrice,
                existingPositions = emptyList(),
                existingOrders = emptyList(),
                positionSize = positionSize,
                stopLossPrice = stopLossPrice,
            )

        assertTrue(result.isInvalid())
        val errors = (result as ValidationResult.Invalid).errors
        assertTrue(errors.any { it.contains("Stop-loss on wrong side") })
    }

    @Test
    fun `test validation warns on very tight stop-loss`() {
        val signal = createTestSignal(direction = Direction.LONG)
        val config = ExecutionConfig()
        val accountBalance = 10000.0
        val currentPrice = 100.0
        val positionSize = 10.0
        val stopLossPrice = 99.9 // Only 0.1% stop

        val result =
            validator.validate(
                signal = signal,
                config = config,
                accountBalance = accountBalance,
                currentPrice = currentPrice,
                existingPositions = emptyList(),
                existingOrders = emptyList(),
                positionSize = positionSize,
                stopLossPrice = stopLossPrice,
            )

        assertTrue(result.isValid())
        val warnings = (result as ValidationResult.Valid).warnings
        assertTrue(warnings.any { it.contains("Very tight stop-loss") })
    }

    @Test
    fun `test validation warns on wide stop-loss`() {
        val signal = createTestSignal(direction = Direction.LONG)
        val config = ExecutionConfig()
        val accountBalance = 10000.0
        val currentPrice = 100.0
        val positionSize = 10.0
        val stopLossPrice = 88.0 // 12% stop

        val result =
            validator.validate(
                signal = signal,
                config = config,
                accountBalance = accountBalance,
                currentPrice = currentPrice,
                existingPositions = emptyList(),
                existingOrders = emptyList(),
                positionSize = positionSize,
                stopLossPrice = stopLossPrice,
            )

        assertTrue(result.isValid())
        val warnings = (result as ValidationResult.Valid).warnings
        assertTrue(warnings.any { it.contains("Wide stop-loss") })
    }

    @Test
    fun `test validation fails on excessively wide stop-loss`() {
        val signal = createTestSignal(direction = Direction.LONG)
        val config = ExecutionConfig()
        val accountBalance = 10000.0
        val currentPrice = 100.0
        val positionSize = 10.0
        val stopLossPrice = 70.0 // 30% stop (exceeds 25% max)

        val result =
            validator.validate(
                signal = signal,
                config = config,
                accountBalance = accountBalance,
                currentPrice = currentPrice,
                existingPositions = emptyList(),
                existingOrders = emptyList(),
                positionSize = positionSize,
                stopLossPrice = stopLossPrice,
            )

        assertTrue(result.isInvalid())
        val errors = (result as ValidationResult.Invalid).errors
        assertTrue(errors.any { it.contains("Stop-loss too wide") })
    }

    @Test
    fun `test dry-run validation is less strict`() {
        val signal = createTestSignal(confidence = 0.50)
        val config = ExecutionConfig(minSignalConfidence = 0.70)

        val result =
            validator.validateForDryRun(
                signal = signal,
                config = config,
            )

        // Dry-run should only warn, not error on low confidence
        assertTrue(result.isValid())
        val warnings = (result as ValidationResult.Valid).warnings
        assertTrue(warnings.any { it.contains("confidence") })
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
            strength = confidence,
            price = 100.0,
            entryPrice = 100.0,
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
