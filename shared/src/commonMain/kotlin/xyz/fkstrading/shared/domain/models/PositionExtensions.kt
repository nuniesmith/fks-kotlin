package xyz.fkstrading.shared.domain.models

/**
 * Extension functions for Position model.
 */

/**
 * Calculates the unrealized PnL for this position based on current price.
 *
 * @param currentPrice The current market price of the asset
 * @return The unrealized profit/loss
 */
fun Position.calculateUnrealizedPnL(currentPrice: Double): Double {
    val priceDiff =
        when (side) {
            OrderSide.BUY -> currentPrice - entryPrice
            OrderSide.SELL -> entryPrice - currentPrice
        }
    return priceDiff * quantity
}

/**
 * Checks if this position is a long position (BUY side).
 */
fun Position.isLong(): Boolean = side == OrderSide.BUY

/**
 * Checks if this position is a short position (SELL side).
 */
fun Position.isShort(): Boolean = side == OrderSide.SELL

/**
 * Checks if this position is currently open.
 */
fun Position.isOpen(): Boolean = status == PositionStatus.OPEN

/**
 * Checks if this position is closed.
 */
fun Position.isClosed(): Boolean = status == PositionStatus.CLOSED

/**
 * Checks if this position is partially closed.
 */
fun Position.isPartiallyClosed(): Boolean = status == PositionStatus.PARTIALLY_CLOSED

/**
 * Gets the current profit/loss percentage.
 *
 * @return The PnL as a percentage of the entry value
 */
fun Position.pnlPercentage(): Double {
    val entryValue = entryPrice * quantity
    return if (entryValue > 0.0) {
        (unrealizedPnL / entryValue) * 100.0
    } else {
        0.0
    }
}

/**
 * Gets the total PnL (realized + unrealized).
 */
fun Position.totalPnL(): Double = realizedPnL + unrealizedPnL

/**
 * Checks if the position is currently profitable.
 */
fun Position.isProfitable(): Boolean = unrealizedPnL > 0.0

/**
 * Checks if the position is currently at a loss.
 */
fun Position.isLosing(): Boolean = unrealizedPnL < 0.0

/**
 * Gets the current value of the position.
 */
fun Position.currentValue(): Double = currentPrice * quantity

/**
 * Gets the entry value of the position.
 */
fun Position.entryValue(): Double = entryPrice * quantity

/**
 * Checks if stop loss has been hit.
 *
 * @return true if current price has hit the stop loss level
 */
fun Position.isStopLossHit(): Boolean {
    if (stopLoss == null) return false

    return when (side) {
        OrderSide.BUY -> currentPrice <= stopLoss
        OrderSide.SELL -> currentPrice >= stopLoss
    }
}

/**
 * Checks if take profit has been hit.
 *
 * @return true if current price has hit the take profit level
 */
fun Position.isTakeProfitHit(): Boolean {
    if (takeProfit == null) return false

    return when (side) {
        OrderSide.BUY -> currentPrice >= takeProfit
        OrderSide.SELL -> currentPrice <= takeProfit
    }
}

/**
 * Gets the distance to stop loss as a percentage.
 *
 * @return The percentage distance to stop loss, or null if no stop loss is set
 */
fun Position.distanceToStopLossPercent(): Double? {
    if (stopLoss == null) return null

    val distance =
        when (side) {
            OrderSide.BUY -> ((currentPrice - stopLoss) / currentPrice) * 100.0
            OrderSide.SELL -> ((stopLoss - currentPrice) / currentPrice) * 100.0
        }

    return distance
}

/**
 * Gets the distance to take profit as a percentage.
 *
 * @return The percentage distance to take profit, or null if no take profit is set
 */
fun Position.distanceToTakeProfitPercent(): Double? {
    if (takeProfit == null) return null

    val distance =
        when (side) {
            OrderSide.BUY -> ((takeProfit - currentPrice) / currentPrice) * 100.0
            OrderSide.SELL -> ((currentPrice - takeProfit) / currentPrice) * 100.0
        }

    return distance
}

/**
 * Creates a copy of this position with updated current price and unrealized PnL.
 *
 * @param newPrice The new current price
 * @return A copy of the position with updated values
 */
fun Position.withUpdatedPrice(newPrice: Double): Position {
    return copy(
        currentPrice = newPrice,
        unrealizedPnL = calculateUnrealizedPnL(newPrice),
        unrealizedPnl = calculateUnrealizedPnL(newPrice),
    )
}
