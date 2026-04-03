package xyz.fkstrading.shared.domain.strategy

import kotlinx.datetime.Clock
import xyz.fkstrading.shared.domain.models.Direction
import xyz.fkstrading.shared.domain.models.Signal
import xyz.fkstrading.shared.domain.models.SignalType
import xyz.fkstrading.shared.domain.models.Timeframe
import xyz.fkstrading.shared.domain.strategy.models.ExecutionConfig
import xyz.fkstrading.shared.domain.strategy.models.PositionSizingMethod
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit tests for PositionSizer.
 */
class PositionSizerTest {
    private val positionSizer = PositionSizer()

    @Test
    fun `test fixed position sizing`() {
        val config =
            ExecutionConfig(
                positionSizingMethod = PositionSizingMethod.FIXED_AMOUNT,
                fixedPositionSize = 10.0,
            )
        val signal = createTestSignal()
        val accountBalance = 10000.0
        val currentPrice = 100.0

        val result =
            positionSizer.calculatePositionSize(
                signal = signal,
                config = config,
                accountBalance = accountBalance,
                currentPrice = currentPrice,
            )

        assertTrue(result is PositionSizeResult.Success)
        assertEquals(10.0, (result as PositionSizeResult.Success).quantity, 0.001)
        assertEquals(PositionSizingMethod.FIXED_AMOUNT, result.method)
    }

    @Test
    fun `test percentage-based position sizing`() {
        val config =
            ExecutionConfig(
                positionSizingMethod = PositionSizingMethod.FIXED_PERCENTAGE,
                accountPercentage = 0.10, // 10% of account
            )
        val signal = createTestSignal()
        val accountBalance = 10000.0
        val currentPrice = 100.0

        val result =
            positionSizer.calculatePositionSize(
                signal = signal,
                config = config,
                accountBalance = accountBalance,
                currentPrice = currentPrice,
            )

        assertTrue(result is PositionSizeResult.Success)
        // 10% of $10,000 = $1,000
        // $1,000 / $100 per unit = 10 units
        assertEquals(10.0, (result as PositionSizeResult.Success).quantity, 0.001)
        assertEquals(PositionSizingMethod.FIXED_PERCENTAGE, result.method)
    }

    @Test
    fun `test risk-based position sizing with stop-loss`() {
        val config =
            ExecutionConfig(
                positionSizingMethod = PositionSizingMethod.RISK_BASED,
                riskPerTrade = 0.02, // Risk 2% of account
            )
        val signal = createTestSignal(direction = Direction.LONG)
        val accountBalance = 10000.0
        val currentPrice = 100.0
        val stopLossPrice = 98.0 // 2% stop-loss

        val result =
            positionSizer.calculatePositionSize(
                signal = signal,
                config = config,
                accountBalance = accountBalance,
                currentPrice = currentPrice,
                stopLossPrice = stopLossPrice,
            )

        assertTrue(result is PositionSizeResult.Success)
        // Risk amount = 2% of $10,000 = $200
        // Stop distance = $100 - $98 = $2 per unit
        // Position size = $200 / $2 = 100 units
        assertEquals(100.0, (result as PositionSizeResult.Success).quantity, 0.001)
        assertEquals(PositionSizingMethod.RISK_BASED, result.method)
        assertEquals(200.0, result.riskAmount ?: 0.0, 0.001)
    }

    @Test
    fun `test risk-based sizing fails without stop-loss`() {
        val config =
            ExecutionConfig(
                positionSizingMethod = PositionSizingMethod.RISK_BASED,
                riskPerTrade = 0.02,
            )
        val signal = createTestSignal()
        val accountBalance = 10000.0
        val currentPrice = 100.0

        val result =
            positionSizer.calculatePositionSize(
                signal = signal,
                config = config,
                accountBalance = accountBalance,
                currentPrice = currentPrice,
                stopLossPrice = null,
            )

        assertTrue(result is PositionSizeResult.Error)
        assertTrue((result as PositionSizeResult.Error).message.contains("Stop-loss"))
    }

    @Test
    fun `test kelly criterion position sizing`() {
        val config =
            ExecutionConfig(
                positionSizingMethod = PositionSizingMethod.KELLY_CRITERION,
                kellyFraction = 0.25, // Quarter Kelly
            )
        val signal = createTestSignal()
        val accountBalance = 10000.0
        val currentPrice = 100.0
        val winRate = 0.60 // 60% win rate
        val profitFactor = 2.0 // Average win 2x average loss

        val result =
            positionSizer.calculatePositionSize(
                signal = signal,
                config = config,
                accountBalance = accountBalance,
                currentPrice = currentPrice,
                winRate = winRate,
                profitFactor = profitFactor,
            )

        assertTrue(result is PositionSizeResult.Success)
        // Full Kelly: (p * b - q) / b = (0.6 * 2 - 0.4) / 2 = 0.8 / 2 = 0.4 (40%)
        // Fractional Kelly (0.25): 0.4 * 0.25 = 0.1 (10%)
        // Position value: $10,000 * 0.1 = $1,000
        // Quantity: $1,000 / $100 = 10 units
        assertEquals(10.0, (result as PositionSizeResult.Success).quantity, 0.001)
        assertEquals(PositionSizingMethod.KELLY_CRITERION, result.method)
    }

    @Test
    fun `test kelly criterion with negative edge returns error`() {
        val config =
            ExecutionConfig(
                positionSizingMethod = PositionSizingMethod.KELLY_CRITERION,
                kellyFraction = 0.25,
            )
        val signal = createTestSignal()
        val accountBalance = 10000.0
        val currentPrice = 100.0
        val winRate = 0.30 // 30% win rate (losing edge)
        val profitFactor = 1.5 // Kelly = (0.3 * 1.5 - 0.7) / 1.5 = -0.25/1.5 = -0.167

        val result =
            positionSizer.calculatePositionSize(
                signal = signal,
                config = config,
                accountBalance = accountBalance,
                currentPrice = currentPrice,
                winRate = winRate,
                profitFactor = profitFactor,
            )

        assertTrue(result is PositionSizeResult.Error)
        assertTrue((result as PositionSizeResult.Error).message.contains("negative"))
    }

    @Test
    fun `test kelly criterion fails without statistics`() {
        val config =
            ExecutionConfig(
                positionSizingMethod = PositionSizingMethod.KELLY_CRITERION,
                kellyFraction = 0.25,
            )
        val signal = createTestSignal()
        val accountBalance = 10000.0
        val currentPrice = 100.0

        val result =
            positionSizer.calculatePositionSize(
                signal = signal,
                config = config,
                accountBalance = accountBalance,
                currentPrice = currentPrice,
                winRate = null,
                profitFactor = null,
            )

        assertTrue(result is PositionSizeResult.Error)
        assertTrue((result as PositionSizeResult.Error).message.contains("Win rate"))
    }

    @Test
    fun `test validation rejects negative account balance`() {
        val config =
            ExecutionConfig(
                positionSizingMethod = PositionSizingMethod.FIXED_AMOUNT,
                fixedPositionSize = 10.0,
            )
        val signal = createTestSignal()
        val accountBalance = -1000.0
        val currentPrice = 100.0

        val result =
            positionSizer.calculatePositionSize(
                signal = signal,
                config = config,
                accountBalance = accountBalance,
                currentPrice = currentPrice,
            )

        assertTrue(result is PositionSizeResult.Error)
        assertTrue((result as PositionSizeResult.Error).message.contains("balance"))
    }

    @Test
    fun `test validation rejects zero or negative price`() {
        val config =
            ExecutionConfig(
                positionSizingMethod = PositionSizingMethod.FIXED_AMOUNT,
                fixedPositionSize = 10.0,
            )
        val signal = createTestSignal()
        val accountBalance = 10000.0
        val currentPrice = 0.0

        val result =
            positionSizer.calculatePositionSize(
                signal = signal,
                config = config,
                accountBalance = accountBalance,
                currentPrice = currentPrice,
            )

        assertTrue(result is PositionSizeResult.Error)
        assertTrue((result as PositionSizeResult.Error).message.contains("price"))
    }

    @Test
    fun `test position size validation detects excessive size`() {
        val quantity = 200.0
        val currentPrice = 100.0
        val accountBalance = 10000.0
        val maxPositionPercent = 0.30 // Max 30% of account

        val errors =
            positionSizer.validatePositionSize(
                quantity = quantity,
                currentPrice = currentPrice,
                accountBalance = accountBalance,
                maxPositionPercent = maxPositionPercent,
            )

        // Position value = 200 * $100 = $20,000
        // Position % = $20,000 / $10,000 = 200% (exceeds 30% max)
        assertTrue(errors.isNotEmpty())
        assertTrue(errors.any { it.contains("exceeds") })
    }

    @Test
    fun `test position size validation allows reasonable size`() {
        val quantity = 10.0
        val currentPrice = 100.0
        val accountBalance = 10000.0
        val maxPositionPercent = 0.30

        val errors =
            positionSizer.validatePositionSize(
                quantity = quantity,
                currentPrice = currentPrice,
                accountBalance = accountBalance,
                maxPositionPercent = maxPositionPercent,
            )

        // Position value = 10 * $100 = $1,000
        // Position % = $1,000 / $10,000 = 10% (within 30% max)
        assertTrue(errors.isEmpty())
    }

    @Test
    fun `test position size validation detects negative quantity`() {
        val quantity = -10.0
        val currentPrice = 100.0
        val accountBalance = 10000.0

        val errors =
            positionSizer.validatePositionSize(
                quantity = quantity,
                currentPrice = currentPrice,
                accountBalance = accountBalance,
            )

        assertTrue(errors.isNotEmpty())
        assertTrue(errors.any { it.contains("positive") })
    }

    @Test
    fun `test risk-based sizing with tight stop-loss`() {
        val config =
            ExecutionConfig(
                positionSizingMethod = PositionSizingMethod.RISK_BASED,
                riskPerTrade = 0.01, // Risk 1% of account
            )
        val signal = createTestSignal(direction = Direction.LONG)
        val accountBalance = 10000.0
        val currentPrice = 100.0
        val stopLossPrice = 99.0 // 1% stop-loss (tight)

        val result =
            positionSizer.calculatePositionSize(
                signal = signal,
                config = config,
                accountBalance = accountBalance,
                currentPrice = currentPrice,
                stopLossPrice = stopLossPrice,
            )

        assertTrue(result is PositionSizeResult.Success)
        // Risk amount = 1% of $10,000 = $100
        // Stop distance = $100 - $99 = $1 per unit
        // Position size = $100 / $1 = 100 units
        assertEquals(100.0, (result as PositionSizeResult.Success).quantity, 0.001)
    }

    @Test
    fun `test risk-based sizing with wide stop-loss`() {
        val config =
            ExecutionConfig(
                positionSizingMethod = PositionSizingMethod.RISK_BASED,
                riskPerTrade = 0.02, // Risk 2% of account
            )
        val signal = createTestSignal(direction = Direction.LONG)
        val accountBalance = 10000.0
        val currentPrice = 100.0
        val stopLossPrice = 90.0 // 10% stop-loss (wide)

        val result =
            positionSizer.calculatePositionSize(
                signal = signal,
                config = config,
                accountBalance = accountBalance,
                currentPrice = currentPrice,
                stopLossPrice = stopLossPrice,
            )

        assertTrue(result is PositionSizeResult.Success)
        // Risk amount = 2% of $10,000 = $200
        // Stop distance = $100 - $90 = $10 per unit
        // Position size = $200 / $10 = 20 units
        assertEquals(20.0, (result as PositionSizeResult.Success).quantity, 0.001)
    }

    @Test
    fun `test percentage sizing with different account sizes`() {
        val config =
            ExecutionConfig(
                positionSizingMethod = PositionSizingMethod.FIXED_PERCENTAGE,
                accountPercentage = 0.05, // 5% of account
            )
        val signal = createTestSignal()
        val currentPrice = 50.0

        // Small account
        val result1 =
            positionSizer.calculatePositionSize(
                signal = signal,
                config = config,
                accountBalance = 1000.0,
                currentPrice = currentPrice,
            )
        assertTrue(result1 is PositionSizeResult.Success)
        // 5% of $1,000 = $50; $50 / $50 = 1 unit
        assertEquals(1.0, (result1 as PositionSizeResult.Success).quantity, 0.001)

        // Large account
        val result2 =
            positionSizer.calculatePositionSize(
                signal = signal,
                config = config,
                accountBalance = 100000.0,
                currentPrice = currentPrice,
            )
        assertTrue(result2 is PositionSizeResult.Success)
        // 5% of $100,000 = $5,000; $5,000 / $50 = 100 units
        assertEquals(100.0, (result2 as PositionSizeResult.Success).quantity, 0.001)
    }

    @Test
    fun `test kelly criterion caps at 20 percent for safety`() {
        val config =
            ExecutionConfig(
                positionSizingMethod = PositionSizingMethod.KELLY_CRITERION,
                kellyFraction = 1.0, // Full Kelly (risky)
            )
        val signal = createTestSignal()
        val accountBalance = 10000.0
        val currentPrice = 100.0
        val winRate = 0.70 // 70% win rate (very high)
        val profitFactor = 3.0 // 3:1 profit factor (very high)

        val result =
            positionSizer.calculatePositionSize(
                signal = signal,
                config = config,
                accountBalance = accountBalance,
                currentPrice = currentPrice,
                winRate = winRate,
                profitFactor = profitFactor,
            )

        assertTrue(result is PositionSizeResult.Success)
        // Full Kelly would be very high, but should be capped at 20%
        val positionValue = (result as PositionSizeResult.Success).quantity * currentPrice
        val positionPercent = positionValue / accountBalance
        assertTrue(positionPercent <= 0.21) // Allow small rounding error
    }

    // Helper function to create test signals
    private fun createTestSignal(
        signalId: String = "TEST-SIGNAL-001",
        symbol: String = "BTC/USD",
        direction: Direction = Direction.LONG,
        confidence: Double = 0.8,
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
        )
    }
}
