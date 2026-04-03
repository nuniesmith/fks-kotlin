package xyz.fkstrading.shared.data.mock

import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
import xyz.fkstrading.shared.data.repository.OrderRemoteDataSource
import xyz.fkstrading.shared.domain.models.*
import kotlin.random.Random
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Mock implementation of OrderRemoteDataSource
 *
 * Provides realistic sample data for testing the UI without a backend.
 * Simulates network delays and order lifecycle.
 */
class MockOrderDataSource : OrderRemoteDataSource {
    private val orders = mutableListOf<Order>()
    private var nextId = 1

    init {
        // Seed with sample orders
        orders.addAll(generateSampleOrders())
    }

    override suspend fun getOrderById(orderId: String): Order? {
        delay(100) // Simulate network delay
        return orders.find { it.orderId == orderId }
    }

    override suspend fun getRecentOrders(limit: Int): List<Order> {
        delay(150)
        return orders.sortedByDescending { it.timestamp }.take(limit)
    }

    override suspend fun getOrdersBySymbol(symbol: String): List<Order> {
        delay(120)
        return orders.filter { it.symbol == symbol }
    }

    override suspend fun getActiveOrders(): List<Order> {
        delay(100)
        return orders.filter { it.isActive() }
    }

    override suspend fun saveOrder(order: Order) {
        delay(200)
        val index = orders.indexOfFirst { it.orderId == order.orderId }
        if (index >= 0) {
            orders[index] = order
        } else {
            orders.add(order)
        }
    }

    override suspend fun updateOrderStatus(
        orderId: String,
        status: OrderStatus,
    ) {
        delay(150)
        val index = orders.indexOfFirst { it.orderId == orderId }
        if (index >= 0) {
            orders[index] =
                orders[index].copy(
                    status = status,
                    updatedAt = Clock.System.now(),
                    completedAt = if (status.isComplete()) Clock.System.now() else null,
                )
        }
    }

    override suspend fun deleteOrder(orderId: String) {
        delay(100)
        orders.removeAll { it.orderId == orderId }
    }

    /**
     * Generates sample orders for testing
     */
    private fun generateSampleOrders(): List<Order> {
        val now = Clock.System.now()

        return listOf(
            // Active limit buy order
            Order(
                orderId = "ORD-${nextId++}",
                symbol = "BTC/USD",
                side = OrderSide.BUY,
                orderType = OrderType.LIMIT,
                quantity = 0.5,
                price = 46000.0,
                status = OrderStatus.ACCEPTED,
                timeInForce = TimeInForce.GTC,
                timestamp = now.minus(2.hours),
                submittedAt = now.minus(2.hours),
                updatedAt = now.minus(2.hours),
            ),
            // Partially filled order
            Order(
                orderId = "ORD-${nextId++}",
                symbol = "ETH/USD",
                side = OrderSide.SELL,
                orderType = OrderType.LIMIT,
                quantity = 10.0,
                price = 3100.0,
                status = OrderStatus.PARTIALLY_FILLED,
                timeInForce = TimeInForce.GTC,
                filledQuantity = 6.5,
                averageFillPrice = 3105.0,
                timestamp = now.minus(4.hours),
                submittedAt = now.minus(4.hours),
                updatedAt = now.minus(30.minutes),
                fees = 19.5,
                commission = 10.0,
            ),
            // Active stop order
            Order(
                orderId = "ORD-${nextId++}",
                symbol = "SOL/USD",
                side = OrderSide.BUY,
                orderType = OrderType.STOP,
                quantity = 50.0,
                stopPrice = 105.0,
                status = OrderStatus.ACCEPTED,
                timeInForce = TimeInForce.GTC,
                timestamp = now.minus(1.hours),
                submittedAt = now.minus(1.hours),
                updatedAt = now.minus(1.hours),
            ),
            // Filled market order
            Order(
                orderId = "ORD-${nextId++}",
                symbol = "AAPL",
                side = OrderSide.BUY,
                orderType = OrderType.MARKET,
                quantity = 100.0,
                status = OrderStatus.FILLED,
                timeInForce = TimeInForce.IOC,
                filledQuantity = 100.0,
                averageFillPrice = 150.5,
                timestamp = now.minus(1.days),
                submittedAt = now.minus(1.days),
                updatedAt = now.minus(1.days),
                completedAt = now.minus(1.days),
                fees = 15.0,
                commission = 7.5,
            ),
            // Cancelled order
            Order(
                orderId = "ORD-${nextId++}",
                symbol = "TSLA",
                side = OrderSide.SELL,
                orderType = OrderType.LIMIT,
                quantity = 50.0,
                price = 260.0,
                status = OrderStatus.CANCELLED,
                timeInForce = TimeInForce.GTC,
                timestamp = now.minus(2.days),
                submittedAt = now.minus(2.days),
                updatedAt = now.minus(1.days),
                completedAt = now.minus(1.days),
            ),
            // Pending order
            Order(
                orderId = "ORD-${nextId++}",
                symbol = "ADA/USD",
                side = OrderSide.BUY,
                orderType = OrderType.LIMIT,
                quantity = 1000.0,
                price = 0.51,
                status = OrderStatus.PENDING,
                timeInForce = TimeInForce.GTC,
                timestamp = now.minus(5.minutes),
                submittedAt = now.minus(5.minutes),
            ),
            // Filled sell order
            Order(
                orderId = "ORD-${nextId++}",
                symbol = "DOGE/USD",
                side = OrderSide.SELL,
                orderType = OrderType.MARKET,
                quantity = 5000.0,
                status = OrderStatus.FILLED,
                timeInForce = TimeInForce.IOC,
                filledQuantity = 5000.0,
                averageFillPrice = 0.115,
                timestamp = now.minus(3.days),
                submittedAt = now.minus(3.days),
                updatedAt = now.minus(3.days),
                completedAt = now.minus(3.days),
                fees = 0.58,
                commission = 0.29,
            ),
            // Active stop-limit order
            Order(
                orderId = "ORD-${nextId++}",
                symbol = "XRP/USD",
                side = OrderSide.SELL,
                orderType = OrderType.STOP_LIMIT,
                quantity = 2000.0,
                price = 0.58,
                stopPrice = 0.59,
                status = OrderStatus.ACCEPTED,
                timeInForce = TimeInForce.GTC,
                timestamp = now.minus(3.hours),
                submittedAt = now.minus(3.hours),
                updatedAt = now.minus(3.hours),
            ),
            // Rejected order
            Order(
                orderId = "ORD-${nextId++}",
                symbol = "GOOGL",
                side = OrderSide.BUY,
                orderType = OrderType.LIMIT,
                quantity = 100.0,
                price = 135.0,
                status = OrderStatus.REJECTED,
                timeInForce = TimeInForce.GTC,
                timestamp = now.minus(6.hours),
                submittedAt = now.minus(6.hours),
                updatedAt = now.minus(6.hours),
                completedAt = now.minus(6.hours),
                errorMessage = "Insufficient funds",
            ),
            // Active limit sell order
            Order(
                orderId = "ORD-${nextId++}",
                symbol = "MSFT",
                side = OrderSide.SELL,
                orderType = OrderType.LIMIT,
                quantity = 30.0,
                price = 385.0,
                status = OrderStatus.ACCEPTED,
                timeInForce = TimeInForce.GTC,
                timestamp = now.minus(45.minutes),
                submittedAt = now.minus(45.minutes),
                updatedAt = now.minus(45.minutes),
            ),
            // Filled limit order with good price
            Order(
                orderId = "ORD-${nextId++}",
                symbol = "BTC/USD",
                side = OrderSide.BUY,
                orderType = OrderType.LIMIT,
                quantity = 0.25,
                price = 44500.0,
                status = OrderStatus.FILLED,
                timeInForce = TimeInForce.GTC,
                filledQuantity = 0.25,
                averageFillPrice = 44450.0,
                timestamp = now.minus(5.days),
                submittedAt = now.minus(5.days),
                updatedAt = now.minus(4.days),
                completedAt = now.minus(4.days),
                fees = 11.1,
                commission = 5.5,
            ),
            // Active market buy
            Order(
                orderId = "ORD-${nextId++}",
                symbol = "ETH/USD",
                side = OrderSide.BUY,
                orderType = OrderType.MARKET,
                quantity = 5.0,
                status = OrderStatus.PENDING,
                timeInForce = TimeInForce.IOC,
                timestamp = now.minus(30.seconds),
                submittedAt = now.minus(30.seconds),
            ),
        )
    }

    /**
     * Simulates order updates (for testing)
     */
    fun simulateOrderUpdate() {
        orders.filter { it.isActive() }.forEach { order ->
            // Randomly update order status
            val random = Random.nextDouble()

            when {
                random < 0.05 && order.status == OrderStatus.PENDING -> {
                    // 5% chance to move pending to open
                    val index = orders.indexOf(order)
                    if (index >= 0) {
                        orders[index] =
                            order.copy(
                                status = OrderStatus.ACCEPTED,
                                updatedAt = Clock.System.now(),
                            )
                    }
                }

                random < 0.02 && order.status == OrderStatus.ACCEPTED -> {
                    // 2% chance to partially fill
                    val index = orders.indexOf(order)
                    if (index >= 0) {
                        val fillAmount = order.quantity * Random.nextDouble(0.3, 0.7)
                        orders[index] =
                            order.copy(
                                status = OrderStatus.PARTIALLY_FILLED,
                                filledQuantity = fillAmount,
                                averageFillPrice = order.price?.let { it * (1 + Random.nextDouble(-0.001, 0.001)) },
                                updatedAt = Clock.System.now(),
                            )
                    }
                }

                random < 0.01 && order.status == OrderStatus.PARTIALLY_FILLED -> {
                    // 1% chance to complete fill
                    val index = orders.indexOf(order)
                    if (index >= 0) {
                        orders[index] =
                            order.copy(
                                status = OrderStatus.FILLED,
                                filledQuantity = order.quantity,
                                averageFillPrice = order.price?.let { it * (1 + Random.nextDouble(-0.001, 0.001)) },
                                updatedAt = Clock.System.now(),
                                completedAt = Clock.System.now(),
                                fees = order.quantity * (order.price ?: 0.0) * 0.001,
                                commission = order.quantity * (order.price ?: 0.0) * 0.0005,
                            )
                    }
                }
            }
        }
    }
}

// Extension function to check if order status is complete
private fun OrderStatus.isComplete(): Boolean {
    return this in listOf(OrderStatus.FILLED, OrderStatus.CANCELLED, OrderStatus.REJECTED, OrderStatus.EXPIRED)
}
