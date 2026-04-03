package xyz.fkstrading.shared.domain.strategy

import kotlinx.datetime.Clock
import xyz.fkstrading.shared.domain.models.Direction
import xyz.fkstrading.shared.domain.models.Order
import xyz.fkstrading.shared.domain.models.OrderSide
import xyz.fkstrading.shared.domain.models.OrderStatus
import xyz.fkstrading.shared.domain.models.Signal
import xyz.fkstrading.shared.domain.models.TimeInForce
import xyz.fkstrading.shared.domain.strategy.models.*
import kotlin.math.abs
import kotlin.random.Random

/**
 * Converts Signal Direction to Order Side.
 */
private fun Direction.toOrderSide(): OrderSide =
    when (this) {
        Direction.LONG -> OrderSide.BUY
        Direction.SHORT -> OrderSide.SELL
    }

/**
 * Builds Order objects from trading signals and execution parameters.
 *
 * Handles conversion of signals to orders with:
 * - Position sizing
 * - Stop-loss and take-profit levels
 * - Order type selection
 * - Price calculation for limit orders
 */
class OrderBuilder {
    /**
     * Builds an order from a signal and execution parameters.
     *
     * @param signal Trading signal to execute
     * @param config Execution configuration
     * @param positionSize Calculated position size (quantity)
     * @param currentPrice Current market price
     * @param stopLossPrice Calculated stop-loss price (optional)
     * @param takeProfitPrice Calculated take-profit price (optional)
     * @param strategyId Strategy identifier (optional)
     * @return OrderBuildResult containing the built order or error
     */
    fun buildOrder(
        signal: Signal,
        config: ExecutionConfig,
        positionSize: Double,
        currentPrice: Double,
        stopLossPrice: Double? = null,
        takeProfitPrice: Double? = null,
        strategyId: String? = null,
    ): OrderBuildResult {
        // Validate inputs
        val validationErrors = validateInputs(signal, positionSize, currentPrice)
        if (validationErrors.isNotEmpty()) {
            return OrderBuildResult.Error(validationErrors.first())
        }

        // Generate order ID
        val orderId = generateOrderId()

        // Convert signal direction to order side
        val orderSide = signal.direction.toOrderSide()

        // Determine order type and price
        val (orderType, orderPrice) =
            determineOrderTypeAndPrice(
                config = config,
                orderSide = orderSide,
                currentPrice = currentPrice,
            )

        // Build the main order
        val order =
            Order(
                orderId = orderId,
                symbol = signal.symbol,
                side = orderSide,
                orderType = orderType,
                quantity = positionSize,
                price = orderPrice,
                status = OrderStatus.PENDING,
                timeInForce = config.timeInForce,
                timestamp = Clock.System.now(),
                signalId = signal.signalId,
                strategyId = strategyId,
                metadata = buildMetadata(signal, config, stopLossPrice, takeProfitPrice),
            )

        // Build stop-loss and take-profit orders if applicable
        val stopLossOrder =
            if (stopLossPrice != null) {
                buildStopLossOrder(
                    symbol = signal.symbol,
                    side = orderSide,
                    quantity = positionSize,
                    stopPrice = stopLossPrice,
                    parentOrderId = orderId,
                    signalId = signal.signalId,
                    strategyId = strategyId,
                )
            } else {
                null
            }

        val takeProfitOrder =
            if (takeProfitPrice != null) {
                buildTakeProfitOrder(
                    symbol = signal.symbol,
                    side = orderSide,
                    quantity = positionSize,
                    limitPrice = takeProfitPrice,
                    parentOrderId = orderId,
                    signalId = signal.signalId,
                    strategyId = strategyId,
                )
            } else {
                null
            }

        return OrderBuildResult.Success(
            order = order,
            stopLossOrder = stopLossOrder,
            takeProfitOrder = takeProfitOrder,
        )
    }

    /**
     * Calculates stop-loss price based on configuration.
     */
    fun calculateStopLoss(
        signal: Signal,
        config: ExecutionConfig,
        currentPrice: Double,
        atr: Double? = null,
    ): Double? {
        return when (config.stopLossMethod) {
            StopLossMethod.FIXED_PERCENTAGE -> {
                val percentage = config.stopLossPercentage ?: 0.02 // Default 2%
                val stopDistance = currentPrice * percentage
                when (signal.direction) {
                    Direction.LONG -> currentPrice - stopDistance
                    Direction.SHORT -> currentPrice + stopDistance
                }
            }

            StopLossMethod.ATR_BASED -> {
                if (atr == null) {
                    // Fallback to percentage if ATR not available
                    return calculateStopLoss(
                        signal = signal,
                        config = config.copy(stopLossMethod = StopLossMethod.FIXED_PERCENTAGE),
                        currentPrice = currentPrice,
                    )
                }
                val stopDistance = atr * config.stopLossAtrMultiple
                when (signal.direction) {
                    Direction.LONG -> currentPrice - stopDistance
                    Direction.SHORT -> currentPrice + stopDistance
                }
            }

            StopLossMethod.NONE -> signal.stopLoss
            else -> {
                // Fallback for any other methods (FIXED_AMOUNT, SUPPORT_RESISTANCE, TRAILING, VOLATILITY_ADJUSTED)
                signal.stopLoss
            }
        }
    }

    /**
     * Calculates take-profit price based on configuration.
     */
    fun calculateTakeProfit(
        signal: Signal,
        config: ExecutionConfig,
        currentPrice: Double,
        stopLossPrice: Double? = null,
        atr: Double? = null,
    ): Double? {
        return when (config.takeProfitMethod) {
            TakeProfitMethod.FIXED_PERCENTAGE -> {
                val percentage = config.takeProfitPercentage ?: 0.02 // Default 2%
                val profitDistance = currentPrice * percentage
                when (signal.direction) {
                    Direction.LONG -> currentPrice + profitDistance
                    Direction.SHORT -> currentPrice - profitDistance
                }
            }

            TakeProfitMethod.ATR_BASED -> {
                if (atr == null) {
                    // Fallback to percentage if ATR not available
                    return calculateTakeProfit(
                        signal = signal,
                        config = config.copy(takeProfitMethod = TakeProfitMethod.FIXED_PERCENTAGE),
                        currentPrice = currentPrice,
                        stopLossPrice = stopLossPrice,
                    )
                }
                val atrMultiple = config.takeProfitAtrMultiple ?: 3.0 // Default 3x ATR
                val profitDistance = atr * atrMultiple
                when (signal.direction) {
                    Direction.LONG -> currentPrice + profitDistance
                    Direction.SHORT -> currentPrice - profitDistance
                }
            }

            TakeProfitMethod.RISK_REWARD_RATIO -> {
                if (stopLossPrice == null) {
                    // Fallback to percentage if no stop-loss
                    return calculateTakeProfit(
                        signal = signal,
                        config = config.copy(takeProfitMethod = TakeProfitMethod.FIXED_PERCENTAGE),
                        currentPrice = currentPrice,
                    )
                }
                val stopDistance = abs(currentPrice - stopLossPrice)
                val profitDistance = stopDistance * config.riskRewardRatio
                when (signal.direction) {
                    Direction.LONG -> currentPrice + profitDistance
                    Direction.SHORT -> currentPrice - profitDistance
                }
            }

            TakeProfitMethod.NONE -> null
            else -> {
                // Fallback for any other methods (FIXED_AMOUNT, SUPPORT_RESISTANCE, TRAILING, MULTIPLE_TARGETS)
                signal.takeProfit
            }
        }
    }

    /**
     * Validates builder inputs.
     */
    private fun validateInputs(
        signal: Signal,
        positionSize: Double,
        currentPrice: Double,
    ): List<String> {
        val errors = mutableListOf<String>()

        if (signal.symbol.isBlank()) {
            errors.add("Signal symbol cannot be blank")
        }

        if (positionSize <= 0) {
            errors.add("Position size must be positive")
        }

        if (currentPrice <= 0) {
            errors.add("Current price must be positive")
        }

        return errors
    }

    /**
     * Determines order type and price based on configuration.
     */
    private fun determineOrderTypeAndPrice(
        config: ExecutionConfig,
        orderSide: OrderSide,
        currentPrice: Double,
    ): Pair<xyz.fkstrading.shared.domain.models.OrderType, Double?> {
        return when (config.defaultOrderType) {
            OrderType.MARKET -> {
                Pair(xyz.fkstrading.shared.domain.models.OrderType.MARKET, null)
            }

            OrderType.LIMIT -> {
                // For limit orders, adjust price slightly to improve fill probability
                val limitPrice =
                    when (orderSide) {
                        OrderSide.BUY -> currentPrice * (1.0 - config.limitOrderOffset)
                        OrderSide.SELL -> currentPrice * (1.0 + config.limitOrderOffset)
                    }
                Pair(xyz.fkstrading.shared.domain.models.OrderType.LIMIT, limitPrice)
            }

            OrderType.STOP -> {
                Pair(xyz.fkstrading.shared.domain.models.OrderType.STOP, null)
            }

            OrderType.STOP_LIMIT -> {
                val limitPrice =
                    when (orderSide) {
                        OrderSide.BUY -> currentPrice * (1.0 + config.limitOrderOffset)
                        OrderSide.SELL -> currentPrice * (1.0 - config.limitOrderOffset)
                    }
                Pair(xyz.fkstrading.shared.domain.models.OrderType.STOP_LIMIT, limitPrice)
            }

            OrderType.TRAILING_STOP -> {
                Pair(xyz.fkstrading.shared.domain.models.OrderType.TRAILING_STOP, null)
            }
        }
    }

    /**
     * Builds stop-loss order.
     */
    private fun buildStopLossOrder(
        symbol: String,
        side: OrderSide,
        quantity: Double,
        stopPrice: Double,
        parentOrderId: String,
        signalId: String?,
        strategyId: String?,
    ): Order {
        // Stop-loss is opposite side of entry order
        val stopSide =
            when (side) {
                OrderSide.BUY -> OrderSide.SELL
                OrderSide.SELL -> OrderSide.BUY
            }

        return Order(
            orderId = generateOrderId(suffix = "SL"),
            symbol = symbol,
            side = stopSide,
            orderType = xyz.fkstrading.shared.domain.models.OrderType.STOP,
            quantity = quantity,
            stopPrice = stopPrice,
            status = OrderStatus.PENDING,
            timeInForce = TimeInForce.GTC,
            timestamp = Clock.System.now(),
            signalId = signalId,
            strategyId = strategyId,
            metadata =
                mapOf(
                    "parent_order_id" to parentOrderId,
                    "order_category" to "stop_loss",
                ),
        )
    }

    /**
     * Builds take-profit order.
     */
    private fun buildTakeProfitOrder(
        symbol: String,
        side: OrderSide,
        quantity: Double,
        limitPrice: Double,
        parentOrderId: String,
        signalId: String?,
        strategyId: String?,
    ): Order {
        // Take-profit is opposite side of entry order
        val tpSide =
            when (side) {
                OrderSide.BUY -> OrderSide.SELL
                OrderSide.SELL -> OrderSide.BUY
            }

        return Order(
            orderId = generateOrderId(suffix = "TP"),
            symbol = symbol,
            side = tpSide,
            orderType = xyz.fkstrading.shared.domain.models.OrderType.LIMIT,
            quantity = quantity,
            price = limitPrice,
            status = OrderStatus.PENDING,
            timeInForce = TimeInForce.GTC,
            timestamp = Clock.System.now(),
            signalId = signalId,
            strategyId = strategyId,
            metadata =
                mapOf(
                    "parent_order_id" to parentOrderId,
                    "order_category" to "take_profit",
                ),
        )
    }

    /**
     * Builds metadata map for the order.
     */
    private fun buildMetadata(
        signal: Signal,
        config: ExecutionConfig,
        stopLossPrice: Double?,
        takeProfitPrice: Double?,
    ): Map<String, String> {
        val metadata = mutableMapOf<String, String>()

        metadata["execution_mode"] = config.mode.name
        metadata["position_sizing_method"] = config.positionSizingMethod.name
        metadata["signal_confidence"] = signal.confidence.toString()

        if (config.dryRunMode) {
            metadata["dry_run"] = "true"
        }

        if (stopLossPrice != null) {
            metadata["stop_loss_price"] = stopLossPrice.toString()
            metadata["stop_loss_method"] = config.stopLossMethod.name
        }

        if (takeProfitPrice != null) {
            metadata["take_profit_price"] = takeProfitPrice.toString()
            metadata["take_profit_method"] = config.takeProfitMethod.name
        }

        signal.strategyName?.let { metadata["strategy_name"] = it }

        return metadata
    }

    /**
     * Generates a unique order ID.
     */
    private fun generateOrderId(suffix: String = ""): String {
        val timestamp = Clock.System.now().toEpochMilliseconds()
        val random = Random.nextInt(1000, 9999)
        return if (suffix.isNotEmpty()) {
            "ORD-$timestamp-$random-$suffix"
        } else {
            "ORD-$timestamp-$random"
        }
    }
}

/**
 * Result of order building operation.
 */
sealed class OrderBuildResult {
    /**
     * Successfully built order(s).
     */
    data class Success(
        val order: Order,
        val stopLossOrder: Order? = null,
        val takeProfitOrder: Order? = null,
    ) : OrderBuildResult() {
        /**
         * All orders (main + SL + TP).
         */
        val allOrders: List<Order>
            get() = listOfNotNull(order, stopLossOrder, takeProfitOrder)
    }

    /**
     * Failed to build order.
     */
    data class Error(
        val message: String,
    ) : OrderBuildResult()
}
