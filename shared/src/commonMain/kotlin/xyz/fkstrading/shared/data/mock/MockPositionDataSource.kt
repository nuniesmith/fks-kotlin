package xyz.fkstrading.shared.data.mock

import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
import xyz.fkstrading.shared.data.repository.PositionRemoteDataSource
import xyz.fkstrading.shared.domain.models.*
import kotlin.random.Random
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

/**
 * Mock implementation of PositionRemoteDataSource
 *
 * Provides realistic sample data for testing the UI without a backend.
 * Simulates network delays and data variations.
 */
class MockPositionDataSource : PositionRemoteDataSource {
    private val positions = mutableListOf<Position>()
    private var nextId = 1

    init {
        // Seed with sample positions
        positions.addAll(generateSamplePositions())
    }

    override suspend fun getPositionById(positionId: String): Position? {
        delay(100) // Simulate network delay
        return positions.find { it.positionId == positionId }
    }

    override suspend fun getRecentPositions(limit: Int): List<Position> {
        delay(150)
        return positions.take(limit)
    }

    override suspend fun getPositionsBySymbol(symbol: String): List<Position> {
        delay(120)
        return positions.filter { it.symbol == symbol }
    }

    override suspend fun getOpenPositions(): List<Position> {
        delay(100)
        return positions.filter { it.status == PositionStatus.OPEN }
    }

    override suspend fun getClosedPositions(): List<Position> {
        delay(100)
        return positions.filter { it.status == PositionStatus.CLOSED }
    }

    override suspend fun savePosition(position: Position) {
        delay(200)
        val index = positions.indexOfFirst { it.positionId == position.positionId }
        if (index >= 0) {
            positions[index] = position
        } else {
            positions.add(position)
        }
    }

    override suspend fun updatePositionPrice(
        positionId: String,
        currentPrice: Double,
        unrealizedPnL: Double,
    ) {
        delay(50)
        val index = positions.indexOfFirst { it.positionId == positionId }
        if (index >= 0) {
            positions[index] =
                positions[index].copy(
                    currentPrice = currentPrice,
                    unrealizedPnL = unrealizedPnL,
                    updatedAt = Clock.System.now(),
                )
        }
    }

    override suspend fun closePosition(
        positionId: String,
        realizedPnL: Double,
    ) {
        delay(150)
        val index = positions.indexOfFirst { it.positionId == positionId }
        if (index >= 0) {
            positions[index] =
                positions[index].copy(
                    status = PositionStatus.CLOSED,
                    closedAt = Clock.System.now(),
                    realizedPnL = realizedPnL,
                    updatedAt = Clock.System.now(),
                )
        }
    }

    override suspend fun deletePosition(positionId: String) {
        delay(100)
        positions.removeAll { it.positionId == positionId }
    }

    /**
     * Generates sample positions for testing
     */
    private fun generateSamplePositions(): List<Position> {
        val now = Clock.System.now()

        return listOf(
            // Open long position - Profitable
            Position(
                positionId = "POS-${nextId++}",
                symbol = "BTC/USD",
                side = OrderSide.BUY,
                quantity = 0.5,
                entryPrice = 45000.0,
                currentPrice = 47500.0,
                stopLoss = 44000.0,
                takeProfit = 50000.0,
                status = PositionStatus.OPEN,
                openedAt = now.minus(2.days),
                unrealizedPnL = 1250.0,
                fees = 22.5,
                commission = 11.25,
                orderId = "ORD-001",
            ),
            // Open short position - Losing
            Position(
                positionId = "POS-${nextId++}",
                symbol = "ETH/USD",
                side = OrderSide.SELL,
                quantity = 10.0,
                entryPrice = 3000.0,
                currentPrice = 3150.0,
                stopLoss = 3200.0,
                takeProfit = 2800.0,
                status = PositionStatus.OPEN,
                openedAt = now.minus(6.hours),
                unrealizedPnL = -1500.0,
                fees = 15.0,
                commission = 7.5,
                orderId = "ORD-002",
            ),
            // Open long position - Small profit
            Position(
                positionId = "POS-${nextId++}",
                symbol = "SOL/USD",
                side = OrderSide.BUY,
                quantity = 50.0,
                entryPrice = 100.0,
                currentPrice = 102.5,
                stopLoss = 98.0,
                takeProfit = 110.0,
                status = PositionStatus.OPEN,
                openedAt = now.minus(3.hours),
                unrealizedPnL = 125.0,
                fees = 5.0,
                commission = 2.5,
                orderId = "ORD-003",
            ),
            // Closed position - Big win
            Position(
                positionId = "POS-${nextId++}",
                symbol = "AAPL",
                side = OrderSide.BUY,
                quantity = 100.0,
                entryPrice = 150.0,
                currentPrice = 165.0,
                stopLoss = 145.0,
                takeProfit = 165.0,
                status = PositionStatus.CLOSED,
                openedAt = now.minus(5.days),
                closedAt = now.minus(1.days),
                realizedPnL = 1500.0,
                fees = 15.0,
                commission = 7.5,
                orderId = "ORD-004",
            ),
            // Closed position - Small loss
            Position(
                positionId = "POS-${nextId++}",
                symbol = "TSLA",
                side = OrderSide.SELL,
                quantity = 50.0,
                entryPrice = 250.0,
                currentPrice = 255.0,
                stopLoss = 255.0,
                takeProfit = 240.0,
                status = PositionStatus.CLOSED,
                openedAt = now.minus(3.days),
                closedAt = now.minus(12.hours),
                realizedPnL = -250.0,
                fees = 12.5,
                commission = 6.25,
                orderId = "ORD-005",
            ),
            // Open long - ADA
            Position(
                positionId = "POS-${nextId++}",
                symbol = "ADA/USD",
                side = OrderSide.BUY,
                quantity = 1000.0,
                entryPrice = 0.50,
                currentPrice = 0.52,
                stopLoss = 0.48,
                takeProfit = 0.60,
                status = PositionStatus.OPEN,
                openedAt = now.minus(8.hours),
                unrealizedPnL = 20.0,
                fees = 0.50,
                commission = 0.25,
                orderId = "ORD-006",
            ),
            // Closed - DOGE win
            Position(
                positionId = "POS-${nextId++}",
                symbol = "DOGE/USD",
                side = OrderSide.BUY,
                quantity = 5000.0,
                entryPrice = 0.10,
                currentPrice = 0.12,
                stopLoss = 0.09,
                takeProfit = 0.12,
                status = PositionStatus.CLOSED,
                openedAt = now.minus(7.days),
                closedAt = now.minus(2.days),
                realizedPnL = 100.0,
                fees = 0.50,
                commission = 0.25,
                orderId = "ORD-007",
            ),
            // Open short - GOOGL
            Position(
                positionId = "POS-${nextId++}",
                symbol = "GOOGL",
                side = OrderSide.SELL,
                quantity = 20.0,
                entryPrice = 140.0,
                currentPrice = 138.0,
                stopLoss = 145.0,
                takeProfit = 130.0,
                status = PositionStatus.OPEN,
                openedAt = now.minus(4.hours),
                unrealizedPnL = 40.0,
                fees = 2.8,
                commission = 1.4,
                orderId = "ORD-008",
            ),
            // Closed - MSFT loss
            Position(
                positionId = "POS-${nextId++}",
                symbol = "MSFT",
                side = OrderSide.BUY,
                quantity = 30.0,
                entryPrice = 380.0,
                currentPrice = 375.0,
                stopLoss = 375.0,
                takeProfit = 400.0,
                status = PositionStatus.CLOSED,
                openedAt = now.minus(4.days),
                closedAt = now.minus(1.days),
                realizedPnL = -150.0,
                fees = 11.4,
                commission = 5.7,
                orderId = "ORD-009",
            ),
            // Open - XRP
            Position(
                positionId = "POS-${nextId++}",
                symbol = "XRP/USD",
                side = OrderSide.BUY,
                quantity = 2000.0,
                entryPrice = 0.60,
                currentPrice = 0.62,
                stopLoss = 0.58,
                takeProfit = 0.70,
                status = PositionStatus.OPEN,
                openedAt = now.minus(2.hours),
                unrealizedPnL = 40.0,
                fees = 1.2,
                commission = 0.6,
                orderId = "ORD-010",
            ),
        )
    }

    /**
     * Simulates real-time price updates (for testing)
     */
    fun simulatePriceUpdate() {
        positions.filter { it.status == PositionStatus.OPEN }.forEach { position ->
            val priceChange = Random.nextDouble(-0.02, 0.02) // +/- 2% change
            val newPrice = position.currentPrice * (1 + priceChange)
            val newPnL = position.calculateUnrealizedPnL(newPrice)

            val index = positions.indexOf(position)
            if (index >= 0) {
                positions[index] =
                    position.copy(
                        currentPrice = newPrice,
                        unrealizedPnL = newPnL,
                        updatedAt = Clock.System.now(),
                    )
            }
        }
    }
}
