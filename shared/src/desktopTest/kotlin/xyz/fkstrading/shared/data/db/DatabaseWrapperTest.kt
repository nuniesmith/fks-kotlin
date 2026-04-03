package xyz.fkstrading.shared.data.db

import app.cash.sqldelight.db.SqlDriver
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import xyz.fkstrading.shared.*
import xyz.fkstrading.shared.domain.models.*
import kotlin.test.*

/**
 * Unit tests for DatabaseWrapper
 * Tests CRUD operations for Signals, Orders, and Positions
 */
class DatabaseWrapperTest {
    private lateinit var driver: SqlDriver
    private lateinit var database: DatabaseWrapper

    @BeforeTest
    fun setup() {
        // Create in-memory database for testing
        driver = DesktopDatabaseDriverFactory.createInMemoryDriver()
        database =
            DatabaseWrapper(
                object : DatabaseDriverFactory {
                    override fun createDriver(): SqlDriver = driver
                },
            )
    }

    @AfterTest
    fun teardown() {
        driver.close()
    }

    // ========================================
    // SIGNAL TESTS
    // ========================================

    @Test
    fun testInsertAndGetSignal() =
        runTest {
            // Given
            val signal =
                Signal.sample(
                    signalId = "SIG-001",
                    symbol = "BTC/USD",
                    direction = Direction.LONG,
                )

            // When
            database.insertOrReplaceSignal(signal, isSynced = false)
            val retrieved = database.getSignalById("SIG-001").first()

            // Then
            assertNotNull(retrieved)
            assertEquals(signal.signalId, retrieved.signalId)
            assertEquals(signal.symbol, retrieved.symbol)
            assertEquals(signal.direction, retrieved.direction)
            assertEquals(signal.entryPrice, retrieved.entryPrice)
        }

    @Test
    fun testGetAllSignals() =
        runTest {
            // Given
            val signals =
                listOf(
                    Signal.sample(signalId = "SIG-001", symbol = "BTC/USD"),
                    Signal.sample(signalId = "SIG-002", symbol = "ETH/USD"),
                    Signal.sample(signalId = "SIG-003", symbol = "AAPL"),
                )

            // When
            signals.forEach { database.insertOrReplaceSignal(it, isSynced = false) }
            val retrieved = database.getAllSignals().first()

            // Then
            assertEquals(3, retrieved.size)
            assertTrue(retrieved.any { it.signalId == "SIG-001" })
            assertTrue(retrieved.any { it.signalId == "SIG-002" })
            assertTrue(retrieved.any { it.signalId == "SIG-003" })
        }

    @Test
    fun testGetSignalsBySymbol() =
        runTest {
            // Given
            val signals =
                listOf(
                    Signal.sample(signalId = "SIG-001", symbol = "BTC/USD"),
                    Signal.sample(signalId = "SIG-002", symbol = "BTC/USD"),
                    Signal.sample(signalId = "SIG-003", symbol = "ETH/USD"),
                )

            // When
            signals.forEach { database.insertOrReplaceSignal(it, isSynced = false) }
            val btcSignals = database.getSignalsBySymbol("BTC/USD").first()

            // Then
            assertEquals(2, btcSignals.size)
            assertTrue(btcSignals.all { it.symbol == "BTC/USD" })
        }

    @Test
    fun testGetRecentSignals() =
        runTest {
            // Given
            repeat(10) { index ->
                val signal = Signal.sample(signalId = "SIG-$index")
                database.insertOrReplaceSignal(signal, isSynced = false)
            }

            // When
            val recent = database.getRecentSignals(5).first()

            // Then
            assertEquals(5, recent.size)
        }

    @Test
    fun testGetUnsyncedSignals() =
        runTest {
            // Given
            val syncedSignal = Signal.sample(signalId = "SIG-001")
            val unsyncedSignal = Signal.sample(signalId = "SIG-002")

            // When
            database.insertOrReplaceSignal(syncedSignal, isSynced = true)
            database.insertOrReplaceSignal(unsyncedSignal, isSynced = false)
            val unsynced = database.getUnsyncedSignals().first()

            // Then
            assertEquals(1, unsynced.size)
            assertEquals("SIG-002", unsynced[0].signalId)
        }

    @Test
    fun testMarkSignalAsSynced() =
        runTest {
            // Given
            val signal = Signal.sample(signalId = "SIG-001")
            database.insertOrReplaceSignal(signal, isSynced = false)

            // When
            database.markSignalAsSynced("SIG-001")
            val unsynced = database.getUnsyncedSignals().first()

            // Then
            assertEquals(0, unsynced.size)
        }

    @Test
    fun testDeleteSignal() =
        runTest {
            // Given
            val signal = Signal.sample(signalId = "SIG-001")
            database.insertOrReplaceSignal(signal, isSynced = false)

            // When
            database.deleteSignal("SIG-001")
            val retrieved = database.getSignalById("SIG-001").first()

            // Then
            assertNull(retrieved)
        }

    // ========================================
    // ORDER TESTS
    // ========================================

    @Test
    fun testInsertAndGetOrder() =
        runTest {
            // Given
            val order =
                Order.sample(
                    orderId = "ORD-001",
                    symbol = "BTC/USD",
                    side = OrderSide.BUY,
                )

            // When
            database.insertOrReplaceOrder(order, isSynced = false)
            val retrieved = database.getOrderById("ORD-001").first()

            // Then
            assertNotNull(retrieved)
            assertEquals(order.orderId, retrieved.orderId)
            assertEquals(order.symbol, retrieved.symbol)
            assertEquals(order.side, retrieved.side)
            assertEquals(order.quantity, retrieved.quantity)
        }

    @Test
    fun testGetActiveOrders() =
        runTest {
            // Given
            val activeOrder = Order.sample(orderId = "ORD-001").copy(status = OrderStatus.PENDING)
            val filledOrder = Order.sample(orderId = "ORD-002").copy(status = OrderStatus.FILLED)
            val cancelledOrder = Order.sample(orderId = "ORD-003").copy(status = OrderStatus.CANCELLED)

            // When
            database.insertOrReplaceOrder(activeOrder, isSynced = false)
            database.insertOrReplaceOrder(filledOrder, isSynced = false)
            database.insertOrReplaceOrder(cancelledOrder, isSynced = false)
            val active = database.getActiveOrders().first()

            // Then
            assertEquals(1, active.size)
            assertEquals("ORD-001", active[0].orderId)
        }

    @Test
    fun testGetOrdersBySymbol() =
        runTest {
            // Given
            val btcOrder1 = Order.sample(orderId = "ORD-001", symbol = "BTC/USD")
            val btcOrder2 = Order.sample(orderId = "ORD-002", symbol = "BTC/USD")
            val ethOrder = Order.sample(orderId = "ORD-003", symbol = "ETH/USD")

            // When
            database.insertOrReplaceOrder(btcOrder1, isSynced = false)
            database.insertOrReplaceOrder(btcOrder2, isSynced = false)
            database.insertOrReplaceOrder(ethOrder, isSynced = false)
            val btcOrders = database.getOrdersBySymbol("BTC/USD").first()

            // Then
            assertEquals(2, btcOrders.size)
            assertTrue(btcOrders.all { it.symbol == "BTC/USD" })
        }

    @Test
    fun testGetOrdersBySignalId() =
        runTest {
            // Given
            val order1 = Order.sample(orderId = "ORD-001").copy(signalId = "SIG-001")
            val order2 = Order.sample(orderId = "ORD-002").copy(signalId = "SIG-001")
            val order3 = Order.sample(orderId = "ORD-003").copy(signalId = "SIG-002")

            // When
            database.insertOrReplaceOrder(order1, isSynced = false)
            database.insertOrReplaceOrder(order2, isSynced = false)
            database.insertOrReplaceOrder(order3, isSynced = false)
            val signalOrders = database.getOrdersBySignalId("SIG-001").first()

            // Then
            assertEquals(2, signalOrders.size)
            assertTrue(signalOrders.all { it.signalId == "SIG-001" })
        }

    @Test
    fun testUpdateOrderStatus() =
        runTest {
            // Given
            val order = Order.sample(orderId = "ORD-001").copy(status = OrderStatus.PENDING)
            database.insertOrReplaceOrder(order, isSynced = false)

            // When
            database.updateOrderStatus("ORD-001", OrderStatus.FILLED)
            val updated = database.getOrderById("ORD-001").first()

            // Then
            assertNotNull(updated)
            assertEquals(OrderStatus.FILLED, updated.status)
        }

    @Test
    fun testGetUnsyncedOrders() =
        runTest {
            // Given
            val syncedOrder = Order.sample(orderId = "ORD-001")
            val unsyncedOrder = Order.sample(orderId = "ORD-002")

            // When
            database.insertOrReplaceOrder(syncedOrder, isSynced = true)
            database.insertOrReplaceOrder(unsyncedOrder, isSynced = false)
            val unsynced = database.getUnsyncedOrders().first()

            // Then
            assertEquals(1, unsynced.size)
            assertEquals("ORD-002", unsynced[0].orderId)
        }

    // ========================================
    // POSITION TESTS
    // ========================================

    @Test
    fun testInsertAndGetPosition() =
        runTest {
            // Given
            val position =
                Position.sample(
                    positionId = "POS-001",
                    symbol = "BTC/USD",
                    side = OrderSide.BUY,
                )

            // When
            database.insertOrReplacePosition(position, isSynced = false)
            val retrieved = database.getPositionById("POS-001").first()

            // Then
            assertNotNull(retrieved)
            assertEquals(position.positionId, retrieved.positionId)
            assertEquals(position.symbol, retrieved.symbol)
            assertEquals(position.side, retrieved.side)
            assertEquals(position.quantity, retrieved.quantity)
        }

    @Test
    fun testGetOpenPositions() =
        runTest {
            // Given
            val openPosition = Position.sample(positionId = "POS-001").copy(status = PositionStatus.OPEN)
            val closedPosition = Position.sample(positionId = "POS-002").copy(status = PositionStatus.CLOSED)

            // When
            database.insertOrReplacePosition(openPosition, isSynced = false)
            database.insertOrReplacePosition(closedPosition, isSynced = false)
            val open = database.getOpenPositions().first()

            // Then
            assertEquals(1, open.size)
            assertEquals("POS-001", open[0].positionId)
            assertEquals(PositionStatus.OPEN, open[0].status)
        }

    @Test
    fun testGetClosedPositions() =
        runTest {
            // Given
            val openPosition = Position.sample(positionId = "POS-001").copy(status = PositionStatus.OPEN)
            val closedPosition = Position.sample(positionId = "POS-002").copy(status = PositionStatus.CLOSED)

            // When
            database.insertOrReplacePosition(openPosition, isSynced = false)
            database.insertOrReplacePosition(closedPosition, isSynced = false)
            val closed = database.getClosedPositions().first()

            // Then
            assertEquals(1, closed.size)
            assertEquals("POS-002", closed[0].positionId)
            assertEquals(PositionStatus.CLOSED, closed[0].status)
        }

    @Test
    fun testGetPositionsBySymbol() =
        runTest {
            // Given
            val btcPosition1 = Position.sample(positionId = "POS-001", symbol = "BTC/USD")
            val btcPosition2 = Position.sample(positionId = "POS-002", symbol = "BTC/USD")
            val ethPosition = Position.sample(positionId = "POS-003", symbol = "ETH/USD")

            // When
            database.insertOrReplacePosition(btcPosition1, isSynced = false)
            database.insertOrReplacePosition(btcPosition2, isSynced = false)
            database.insertOrReplacePosition(ethPosition, isSynced = false)
            val btcPositions = database.getPositionsBySymbol("BTC/USD").first()

            // Then
            assertEquals(2, btcPositions.size)
            assertTrue(btcPositions.all { it.symbol == "BTC/USD" })
        }

    @Test
    fun testUpdatePositionPriceAndPnL() =
        runTest {
            // Given
            val position = Position.sample(positionId = "POS-001")
            database.insertOrReplacePosition(position, isSynced = false)

            // When
            database.updatePositionPriceAndPnL("POS-001", 52000.0, 2000.0)
            val updated = database.getPositionById("POS-001").first()

            // Then
            assertNotNull(updated)
            assertEquals(52000.0, updated.currentPrice)
            assertEquals(2000.0, updated.unrealizedPnL)
        }

    @Test
    fun testClosePosition() =
        runTest {
            // Given
            val position = Position.sample(positionId = "POS-001").copy(status = PositionStatus.OPEN)
            database.insertOrReplacePosition(position, isSynced = false)

            // When
            database.closePosition("POS-001", 1500.0)
            val updated = database.getPositionById("POS-001").first()

            // Then
            assertNotNull(updated)
            assertEquals(PositionStatus.CLOSED, updated.status)
            assertEquals(1500.0, updated.realizedPnL)
            assertNotNull(updated.closedAt)
        }

    @Test
    fun testGetUnsyncedPositions() =
        runTest {
            // Given
            val syncedPosition = Position.sample(positionId = "POS-001")
            val unsyncedPosition = Position.sample(positionId = "POS-002")

            // When
            database.insertOrReplacePosition(syncedPosition, isSynced = true)
            database.insertOrReplacePosition(unsyncedPosition, isSynced = false)
            val unsynced = database.getUnsyncedPositions().first()

            // Then
            assertEquals(1, unsynced.size)
            assertEquals("POS-002", unsynced[0].positionId)
        }

    // ========================================
    // GENERAL TESTS
    // ========================================

    @Test
    fun testClearAllData() =
        runTest {
            // Given
            database.insertOrReplaceSignal(Signal.sample(signalId = "SIG-001"), isSynced = false)
            database.insertOrReplaceOrder(Order.sample(orderId = "ORD-001"), isSynced = false)
            database.insertOrReplacePosition(Position.sample(positionId = "POS-001"), isSynced = false)

            // When
            database.clearAllData()

            // Then
            assertEquals(0, database.getAllSignals().first().size)
            assertEquals(0, database.getAllOrders().first().size)
            assertEquals(0, database.getAllPositions().first().size)
        }

    @Test
    fun testReplaceSignalUpdatesExisting() =
        runTest {
            // Given
            val signal1 = Signal.sample(signalId = "SIG-001", symbol = "BTC/USD")
            database.insertOrReplaceSignal(signal1, isSynced = false)

            // When - Insert same ID with different data
            val signal2 = signal1.copy(symbol = "ETH/USD")
            database.insertOrReplaceSignal(signal2, isSynced = false)

            // Then
            val all = database.getAllSignals().first()
            assertEquals(1, all.size) // Should still be 1, not 2
            assertEquals("ETH/USD", all[0].symbol) // Should have updated value
        }
}
