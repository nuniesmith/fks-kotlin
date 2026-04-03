package xyz.fkstrading.client.features.orders

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import xyz.fkstrading.shared.data.repository.OrderRepository
import xyz.fkstrading.shared.domain.models.*

/**
 * ViewModel for Orders Screen
 *
 * Manages order state including:
 * - Active and completed orders
 * - Order creation
 * - Order cancellation
 * - Order filtering and search
 * - Order history
 */
class OrdersViewModel(
    private val orderRepository: OrderRepository,
) {
    private val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // UI State
    private val _uiState = MutableStateFlow<OrdersUiState>(OrdersUiState.Loading)
    val uiState: StateFlow<OrdersUiState> = _uiState.asStateFlow()

    // Filter state
    private val _filterState = MutableStateFlow(OrderFilter())
    val filterState: StateFlow<OrderFilter> = _filterState.asStateFlow()

    // Order creation state
    private val _orderCreationState = MutableStateFlow<OrderCreationState>(OrderCreationState.Idle)
    val orderCreationState: StateFlow<OrderCreationState> = _orderCreationState.asStateFlow()

    // Order metrics
    private val _orderMetrics = MutableStateFlow(OrderMetrics())
    val orderMetrics: StateFlow<OrderMetrics> = _orderMetrics.asStateFlow()

    init {
        observeOrders()
    }

    /**
     * Observes orders from repository and updates UI state
     */
    private fun observeOrders() {
        viewModelScope.launch {
            combine(
                orderRepository.observeAllOrders(),
                _filterState,
            ) { orders, filter ->
                applyFilter(orders, filter)
            }.catch { error ->
                _uiState.value =
                    OrdersUiState.Error(
                        error.message ?: "Failed to load orders",
                    )
            }.collect { filteredOrders ->
                updateMetrics(filteredOrders)
                _uiState.value =
                    if (filteredOrders.isEmpty()) {
                        OrdersUiState.Empty
                    } else {
                        OrdersUiState.Success(filteredOrders)
                    }
            }
        }
    }

    /**
     * Applies filter to orders list
     */
    private fun applyFilter(
        orders: List<Order>,
        filter: OrderFilter,
    ): List<Order> {
        var filtered = orders

        // Filter by status
        if (filter.activeOnly) {
            filtered = filtered.filter { it.isActive() }
        }
        if (filter.status != null) {
            filtered = filtered.filter { it.status == filter.status }
        }

        // Filter by symbol
        if (filter.symbol != null) {
            filtered =
                filtered.filter {
                    it.symbol.contains(filter.symbol, ignoreCase = true)
                }
        }

        // Filter by order type
        if (filter.orderType != null) {
            filtered = filtered.filter { it.orderType == filter.orderType }
        }

        // Filter by side
        if (filter.side != null) {
            filtered = filtered.filter { it.side == filter.side }
        }

        // Filter by search query
        if (filter.searchQuery.isNotBlank()) {
            filtered =
                filtered.filter { order ->
                    order.symbol.contains(filter.searchQuery, ignoreCase = true) ||
                        order.orderId.contains(filter.searchQuery, ignoreCase = true)
                }
        }

        // Sort
        filtered =
            when (filter.sortBy) {
                OrderSortOption.DATE_DESC -> filtered.sortedByDescending { it.timestamp }
                OrderSortOption.DATE_ASC -> filtered.sortedBy { it.timestamp }
                OrderSortOption.SYMBOL_ASC -> filtered.sortedBy { it.symbol }
                OrderSortOption.SYMBOL_DESC -> filtered.sortedByDescending { it.symbol }
                OrderSortOption.QUANTITY_DESC -> filtered.sortedByDescending { it.quantity }
                OrderSortOption.QUANTITY_ASC -> filtered.sortedBy { it.quantity }
            }

        return filtered
    }

    /**
     * Updates order metrics based on current orders
     */
    private fun updateMetrics(orders: List<Order>) {
        val activeOrders = orders.filter { it.isActive() }
        val completedOrders = orders.filter { it.isComplete() }
        val filledOrders = orders.filter { it.isFilled() }
        val cancelledOrders = orders.filter { it.status == OrderStatus.CANCELLED }
        val rejectedOrders = orders.filter { it.status == OrderStatus.REJECTED }

        val totalVolume = filledOrders.sumOf { it.quantity * (it.averageFillPrice ?: 0.0) }
        val totalFees = orders.sumOf { (it.fees ?: 0.0) + (it.commission ?: 0.0) }

        val fillRate =
            if (orders.isNotEmpty()) {
                (filledOrders.size.toDouble() / orders.size) * 100
            } else {
                0.0
            }

        _orderMetrics.value =
            OrderMetrics(
                totalOrders = orders.size,
                activeOrders = activeOrders.size,
                completedOrders = completedOrders.size,
                filledOrders = filledOrders.size,
                cancelledOrders = cancelledOrders.size,
                rejectedOrders = rejectedOrders.size,
                totalVolume = totalVolume,
                totalFees = totalFees,
                fillRate = fillRate,
            )
    }

    /**
     * Refreshes orders from remote server
     */
    fun refresh() {
        viewModelScope.launch {
            _uiState.value = OrdersUiState.Loading
            try {
                orderRepository.refresh()
            } catch (e: Exception) {
                _uiState.value =
                    OrdersUiState.Error(
                        e.message ?: "Failed to refresh orders",
                    )
            }
        }
    }

    /**
     * Creates a new order
     */
    fun createOrder(orderRequest: OrderRequest) {
        viewModelScope.launch {
            _orderCreationState.value = OrderCreationState.Creating
            try {
                val order = orderRequest.toOrder()
                orderRepository.saveOrder(order)
                _orderCreationState.value = OrderCreationState.Success(order)
            } catch (e: Exception) {
                _orderCreationState.value =
                    OrderCreationState.Error(
                        e.message ?: "Failed to create order",
                    )
            }
        }
    }

    /**
     * Cancels an order
     */
    fun cancelOrder(orderId: String) {
        viewModelScope.launch {
            try {
                orderRepository.updateOrderStatus(orderId, OrderStatus.CANCELLED)
            } catch (e: Exception) {
                println("Error cancelling order: ${e.message}")
            }
        }
    }

    /**
     * Deletes an order
     */
    fun deleteOrder(orderId: String) {
        viewModelScope.launch {
            try {
                orderRepository.deleteOrder(orderId)
            } catch (e: Exception) {
                println("Error deleting order: ${e.message}")
            }
        }
    }

    /**
     * Updates the filter state
     */
    fun updateFilter(filter: OrderFilter) {
        _filterState.value = filter
    }

    /**
     * Sets search query
     */
    fun setSearchQuery(query: String) {
        _filterState.value = _filterState.value.copy(searchQuery = query)
    }

    /**
     * Toggles active orders filter
     */
    fun toggleActiveOnly() {
        _filterState.value =
            _filterState.value.copy(
                activeOnly = !_filterState.value.activeOnly,
            )
    }

    /**
     * Shows all orders (clears filters)
     */
    fun showAllOrders() {
        _filterState.value = OrderFilter()
    }

    /**
     * Updates sort option
     */
    fun updateSortOption(sortOption: OrderSortOption) {
        _filterState.value = _filterState.value.copy(sortBy = sortOption)
    }

    /**
     * Clears all filters
     */
    fun clearFilters() {
        _filterState.value = OrderFilter()
    }

    /**
     * Resets order creation state
     */
    fun resetOrderCreationState() {
        _orderCreationState.value = OrderCreationState.Idle
    }

    /**
     * Cleanup resources
     */
    fun onCleared() {
        viewModelScope.launch {
            // Any cleanup needed
        }
    }
}

/**
 * UI state for orders screen
 */
sealed class OrdersUiState {
    object Loading : OrdersUiState()

    object Empty : OrdersUiState()

    data class Success(val orders: List<Order>) : OrdersUiState()

    data class Error(val message: String) : OrdersUiState()
}

/**
 * Order creation state
 */
sealed class OrderCreationState {
    object Idle : OrderCreationState()

    object Creating : OrderCreationState()

    data class Success(val order: Order) : OrderCreationState()

    data class Error(val message: String) : OrderCreationState()
}

/**
 * Filter configuration for orders
 */
data class OrderFilter(
    val activeOnly: Boolean = false,
    val status: OrderStatus? = null,
    val symbol: String? = null,
    val orderType: OrderType? = null,
    val side: OrderSide? = null,
    val searchQuery: String = "",
    val sortBy: OrderSortOption = OrderSortOption.DATE_DESC,
)

/**
 * Sort options for orders
 */
enum class OrderSortOption {
    DATE_DESC,
    DATE_ASC,
    SYMBOL_ASC,
    SYMBOL_DESC,
    QUANTITY_DESC,
    QUANTITY_ASC,
}

/**
 * Order metrics
 */
data class OrderMetrics(
    val totalOrders: Int = 0,
    val activeOrders: Int = 0,
    val completedOrders: Int = 0,
    val filledOrders: Int = 0,
    val cancelledOrders: Int = 0,
    val rejectedOrders: Int = 0,
    val totalVolume: Double = 0.0,
    val totalFees: Double = 0.0,
    val fillRate: Double = 0.0,
)

/**
 * Order creation request
 */
data class OrderRequest(
    val symbol: String,
    val side: OrderSide,
    val orderType: OrderType,
    val quantity: Double,
    val price: Double? = null,
    val stopPrice: Double? = null,
    val timeInForce: TimeInForce = TimeInForce.GTC,
    val signalId: String? = null,
    val strategyId: String? = null,
) {
    /**
     * Converts request to Order domain model
     */
    fun toOrder(): Order {
        return Order(
            orderId = generateOrderId(),
            symbol = symbol,
            side = side,
            orderType = orderType,
            quantity = quantity,
            price = price,
            stopPrice = stopPrice,
            status = OrderStatus.PENDING,
            timeInForce = timeInForce,
            timestamp = kotlinx.datetime.Clock.System.now(),
            signalId = signalId,
            strategyId = strategyId,
        )
    }

    /**
     * Validates the order request
     */
    fun isValid(): Boolean {
        return when {
            symbol.isBlank() -> false
            quantity <= 0 -> false
            orderType == OrderType.LIMIT && (price == null || price <= 0) -> false
            orderType == OrderType.STOP_LIMIT && (price == null || price <= 0) -> false
            (orderType == OrderType.STOP || orderType == OrderType.STOP_LIMIT) &&
                (stopPrice == null || stopPrice <= 0) -> false
            else -> true
        }
    }

    private fun generateOrderId(): String {
        return "ORD-${System.currentTimeMillis()}-${(1000..9999).random()}"
    }
}
