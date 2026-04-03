package xyz.fkstrading.shared.domain.models

/**
 * Extension functions for Order model.
 */

/**
 * Checks if this order is active (not in a terminal state).
 *
 * An order is considered active if it's in one of these states:
 * - PENDING
 * - SUBMITTED
 * - ACCEPTED
 * - PARTIALLY_FILLED
 *
 * Terminal states (not active):
 * - FILLED
 * - CANCELLED
 * - REJECTED
 * - EXPIRED
 */
fun Order.isActive(): Boolean {
    return when (status) {
        OrderStatus.PENDING,
        OrderStatus.SUBMITTED,
        OrderStatus.ACCEPTED,
        OrderStatus.PARTIALLY_FILLED,
        -> true

        OrderStatus.FILLED,
        OrderStatus.CANCELLED,
        OrderStatus.REJECTED,
        OrderStatus.EXPIRED,
        -> false
    }
}

/**
 * Checks if this order is in a terminal state (completed, no longer active).
 */
fun Order.isTerminal(): Boolean = !isActive()

/**
 * Alias for [isTerminal] — checks if this order is in a completed (terminal) state.
 * Terminal states: FILLED, CANCELLED, REJECTED, EXPIRED.
 */
fun Order.isComplete(): Boolean = isTerminal()

/**
 * Checks if this order has been filled (completely or partially).
 */
fun Order.isFilled(): Boolean {
    return status == OrderStatus.FILLED || status == OrderStatus.PARTIALLY_FILLED
}

/**
 * Checks if this order is completely filled.
 */
fun Order.isCompletelyFilled(): Boolean = status == OrderStatus.FILLED

/**
 * Checks if this order is pending (not yet submitted).
 */
fun Order.isPending(): Boolean = status == OrderStatus.PENDING

/**
 * Checks if this order has been cancelled.
 */
fun Order.isCancelled(): Boolean = status == OrderStatus.CANCELLED

/**
 * Checks if this order was rejected.
 */
fun Order.isRejected(): Boolean = status == OrderStatus.REJECTED

/**
 * Gets the remaining quantity to be filled.
 */
fun Order.remainingQuantity(): Double = quantity - filledQuantity

/**
 * Gets the fill percentage (0.0 to 1.0).
 */
fun Order.fillPercentage(): Double {
    return if (quantity > 0.0) {
        filledQuantity / quantity
    } else {
        0.0
    }
}
