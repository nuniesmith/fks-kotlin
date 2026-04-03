package xyz.fkstrading.shared.data.repository

import kotlinx.coroutines.flow.Flow
import xyz.fkstrading.shared.domain.models.Order
import xyz.fkstrading.shared.domain.models.OrderStatus

/**
 * Repository interface for managing trading orders
 * Provides offline-first data access with automatic sync
 */
interface OrderRepository {
    /**
     * Observes an order by ID
     * Returns a Flow that emits the order whenever it changes
     */
    fun observeOrder(orderId: String): Flow<Order?>

    /**
     * Observes all orders
     * Returns a Flow that emits the list of orders whenever it changes
     */
    fun observeAllOrders(): Flow<List<Order>>

    /**
     * Observes orders for a specific symbol
     */
    fun observeOrdersBySymbol(symbol: String): Flow<List<Order>>

    /**
     * Observes active orders (PENDING, OPEN, PARTIALLY_FILLED)
     */
    fun observeActiveOrders(): Flow<List<Order>>

    /**
     * Observes orders by status
     */
    fun observeOrdersByStatus(status: OrderStatus): Flow<List<Order>>

    /**
     * Observes orders related to a specific signal
     */
    fun observeOrdersBySignalId(signalId: String): Flow<List<Order>>

    /**
     * Observes recent orders (limited number)
     */
    fun observeRecentOrders(limit: Int = 50): Flow<List<Order>>

    /**
     * Gets an order by ID (one-time fetch)
     */
    suspend fun getOrderById(orderId: String): Order?

    /**
     * Gets all orders (one-time fetch)
     */
    suspend fun getAllOrders(): List<Order>

    /**
     * Gets orders for a specific symbol (one-time fetch)
     */
    suspend fun getOrdersBySymbol(symbol: String): List<Order>

    /**
     * Gets active orders (one-time fetch)
     */
    suspend fun getActiveOrders(): List<Order>

    /**
     * Saves an order to local database
     * If offline, marks for sync when connection is restored
     */
    suspend fun saveOrder(order: Order)

    /**
     * Saves multiple orders in batch
     */
    suspend fun saveOrders(orders: List<Order>)

    /**
     * Updates an order's status
     */
    suspend fun updateOrderStatus(
        orderId: String,
        status: OrderStatus,
    )

    /**
     * Deletes an order by ID
     */
    suspend fun deleteOrder(orderId: String)

    /**
     * Deletes old orders older than the specified timestamp
     */
    suspend fun deleteOldOrders(olderThanMillis: Long)

    /**
     * Syncs local changes with remote server
     * Returns true if sync was successful
     */
    suspend fun sync(): Boolean

    /**
     * Forces a refresh from remote server
     */
    suspend fun refresh()

    /**
     * Clears all local orders
     */
    suspend fun clearAll()
}
