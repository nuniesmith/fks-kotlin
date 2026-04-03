package xyz.fkstrading.shared.domain.strategy

import kotlin.math.pow
import kotlin.math.round

/**
 * Shared utility functions for strategy execution.
 */

/**
 * Extension function to format doubles for display.
 * Uses a platform-agnostic approach compatible with Kotlin/Native.
 */
fun Double.format(decimals: Int = 2): String {
    val multiplier = 10.0.pow(decimals)
    val rounded = round(this * multiplier) / multiplier
    return rounded.toString()
}
