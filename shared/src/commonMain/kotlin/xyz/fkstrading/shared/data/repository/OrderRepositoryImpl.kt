package xyz.fkstrading.shared.data.repository

import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import xyz.fkstrading.shared.data.db.DatabaseWrapper
import xyz.fkstrading.shared.domain.models.Order
import xyz.fkstrading.shared.domain.models.OrderStatus

/**
 * Offline-first implementation of OrderRepository
 *
 * Strategy:
 * - All reads come from local database
 * - All writes go to local database immediately
 * - Sync with remote happens in background
 * - Conflict resolution favors server data
 */
class OrderRepositoryImpl(
    private val database: DatabaseWrapper,
    private val remoteDataSource: OrderRemoteDataSource? = null,
) : OrderRepository {
    private val syncMutex = Mutex()
    private val _syncStatus = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
    val syncStatus: StateFlow<SyncStatus> = _syncStatus.asStateFlow()

    // ========================================
    // OBSERVE OPERATIONS
    // ========================================

    override fun observeOrder(orderId: String): Flow<Order?> {
        return database.getOrderById(orderId)
    }

    override fun observeAllOrders(): Flow<List<Order>> {
        return database.getAllOrders()
    }

    override fun observeOrdersBySymbol(symbol: String): Flow<List<Order>> {
        return database.getOrdersBySymbol(symbol)
    }

    override fun observeActiveOrders(): Flow<List<Order>> {
        return database.getActiveOrders()
    }

    override fun observeOrdersByStatus(status: OrderStatus): Flow<List<Order>> {
        return database.getAllOrders().map { orders ->
            orders.filter { it.status == status }
        }
    }

    override fun observeOrdersBySignalId(signalId: String): Flow<List<Order>> {
        return database.getOrdersBySignalId(signalId)
    }

    override fun observeRecentOrders(limit: Int): Flow<List<Order>> {
        return database.getAllOrders().map { orders ->
            orders.take(limit)
        }
    }

    // ========================================
    // GET OPERATIONS
    // ========================================

    override suspend fun getOrderById(orderId: String): Order? {
        return database.getOrderById(orderId).first()
    }

    override suspend fun getAllOrders(): List<Order> {
        return database.getAllOrders().first()
    }

    override suspend fun getOrdersBySymbol(symbol: String): List<Order> {
        return database.getOrdersBySymbol(symbol).first()
    }

    override suspend fun getActiveOrders(): List<Order> {
        return database.getActiveOrders().first()
    }

    // ========================================
    // WRITE OPERATIONS
    // ========================================

    override suspend fun saveOrder(order: Order) {
        // Save to local database immediately
        database.insertOrReplaceOrder(order, isSynced = false)

        // Try to sync to remote in background if available
        if (remoteDataSource != null) {
            try {
                remoteDataSource.saveOrder(order)
                database.markOrderAsSynced(order.orderId)
            } catch (e: Exception) {
                // Failed to sync, will be retried during next sync operation
                println("Failed to sync order ${order.orderId} to remote: ${e.message}")
            }
        }
    }

    override suspend fun saveOrders(orders: List<Order>) {
        orders.forEach { order ->
            database.insertOrReplaceOrder(order, isSynced = false)
        }

        // Try to sync to remote in background if available
        if (remoteDataSource != null) {
            try {
                orders.forEach { order ->
                    remoteDataSource.saveOrder(order)
                    database.markOrderAsSynced(order.orderId)
                }
            } catch (e: Exception) {
                println("Failed to sync orders to remote: ${e.message}")
            }
        }
    }

    override suspend fun updateOrderStatus(
        orderId: String,
        status: OrderStatus,
    ) {
        database.updateOrderStatus(orderId, status)

        // Try to sync to remote if available
        if (remoteDataSource != null) {
            try {
                remoteDataSource.updateOrderStatus(orderId, status)
            } catch (e: Exception) {
                println("Failed to sync order status update to remote: ${e.message}")
            }
        }
    }

    override suspend fun deleteOrder(orderId: String) {
        database.deleteOrder(orderId)

        // Try to delete from remote if available
        if (remoteDataSource != null) {
            try {
                remoteDataSource.deleteOrder(orderId)
            } catch (e: Exception) {
                println("Failed to delete order $orderId from remote: ${e.message}")
            }
        }
    }

    override suspend fun deleteOldOrders(olderThanMillis: Long) {
        // Note: This is a local-only operation
        // We don't delete from remote to preserve history
        val allOrders = database.getAllOrders().first()
        allOrders.forEach { order ->
            if (order.timestamp.toEpochMilliseconds() < olderThanMillis) {
                database.deleteOrder(order.orderId)
            }
        }
    }

    // ========================================
    // SYNC OPERATIONS
    // ========================================

    override suspend fun sync(): Boolean =
        syncMutex.withLock {
            if (remoteDataSource == null) {
                return false
            }

            return try {
                _syncStatus.value = SyncStatus.Syncing

                // Step 1: Push unsynced local changes to remote
                val unsyncedOrders = database.getUnsyncedOrders().first()
                unsyncedOrders.forEach { order ->
                    try {
                        remoteDataSource.saveOrder(order)
                        database.markOrderAsSynced(order.orderId)
                    } catch (e: Exception) {
                        println("Failed to sync order ${order.orderId}: ${e.message}")
                    }
                }

                // Step 2: Pull latest orders from remote
                val remoteOrders = remoteDataSource.getRecentOrders(limit = 100)
                remoteOrders.forEach { order ->
                    database.insertOrReplaceOrder(order, isSynced = true)
                }

                _syncStatus.value = SyncStatus.Success
                true
            } catch (e: Exception) {
                _syncStatus.value = SyncStatus.Error(e.message ?: "Unknown error")
                println("Sync failed: ${e.message}")
                false
            } finally {
                if (_syncStatus.value is SyncStatus.Syncing) {
                    _syncStatus.value = SyncStatus.Idle
                }
            }
        }

    override suspend fun refresh() {
        if (remoteDataSource == null) {
            return
        }

        try {
            _syncStatus.value = SyncStatus.Syncing

            // Fetch latest orders from remote
            val remoteOrders = remoteDataSource.getRecentOrders(limit = 100)
            remoteOrders.forEach { order ->
                database.insertOrReplaceOrder(order, isSynced = true)
            }

            _syncStatus.value = SyncStatus.Success
        } catch (e: Exception) {
            _syncStatus.value = SyncStatus.Error(e.message ?: "Unknown error")
            println("Refresh failed: ${e.message}")
        } finally {
            if (_syncStatus.value is SyncStatus.Syncing) {
                _syncStatus.value = SyncStatus.Idle
            }
        }
    }

    // ========================================
    // CLEANUP OPERATIONS
    // ========================================

    override suspend fun clearAll() {
        // Note: This only clears local data, not remote
        database.getAllOrders().first().forEach { order ->
            database.deleteOrder(order.orderId)
        }
    }
}

/**
 * Interface for remote data source operations
 * This allows injecting different implementations (REST API, GraphQL, etc.)
 */
interface OrderRemoteDataSource {
    suspend fun getOrderById(orderId: String): Order?

    suspend fun getRecentOrders(limit: Int): List<Order>

    suspend fun getOrdersBySymbol(symbol: String): List<Order>

    suspend fun getActiveOrders(): List<Order>

    suspend fun saveOrder(order: Order)

    suspend fun updateOrderStatus(
        orderId: String,
        status: OrderStatus,
    )

    suspend fun deleteOrder(orderId: String)
}
