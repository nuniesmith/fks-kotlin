package xyz.fkstrading.shared.domain.usecases

import kotlinx.coroutines.flow.Flow
import xyz.fkstrading.shared.data.api.DashboardOverviewResponse
import xyz.fkstrading.shared.data.api.FksApiClient
import xyz.fkstrading.shared.data.api.JanusHealthResponse
import xyz.fkstrading.shared.data.repository.OrderRepository
import xyz.fkstrading.shared.data.repository.PositionRepository
import xyz.fkstrading.shared.data.repository.SignalRepository
import xyz.fkstrading.shared.domain.models.Order
import xyz.fkstrading.shared.domain.models.Position
import xyz.fkstrading.shared.domain.models.Signal

// =============================================================================
// Signal Use Cases
// =============================================================================

/**
 * Retrieves recent trading signals.
 *
 * Uses the local repository (offline-first) for observed data,
 * falling back to the API client for one-shot fetches.
 */
class GetRecentSignalsUseCase(
    private val signalRepository: SignalRepository,
) {
    /**
     * Observe recent signals as a reactive Flow.
     *
     * @param limit Maximum number of signals to return
     * @return Flow emitting updated signal lists
     */
    fun observe(limit: Int = 50): Flow<List<Signal>> {
        return signalRepository.observeRecentSignals(limit)
    }

    /**
     * One-shot fetch of all signals.
     */
    suspend operator fun invoke(): Result<List<Signal>> {
        return runCatching { signalRepository.getAllSignals() }
    }
}

/**
 * Retrieves signals filtered by trading symbol.
 */
class GetSignalsBySymbolUseCase(
    private val signalRepository: SignalRepository,
) {
    fun observe(symbol: String): Flow<List<Signal>> {
        return signalRepository.observeSignalsBySymbol(symbol)
    }

    suspend operator fun invoke(symbol: String): Result<List<Signal>> {
        return runCatching { signalRepository.getSignalsBySymbol(symbol) }
    }
}

/**
 * Retrieves a single signal by its ID.
 */
class GetSignalByIdUseCase(
    private val signalRepository: SignalRepository,
) {
    fun observe(signalId: String): Flow<Signal?> {
        return signalRepository.observeSignal(signalId)
    }

    suspend operator fun invoke(signalId: String): Result<Signal?> {
        return runCatching { signalRepository.getSignalById(signalId) }
    }
}

// =============================================================================
// Order Use Cases
// =============================================================================

/**
 * Retrieves recent trading orders.
 */
class GetRecentOrdersUseCase(
    private val orderRepository: OrderRepository,
) {
    fun observe(limit: Int = 50): Flow<List<Order>> {
        return orderRepository.observeRecentOrders(limit)
    }

    suspend operator fun invoke(): Result<List<Order>> {
        return runCatching { orderRepository.getAllOrders() }
    }
}

/**
 * Retrieves currently active orders (PENDING, OPEN, PARTIALLY_FILLED).
 */
class GetActiveOrdersUseCase(
    private val orderRepository: OrderRepository,
) {
    fun observe(): Flow<List<Order>> {
        return orderRepository.observeActiveOrders()
    }

    suspend operator fun invoke(): Result<List<Order>> {
        return runCatching { orderRepository.getActiveOrders() }
    }
}

/**
 * Retrieves orders filtered by trading symbol.
 */
class GetOrdersBySymbolUseCase(
    private val orderRepository: OrderRepository,
) {
    fun observe(symbol: String): Flow<List<Order>> {
        return orderRepository.observeOrdersBySymbol(symbol)
    }

    suspend operator fun invoke(symbol: String): Result<List<Order>> {
        return runCatching { orderRepository.getOrdersBySymbol(symbol) }
    }
}

/**
 * Creates a new trading order.
 *
 * Saves the order locally first (offline-first), then syncs with the API.
 */
class CreateOrderUseCase(
    private val orderRepository: OrderRepository,
    private val apiClient: FksApiClient,
) {
    suspend operator fun invoke(order: Order): Result<Order> {
        return runCatching {
            // Submit to API
            val createdOrder = apiClient.createOrder(order).getOrThrow()
            // Persist locally
            orderRepository.saveOrder(createdOrder)
            createdOrder
        }
    }
}

/**
 * Cancels an existing order.
 */
class CancelOrderUseCase(
    private val apiClient: FksApiClient,
    private val orderRepository: OrderRepository,
) {
    suspend operator fun invoke(orderId: String): Result<Order> {
        return runCatching {
            val cancelledOrder = apiClient.cancelOrder(orderId).getOrThrow()
            orderRepository.saveOrder(cancelledOrder)
            cancelledOrder
        }
    }
}

// =============================================================================
// Position Use Cases
// =============================================================================

/**
 * Retrieves all trading positions.
 */
class GetPositionsUseCase(
    private val positionRepository: PositionRepository,
) {
    fun observe(): Flow<List<Position>> {
        return positionRepository.observeAllPositions()
    }

    suspend operator fun invoke(): Result<List<Position>> {
        return runCatching { positionRepository.getAllPositions() }
    }
}

/**
 * Retrieves only open (active) positions.
 */
class GetOpenPositionsUseCase(
    private val positionRepository: PositionRepository,
) {
    fun observe(): Flow<List<Position>> {
        return positionRepository.observeOpenPositions()
    }

    suspend operator fun invoke(): Result<List<Position>> {
        return runCatching { positionRepository.getOpenPositions() }
    }
}

/**
 * Retrieves closed positions for performance review.
 */
class GetClosedPositionsUseCase(
    private val positionRepository: PositionRepository,
) {
    fun observe(): Flow<List<Position>> {
        return positionRepository.observeClosedPositions()
    }

    suspend operator fun invoke(): Result<List<Position>> {
        return runCatching { positionRepository.getClosedPositions() }
    }
}

/**
 * Retrieves positions filtered by trading symbol.
 */
class GetPositionsBySymbolUseCase(
    private val positionRepository: PositionRepository,
) {
    fun observe(symbol: String): Flow<List<Position>> {
        return positionRepository.observePositionsBySymbol(symbol)
    }

    suspend operator fun invoke(symbol: String): Result<List<Position>> {
        return runCatching { positionRepository.getPositionsBySymbol(symbol) }
    }
}

/**
 * Closes an existing position.
 */
class ClosePositionUseCase(
    private val apiClient: FksApiClient,
    private val positionRepository: PositionRepository,
) {
    suspend operator fun invoke(positionId: String): Result<Position> {
        return runCatching {
            val closedPosition = apiClient.closePosition(positionId).getOrThrow()
            positionRepository.savePosition(closedPosition)
            closedPosition
        }
    }
}

// =============================================================================
// System / Health Use Cases
// =============================================================================

/**
 * Checks the health of the janus Brain API (`GET /health`).
 */
class GetSystemHealthUseCase(
    private val apiClient: FksApiClient,
) {
    suspend operator fun invoke(): Result<JanusHealthResponse> {
        return apiClient.getHealth()
    }
}

/**
 * Retrieves the janus dashboard overview (`GET /api/dashboard/overview`).
 */
class GetSystemStatusUseCase(
    private val apiClient: FksApiClient,
) {
    suspend operator fun invoke(): Result<DashboardOverviewResponse> {
        return apiClient.getStatus()
    }
}

// =============================================================================
// Sync Use Cases
// =============================================================================

/**
 * Synchronizes all local data with the remote server.
 *
 * Triggers sync on signals, orders, and positions repositories.
 * Returns true only if all syncs succeed.
 */
class SyncAllDataUseCase(
    private val signalRepository: SignalRepository,
    private val orderRepository: OrderRepository,
    private val positionRepository: PositionRepository,
) {
    suspend operator fun invoke(): Result<Boolean> {
        return runCatching {
            val signalSync = signalRepository.sync()
            val orderSync = orderRepository.sync()
            val positionSync = positionRepository.sync()
            signalSync && orderSync && positionSync
        }
    }
}

/**
 * Forces a full refresh of all data from the remote server.
 */
class RefreshAllDataUseCase(
    private val signalRepository: SignalRepository,
    private val orderRepository: OrderRepository,
    private val positionRepository: PositionRepository,
) {
    suspend operator fun invoke(): Result<Unit> {
        return runCatching {
            signalRepository.refresh()
            orderRepository.refresh()
            positionRepository.refresh()
        }
    }
}
