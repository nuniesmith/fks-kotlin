package xyz.fkstrading.shared.domain.strategy

import kotlinx.datetime.Clock
import xyz.fkstrading.shared.domain.models.*
import xyz.fkstrading.shared.domain.strategy.models.ExecutionConfig
import xyz.fkstrading.shared.domain.strategy.models.ExecutionMode
import xyz.fkstrading.shared.domain.strategy.models.PositionSizingMethod
import xyz.fkstrading.shared.domain.strategy.models.StopLossMethod
import xyz.fkstrading.shared.domain.strategy.models.TakeProfitMethod
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import xyz.fkstrading.shared.domain.strategy.models.OrderType as StrategyOrderType

/**
 * Unit tests for OrderBuilder.
 */
class OrderBuilderTest {
    private val orderBuilder = OrderBuilder()

    @Test
    fun `test build market order for long signal`() {
        val signal = createTestSignal(direction = Direction.LONG)
        val config =
            ExecutionConfig(
                defaultOrderType = StrategyOrderType.MARKET,
            )
        val positionSize = 10.0
        val currentPrice = 100.0

        val result =
            orderBuilder.buildOrder(
                signal = signal,
                config = config,
                positionSize = positionSize,
                currentPrice = currentPrice,
            )

        assertTrue(result is OrderBuildResult.Success)
        val order = (result as OrderBuildResult.Success).order
        assertEquals(signal.symbol, order.symbol)
        assertEquals(OrderSide.BUY, order.side)
        assertEquals(xyz.fkstrading.shared.domain.models.OrderType.MARKET, order.orderType)
        assertEquals(positionSize, order.quantity)
        assertNull(order.price) // Market orders have no price
        assertEquals(OrderStatus.PENDING, order.status)
        assertEquals(signal.signalId, order.signalId)
    }

    @Test
    fun `test build market order for short signal`() {
        val signal = createTestSignal(direction = Direction.SHORT)
        val config =
            ExecutionConfig(
                defaultOrderType = StrategyOrderType.MARKET,
            )
        val positionSize = 5.0
        val currentPrice = 200.0

        val result =
            orderBuilder.buildOrder(
                signal = signal,
                config = config,
                positionSize = positionSize,
                currentPrice = currentPrice,
            )

        assertTrue(result is OrderBuildResult.Success)
        val order = (result as OrderBuildResult.Success).order
        assertEquals(OrderSide.SELL, order.side)
        assertEquals(xyz.fkstrading.shared.domain.models.OrderType.MARKET, order.orderType)
        assertEquals(positionSize, order.quantity)
    }

    @Test
    fun `test build limit order with price offset`() {
        val signal = createTestSignal(direction = Direction.LONG)
        val config =
            ExecutionConfig(
                defaultOrderType = StrategyOrderType.LIMIT,
                limitOrderOffset = 0.001, // 0.1% offset
            )
        val positionSize = 10.0
        val currentPrice = 100.0

        val result =
            orderBuilder.buildOrder(
                signal = signal,
                config = config,
                positionSize = positionSize,
                currentPrice = currentPrice,
            )

        assertTrue(result is OrderBuildResult.Success)
        val order = (result as OrderBuildResult.Success).order
        assertEquals(xyz.fkstrading.shared.domain.models.OrderType.LIMIT, order.orderType)
        assertNotNull(order.price)
        // For BUY limit, price should be slightly below current price
        assertTrue(order.price!! < currentPrice)
        assertEquals(99.9, order.price!!, 0.01) // 100 * (1 - 0.001) = 99.9
    }

    @Test
    fun `test build limit order for short with price offset`() {
        val signal = createTestSignal(direction = Direction.SHORT)
        val config =
            ExecutionConfig(
                defaultOrderType = StrategyOrderType.LIMIT,
                limitOrderOffset = 0.002, // 0.2% offset
            )
        val positionSize = 10.0
        val currentPrice = 100.0

        val result =
            orderBuilder.buildOrder(
                signal = signal,
                config = config,
                positionSize = positionSize,
                currentPrice = currentPrice,
            )

        assertTrue(result is OrderBuildResult.Success)
        val order = (result as OrderBuildResult.Success).order
        assertEquals(OrderSide.SELL, order.side)
        assertEquals(xyz.fkstrading.shared.domain.models.OrderType.LIMIT, order.orderType)
        assertNotNull(order.price)
        // For SELL limit, price should be slightly above current price
        assertTrue(order.price!! > currentPrice)
        assertEquals(100.2, order.price!!, 0.01) // 100 * (1 + 0.002) = 100.2
    }

    @Test
    fun `test build order with stop-loss`() {
        val signal = createTestSignal(direction = Direction.LONG)
        val config = ExecutionConfig(defaultOrderType = StrategyOrderType.MARKET)
        val positionSize = 10.0
        val currentPrice = 100.0
        val stopLossPrice = 95.0

        val result =
            orderBuilder.buildOrder(
                signal = signal,
                config = config,
                positionSize = positionSize,
                currentPrice = currentPrice,
                stopLossPrice = stopLossPrice,
            )

        assertTrue(result is OrderBuildResult.Success)
        val success = result as OrderBuildResult.Success

        // Check main order
        assertNotNull(success.order)

        // Check stop-loss order
        assertNotNull(success.stopLossOrder)
        val slOrder = success.stopLossOrder!!
        assertEquals(signal.symbol, slOrder.symbol)
        assertEquals(OrderSide.SELL, slOrder.side) // Opposite of LONG entry
        assertEquals(xyz.fkstrading.shared.domain.models.OrderType.STOP, slOrder.orderType)
        assertEquals(positionSize, slOrder.quantity)
        assertEquals(stopLossPrice, slOrder.stopPrice)
        assertEquals(TimeInForce.GTC, slOrder.timeInForce)
        assertTrue(slOrder.metadata.containsKey("parent_order_id"))
        assertEquals("stop_loss", slOrder.metadata["order_category"])
    }

    @Test
    fun `test build order with take-profit`() {
        val signal = createTestSignal(direction = Direction.LONG)
        val config = ExecutionConfig(defaultOrderType = StrategyOrderType.MARKET)
        val positionSize = 10.0
        val currentPrice = 100.0
        val takeProfitPrice = 110.0

        val result =
            orderBuilder.buildOrder(
                signal = signal,
                config = config,
                positionSize = positionSize,
                currentPrice = currentPrice,
                takeProfitPrice = takeProfitPrice,
            )

        assertTrue(result is OrderBuildResult.Success)
        val success = result as OrderBuildResult.Success

        // Check take-profit order
        assertNotNull(success.takeProfitOrder)
        val tpOrder = success.takeProfitOrder!!
        assertEquals(signal.symbol, tpOrder.symbol)
        assertEquals(OrderSide.SELL, tpOrder.side) // Opposite of LONG entry
        assertEquals(xyz.fkstrading.shared.domain.models.OrderType.LIMIT, tpOrder.orderType)
        assertEquals(positionSize, tpOrder.quantity)
        assertEquals(takeProfitPrice, tpOrder.price)
        assertEquals(TimeInForce.GTC, tpOrder.timeInForce)
        assertTrue(tpOrder.metadata.containsKey("parent_order_id"))
        assertEquals("take_profit", tpOrder.metadata["order_category"])
    }

    @Test
    fun `test build order triplet with SL and TP`() {
        val signal = createTestSignal(direction = Direction.SHORT)
        val config = ExecutionConfig(defaultOrderType = StrategyOrderType.MARKET)
        val positionSize = 20.0
        val currentPrice = 100.0
        val stopLossPrice = 105.0 // Above entry for SHORT
        val takeProfitPrice = 90.0 // Below entry for SHORT

        val result =
            orderBuilder.buildOrder(
                signal = signal,
                config = config,
                positionSize = positionSize,
                currentPrice = currentPrice,
                stopLossPrice = stopLossPrice,
                takeProfitPrice = takeProfitPrice,
                strategyId = "STRATEGY-001",
            )

        assertTrue(result is OrderBuildResult.Success)
        val success = result as OrderBuildResult.Success

        // Check all three orders are present
        assertNotNull(success.order)
        assertNotNull(success.stopLossOrder)
        assertNotNull(success.takeProfitOrder)

        // Verify allOrders helper
        assertEquals(3, success.allOrders.size)

        // Verify stop-loss is BUY (opposite of SHORT)
        assertEquals(OrderSide.BUY, success.stopLossOrder!!.side)
        assertEquals(stopLossPrice, success.stopLossOrder!!.stopPrice)

        // Verify take-profit is BUY (opposite of SHORT)
        assertEquals(OrderSide.BUY, success.takeProfitOrder!!.side)
        assertEquals(takeProfitPrice, success.takeProfitOrder!!.price)

        // Verify strategy ID is propagated
        assertEquals("STRATEGY-001", success.order.strategyId)
        assertEquals("STRATEGY-001", success.stopLossOrder!!.strategyId)
        assertEquals("STRATEGY-001", success.takeProfitOrder!!.strategyId)
    }

    @Test
    fun `test calculate stop-loss with percentage method`() {
        val signal = createTestSignal(direction = Direction.LONG)
        val config =
            ExecutionConfig(
                stopLossMethod = StopLossMethod.FIXED_PERCENTAGE,
                stopLossPercentage = 0.02, // 2%
            )
        val currentPrice = 100.0

        val stopLoss =
            orderBuilder.calculateStopLoss(
                signal = signal,
                config = config,
                currentPrice = currentPrice,
            )

        assertNotNull(stopLoss)
        // LONG: stop should be 2% below entry
        assertEquals(98.0, stopLoss!!, 0.01) // 100 * (1 - 0.02) = 98
    }

    @Test
    fun `test calculate stop-loss percentage for short`() {
        val signal = createTestSignal(direction = Direction.SHORT)
        val config =
            ExecutionConfig(
                stopLossMethod = StopLossMethod.FIXED_PERCENTAGE,
                stopLossPercentage = 0.03, // 3%
            )
        val currentPrice = 100.0

        val stopLoss =
            orderBuilder.calculateStopLoss(
                signal = signal,
                config = config,
                currentPrice = currentPrice,
            )

        assertNotNull(stopLoss)
        // SHORT: stop should be 3% above entry
        assertEquals(103.0, stopLoss!!, 0.01) // 100 * (1 + 0.03) = 103
    }

    @Test
    fun `test calculate stop-loss with ATR method`() {
        val signal = createTestSignal(direction = Direction.LONG)
        val config =
            ExecutionConfig(
                stopLossMethod = StopLossMethod.ATR_BASED,
                stopLossAtrMultiple = 2.0,
            )
        val currentPrice = 100.0
        val atr = 1.5

        val stopLoss =
            orderBuilder.calculateStopLoss(
                signal = signal,
                config = config,
                currentPrice = currentPrice,
                atr = atr,
            )

        assertNotNull(stopLoss)
        // LONG: stop = currentPrice - (ATR * multiplier)
        assertEquals(97.0, stopLoss!!, 0.01) // 100 - (1.5 * 2) = 97
    }

    @Test
    fun `test calculate stop-loss ATR fallback to percentage`() {
        val signal = createTestSignal(direction = Direction.LONG)
        val config =
            ExecutionConfig(
                stopLossMethod = StopLossMethod.ATR_BASED,
                stopLossAtrMultiple = 2.0,
                stopLossPercentage = 0.02, // Fallback
            )
        val currentPrice = 100.0
        val atr = null // ATR not available

        val stopLoss =
            orderBuilder.calculateStopLoss(
                signal = signal,
                config = config,
                currentPrice = currentPrice,
                atr = atr,
            )

        assertNotNull(stopLoss)
        // Should fall back to percentage method
        assertEquals(98.0, stopLoss!!, 0.01)
    }

    @Test
    fun `test calculate stop-loss from signal`() {
        val signal =
            createTestSignal(
                direction = Direction.LONG,
                stopLoss = 95.0,
            )
        val config =
            ExecutionConfig(
                stopLossMethod = StopLossMethod.NONE,
            )
        val currentPrice = 100.0

        val stopLoss =
            orderBuilder.calculateStopLoss(
                signal = signal,
                config = config,
                currentPrice = currentPrice,
            )

        assertNotNull(stopLoss)
        assertEquals(95.0, stopLoss!!, 0.01)
    }

    @Test
    fun `test calculate take-profit with percentage method`() {
        val signal = createTestSignal(direction = Direction.LONG)
        val config =
            ExecutionConfig(
                takeProfitMethod = TakeProfitMethod.FIXED_PERCENTAGE,
                takeProfitPercentage = 0.05, // 5%
            )
        val currentPrice = 100.0

        val takeProfit =
            orderBuilder.calculateTakeProfit(
                signal = signal,
                config = config,
                currentPrice = currentPrice,
            )

        assertNotNull(takeProfit)
        // LONG: TP should be 5% above entry
        assertEquals(105.0, takeProfit!!, 0.01)
    }

    @Test
    fun `test calculate take-profit with risk-reward ratio`() {
        val signal = createTestSignal(direction = Direction.LONG)
        val config =
            ExecutionConfig(
                takeProfitMethod = TakeProfitMethod.RISK_REWARD_RATIO,
                riskRewardRatio = 2.0, // 2:1 R/R
            )
        val currentPrice = 100.0
        val stopLossPrice = 98.0 // 2 points risk

        val takeProfit =
            orderBuilder.calculateTakeProfit(
                signal = signal,
                config = config,
                currentPrice = currentPrice,
                stopLossPrice = stopLossPrice,
            )

        assertNotNull(takeProfit)
        // Risk = 100 - 98 = 2
        // Reward = 2 * 2.0 = 4
        // TP = 100 + 4 = 104
        assertEquals(104.0, takeProfit!!, 0.01)
    }

    @Test
    fun `test calculate take-profit risk-reward for short`() {
        val signal = createTestSignal(direction = Direction.SHORT)
        val config =
            ExecutionConfig(
                takeProfitMethod = TakeProfitMethod.RISK_REWARD_RATIO,
                riskRewardRatio = 3.0, // 3:1 R/R
            )
        val currentPrice = 100.0
        val stopLossPrice = 105.0 // 5 points risk

        val takeProfit =
            orderBuilder.calculateTakeProfit(
                signal = signal,
                config = config,
                currentPrice = currentPrice,
                stopLossPrice = stopLossPrice,
            )

        assertNotNull(takeProfit)
        // Risk = 105 - 100 = 5
        // Reward = 5 * 3.0 = 15
        // TP = 100 - 15 = 85
        assertEquals(85.0, takeProfit!!, 0.01)
    }

    @Test
    fun `test order metadata contains execution details`() {
        val signal =
            createTestSignal(
                direction = Direction.LONG,
                strategyName = "MomentumStrategy",
            )
        val config =
            ExecutionConfig(
                mode = ExecutionMode.AUTO,
                defaultOrderType = StrategyOrderType.MARKET,
                positionSizingMethod = PositionSizingMethod.RISK_BASED,
                stopLossMethod = StopLossMethod.FIXED_PERCENTAGE,
                takeProfitMethod = TakeProfitMethod.RISK_REWARD_RATIO,
                dryRunMode = true,
            )
        val positionSize = 10.0
        val currentPrice = 100.0
        val stopLossPrice = 95.0
        val takeProfitPrice = 110.0

        val result =
            orderBuilder.buildOrder(
                signal = signal,
                config = config,
                positionSize = positionSize,
                currentPrice = currentPrice,
                stopLossPrice = stopLossPrice,
                takeProfitPrice = takeProfitPrice,
            )

        assertTrue(result is OrderBuildResult.Success)
        val metadata = (result as OrderBuildResult.Success).order.metadata

        assertEquals("AUTO", metadata["execution_mode"])
        assertEquals("RISK_BASED", metadata["position_sizing_method"])
        assertEquals(signal.confidence.toString(), metadata["signal_confidence"])
        assertEquals("true", metadata["dry_run"])
        assertEquals(stopLossPrice.toString(), metadata["stop_loss_price"])
        assertEquals("FIXED_PERCENTAGE", metadata["stop_loss_method"])
        assertEquals(takeProfitPrice.toString(), metadata["take_profit_price"])
        assertEquals("RISK_REWARD_RATIO", metadata["take_profit_method"])
        assertEquals("MomentumStrategy", metadata["strategy_name"])
    }

    @Test
    fun `test validation rejects invalid inputs`() {
        val signal = createTestSignal(symbol = "") // Blank symbol
        val config = ExecutionConfig()
        val positionSize = -10.0 // Negative size
        val currentPrice = 0.0 // Zero price

        val result =
            orderBuilder.buildOrder(
                signal = signal,
                config = config,
                positionSize = positionSize,
                currentPrice = currentPrice,
            )

        assertTrue(result is OrderBuildResult.Error)
        // Should catch at least one error
        assertNotNull((result as OrderBuildResult.Error).message)
    }

    // Helper function to create test signals
    private fun createTestSignal(
        symbol: String = "BTC/USD",
        direction: Direction = Direction.LONG,
        confidence: Double = 0.85,
        stopLoss: Double = if (direction == Direction.LONG) 95.0 else 105.0,
        takeProfit: Double = if (direction == Direction.LONG) 110.0 else 90.0,
        strategyName: String? = null,
    ): Signal {
        return Signal(
            signalId = "TEST-SIGNAL-${Clock.System.now().toEpochMilliseconds()}",
            signalType = SignalType.ENTRY,
            symbol = symbol,
            timeframe = Timeframe.H1,
            direction = direction,
            strength = confidence,
            price = 100.0,
            entryPrice = 100.0,
            stopLoss = stopLoss,
            takeProfit = takeProfit,
            confidence = confidence,
            timestamp = Clock.System.now(),
            strategyName = strategyName,
        )
    }
}
