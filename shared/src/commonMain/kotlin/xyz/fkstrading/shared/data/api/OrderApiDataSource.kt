package xyz.fkstrading.shared.data.api

import xyz.fkstrading.shared.data.repository.OrderRemoteDataSource
import xyz.fkstrading.shared.domain.models.Order
import xyz.fkstrading.shared.domain.models.OrderStatus

/**
 * REST API implementation of OrderRemoteDataSource
 *
 * Provides order data operations using HTTP endpoints.
 * This implementation uses FksApiClient for network communication.
 */
class OrderApiDataSource(
    private val apiClient: FksApiClient,
) : OrderRemoteDataSource {
    override suspend fun getOrderById(orderId: String): Order? {
        return apiClient.getOrderById(orderId)
            .getOrNull()
    }

    override suspend fun getRecentOrders(limit: Int): List<Order> {
        return apiClient.getRecentOrders(limit)
            .getOrElse { emptyList() }
    }

    override suspend fun getOrdersBySymbol(symbol: String): List<Order> {
        return apiClient.getOrdersBySymbol(symbol)
            .getOrElse { emptyList() }
    }

    override suspend fun getActiveOrders(): List<Order> {
        return apiClient.getActiveOrders()
            .getOrElse { emptyList() }
    }

    override suspend fun saveOrder(order: Order) {
        val result = apiClient.createOrder(order)
        if (result.isFailure) {
            throw result.exceptionOrNull() ?: Exception("Failed to save order")
        }
    }

    override suspend fun updateOrderStatus(
        orderId: String,
        status: OrderStatus,
    ) {
        // First get the order, then update it
        val order =
            getOrderById(orderId)
                ?: throw Exception("Order not found: $orderId")

        val updatedOrder =
            order.copy(
                status = status,
                updatedAt = kotlinx.datetime.Clock.System.now(),
            )

        val result = apiClient.updateOrder(updatedOrder)
        if (result.isFailure) {
            throw result.exceptionOrNull() ?: Exception("Failed to update order status")
        }
    }

    override suspend fun deleteOrder(orderId: String) {
        val result = apiClient.deleteOrder(orderId)
        if (result.isFailure) {
            throw result.exceptionOrNull() ?: Exception("Failed to delete order")
        }
    }
}
