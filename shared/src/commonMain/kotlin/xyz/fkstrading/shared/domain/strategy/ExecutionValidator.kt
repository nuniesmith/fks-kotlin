package xyz.fkstrading.shared.domain.strategy

import xyz.fkstrading.shared.domain.models.Direction
import xyz.fkstrading.shared.domain.models.Order
import xyz.fkstrading.shared.domain.models.Position
import xyz.fkstrading.shared.domain.models.Signal
import xyz.fkstrading.shared.domain.models.isActive
import xyz.fkstrading.shared.domain.strategy.models.ExecutionConfig
import kotlin.math.abs

/**
 * Converts Signal Direction to Order Side name for comparison.
 */
private fun Direction.toOrderSideName(): String =
    when (this) {
        Direction.LONG -> "BUY"
        Direction.SHORT -> "SELL"
    }

/**
 * Validates execution requests before orders are placed.
 *
 * Performs pre-flight checks including:
 * - Signal quality validation
 * - Account balance checks
 * - Position limits enforcement
 * - Risk limit validation
 * - Duplicate order detection
 * - Asset whitelist/blacklist checks
 */
class ExecutionValidator {
    /**
     * Validates a signal for execution.
     *
     * @param signal Signal to validate
     * @param config Execution configuration
     * @param accountBalance Current account balance
     * @param currentPrice Current market price
     * @param existingPositions List of existing open positions
     * @param existingOrders List of existing active orders
     * @param positionSize Calculated position size
     * @param stopLossPrice Calculated stop-loss price (optional)
     * @return ValidationResult with errors/warnings
     */
    fun validate(
        signal: Signal,
        config: ExecutionConfig,
        accountBalance: Double,
        currentPrice: Double,
        existingPositions: List<Position>,
        existingOrders: List<Order>,
        positionSize: Double,
        stopLossPrice: Double? = null,
    ): ValidationResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        // 1. Validate signal quality
        validateSignalQuality(signal, config, errors, warnings)

        // 2. Validate account balance
        validateAccountBalance(accountBalance, currentPrice, positionSize, errors)

        // 3. Validate position limits
        validatePositionLimits(signal, config, existingPositions, errors)

        // 4. Validate risk limits
        validateRiskLimits(
            signal = signal,
            config = config,
            accountBalance = accountBalance,
            currentPrice = currentPrice,
            positionSize = positionSize,
            stopLossPrice = stopLossPrice,
            existingPositions = existingPositions,
            errors = errors,
            warnings = warnings,
        )

        // 5. Check for duplicate orders
        checkDuplicateOrders(signal, existingOrders, errors, warnings)

        // 6. Validate asset whitelist/blacklist
        validateAssetFilters(signal, config, errors)

        // 7. Validate position size is reasonable
        validatePositionSize(positionSize, currentPrice, accountBalance, errors, warnings)

        // 8. Validate stop-loss if present
        if (stopLossPrice != null) {
            validateStopLoss(signal, currentPrice, stopLossPrice, errors, warnings)
        }

        return if (errors.isEmpty()) {
            ValidationResult.Valid(warnings)
        } else {
            ValidationResult.Invalid(errors, warnings)
        }
    }

    /**
     * Validates signal quality (confidence, required fields).
     */
    private fun validateSignalQuality(
        signal: Signal,
        config: ExecutionConfig,
        errors: MutableList<String>,
        warnings: MutableList<String>,
    ) {
        // Check confidence threshold
        if (signal.confidence < config.minSignalConfidence) {
            errors.add(
                "Signal confidence (${(signal.confidence * 100).toInt()}%) " +
                    "below minimum (${(config.minSignalConfidence * 100).toInt()}%)",
            )
        }

        // Warn if confidence is borderline
        if (signal.confidence < config.minSignalConfidence + 0.1) {
            warnings.add(
                "Signal confidence (${(signal.confidence * 100).toInt()}%) " +
                    "is close to minimum threshold",
            )
        }

        // Check required fields
        if (signal.symbol.isBlank()) {
            errors.add("Signal symbol is blank")
        }

        if (signal.entryPrice <= 0) {
            errors.add("Signal entry price must be positive")
        }

        if (signal.stopLoss <= 0) {
            errors.add("Signal stop-loss must be positive")
        }

        if (signal.takeProfit <= 0) {
            errors.add("Signal take-profit must be positive")
        }

        // Warn if signal is old
        val signalAgeHours =
            (
                kotlinx.datetime.Clock.System.now().toEpochMilliseconds() -
                    signal.timestamp.toEpochMilliseconds()
            ) / (1000 * 60 * 60)
        if (signalAgeHours > 1) {
            warnings.add("Signal is ${signalAgeHours}h old - may be stale")
        }
    }

    /**
     * Validates sufficient account balance.
     */
    private fun validateAccountBalance(
        accountBalance: Double,
        currentPrice: Double,
        positionSize: Double,
        errors: MutableList<String>,
    ) {
        if (accountBalance <= 0) {
            errors.add("Account balance is zero or negative")
            return
        }

        val requiredCapital = positionSize * currentPrice

        // For cash accounts, need full capital
        if (requiredCapital > accountBalance) {
            errors.add(
                "Insufficient balance: need $${requiredCapital.toInt()} " +
                    "but have $${accountBalance.toInt()} " +
                    "(requires margin/leverage)",
            )
        }
    }

    /**
     * Validates position limits.
     */
    private fun validatePositionLimits(
        signal: Signal,
        config: ExecutionConfig,
        existingPositions: List<Position>,
        errors: MutableList<String>,
    ) {
        // Check max total positions
        if (existingPositions.size >= config.maxPositions) {
            errors.add(
                "Maximum positions limit reached " +
                    "(${existingPositions.size}/${config.maxPositions})",
            )
        }

        // Check max positions per asset
        val positionsInAsset = existingPositions.count { it.symbol == signal.symbol }
        if (positionsInAsset >= config.maxPositionsPerAsset) {
            errors.add(
                "Maximum positions in ${signal.symbol} reached " +
                    "($positionsInAsset/${config.maxPositionsPerAsset})",
            )
        }
    }

    /**
     * Validates risk limits.
     */
    private fun validateRiskLimits(
        signal: Signal,
        config: ExecutionConfig,
        accountBalance: Double,
        currentPrice: Double,
        positionSize: Double,
        stopLossPrice: Double?,
        existingPositions: List<Position>,
        errors: MutableList<String>,
        warnings: MutableList<String>,
    ) {
        // Calculate position value
        val positionValue = positionSize * currentPrice
        val positionPercent = positionValue / accountBalance

        // Warn if position is large
        if (positionPercent > 0.20) {
            warnings.add(
                "Large position: ${(positionPercent * 100).toInt()}% of account",
            )
        }

        // Error if position is too large (safety check)
        if (positionPercent > 0.50) {
            errors.add(
                "Position too large: ${(positionPercent * 100).toInt()}% of account " +
                    "(maximum 50%)",
            )
        }

        // Calculate risk amount if stop-loss provided
        if (stopLossPrice != null) {
            val stopDistance = abs(currentPrice - stopLossPrice)
            val riskAmount = positionSize * stopDistance
            val riskPercent = riskAmount / accountBalance

            // Check if risk exceeds configured limit
            if (riskPercent > config.riskPerTrade * 1.5) {
                errors.add(
                    "Risk per trade (${(riskPercent * 100).format(1)}%) " +
                        "significantly exceeds configured limit " +
                        "(${(config.riskPerTrade * 100).format(1)}%)",
                )
            }

            // Warn if risk is above configured limit
            if (riskPercent > config.riskPerTrade) {
                warnings.add(
                    "Risk per trade (${(riskPercent * 100).format(1)}%) " +
                        "above configured limit (${(config.riskPerTrade * 100).format(1)}%)",
                )
            }
        }

        // Calculate total portfolio exposure
        val existingExposure =
            existingPositions.sumOf { position ->
                // Estimate position value (would need current prices in real scenario)
                abs(position.quantity * position.entryPrice)
            }
        val totalExposure = existingExposure + positionValue
        val totalExposurePercent = totalExposure / accountBalance

        // Warn if total exposure is high
        if (totalExposurePercent > 1.5) {
            warnings.add(
                "High total exposure: ${(totalExposurePercent * 100).toInt()}% of account " +
                    "(${existingPositions.size + 1} positions)",
            )
        }

        // Error if exposure is excessive
        if (totalExposurePercent > 3.0) {
            errors.add(
                "Excessive total exposure: ${(totalExposurePercent * 100).toInt()}% of account " +
                    "(maximum 300%)",
            )
        }
    }

    /**
     * Checks for duplicate or conflicting orders.
     */
    private fun checkDuplicateOrders(
        signal: Signal,
        existingOrders: List<Order>,
        errors: MutableList<String>,
        warnings: MutableList<String>,
    ) {
        // Check for orders with same signal ID
        val sameSignalOrders =
            existingOrders.filter {
                it.signalId == signal.signalId && it.isActive()
            }
        if (sameSignalOrders.isNotEmpty()) {
            errors.add(
                "Order already exists for signal ${signal.signalId}",
            )
        }

        // Check for recent orders on same symbol and side
        val recentSameSymbolOrders =
            existingOrders.filter { order ->
                order.symbol == signal.symbol &&
                    order.side.name == signal.direction.toOrderSideName() &&
                    order.isActive()
            }
        if (recentSameSymbolOrders.isNotEmpty()) {
            warnings.add(
                "Active ${signal.direction.toOrderSideName()} order(s) already exist for ${signal.symbol}",
            )
        }
    }

    /**
     * Validates asset against whitelist/blacklist.
     */
    private fun validateAssetFilters(
        signal: Signal,
        config: ExecutionConfig,
        errors: MutableList<String>,
    ) {
        // Check blacklist first
        if (config.assetBlacklist.contains(signal.symbol)) {
            errors.add("${signal.symbol} is blacklisted")
        }

        // Check whitelist if configured
        if (config.assetWhitelist.isNotEmpty() &&
            !config.assetWhitelist.contains(signal.symbol)
        ) {
            errors.add(
                "${signal.symbol} not in whitelist " +
                    "(allowed: ${config.assetWhitelist.joinToString(", ")})",
            )
        }
    }

    /**
     * Validates position size is reasonable.
     */
    private fun validatePositionSize(
        positionSize: Double,
        currentPrice: Double,
        accountBalance: Double,
        errors: MutableList<String>,
        warnings: MutableList<String>,
    ) {
        if (positionSize <= 0) {
            errors.add("Position size must be positive")
        }

        // Check minimum position size (avoid dust trades)
        val positionValue = positionSize * currentPrice
        val minPositionValue = accountBalance * 0.001 // 0.1% of account
        if (positionValue < minPositionValue) {
            warnings.add(
                "Position size very small: $${positionValue.toInt()} " +
                    "(${(positionValue / accountBalance * 100).format(2)}% of account)",
            )
        }

        // Check for fractional shares if needed (platform-specific)
        // This is a placeholder - actual validation depends on broker/exchange
        if (positionSize != positionSize.toInt().toDouble()) {
            warnings.add("Fractional position size: ${positionSize.format(4)} units")
        }
    }

    /**
     * Validates stop-loss price makes sense.
     */
    private fun validateStopLoss(
        signal: Signal,
        currentPrice: Double,
        stopLossPrice: Double,
        errors: MutableList<String>,
        warnings: MutableList<String>,
    ) {
        if (stopLossPrice <= 0) {
            errors.add("Stop-loss price must be positive")
            return
        }

        // Validate stop-loss is in correct direction
        val stopDistance = currentPrice - stopLossPrice
        val isValid =
            when (signal.direction) {
                Direction.LONG -> stopDistance > 0 // SL below entry
                Direction.SHORT -> stopDistance < 0 // SL above entry
            }

        if (!isValid) {
            errors.add(
                "Stop-loss on wrong side: ${signal.direction.name} at $${currentPrice.format(2)} " +
                    "with SL at $${stopLossPrice.format(2)}",
            )
        }

        // Calculate stop-loss percentage
        val stopPercent = abs(stopDistance) / currentPrice

        // Warn if stop is very tight
        if (stopPercent < 0.005) {
            warnings.add(
                "Very tight stop-loss: ${(stopPercent * 100).format(2)}% " +
                    "(may be stopped out by noise)",
            )
        }

        // Warn if stop is very wide
        if (stopPercent > 0.10) {
            warnings.add(
                "Wide stop-loss: ${(stopPercent * 100).format(2)}% " +
                    "(high risk per trade)",
            )
        }

        // Error if stop is excessively wide
        if (stopPercent > 0.25) {
            errors.add(
                "Stop-loss too wide: ${(stopPercent * 100).format(2)}% " +
                    "(maximum 25%)",
            )
        }
    }

    /**
     * Quick validation for dry-run mode (less strict).
     */
    fun validateForDryRun(
        signal: Signal,
        config: ExecutionConfig,
    ): ValidationResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        // Basic signal validation
        if (signal.symbol.isBlank()) {
            errors.add("Signal symbol is blank")
        }

        if (signal.confidence < config.minSignalConfidence) {
            warnings.add(
                "Signal confidence (${(signal.confidence * 100).toInt()}%) " +
                    "below minimum (${(config.minSignalConfidence * 100).toInt()}%)",
            )
        }

        // Asset filters
        if (config.assetBlacklist.contains(signal.symbol)) {
            errors.add("${signal.symbol} is blacklisted")
        }

        if (config.assetWhitelist.isNotEmpty() &&
            !config.assetWhitelist.contains(signal.symbol)
        ) {
            errors.add("${signal.symbol} not in whitelist")
        }

        return if (errors.isEmpty()) {
            ValidationResult.Valid(warnings)
        } else {
            ValidationResult.Invalid(errors, warnings)
        }
    }
}

/**
 * Result of validation check.
 */
sealed class ValidationResult {
    /**
     * Validation passed.
     */
    data class Valid(
        val warnings: List<String> = emptyList(),
    ) : ValidationResult() {
        val hasWarnings: Boolean get() = warnings.isNotEmpty()
    }

    /**
     * Validation failed.
     */
    data class Invalid(
        val errors: List<String>,
        val warnings: List<String> = emptyList(),
    ) : ValidationResult() {
        val errorMessage: String get() = errors.joinToString("; ")
    }

    /**
     * Check if validation passed.
     */
    fun isValid(): Boolean = this is Valid

    /**
     * Check if validation failed.
     */
    fun isInvalid(): Boolean = this is Invalid
}
