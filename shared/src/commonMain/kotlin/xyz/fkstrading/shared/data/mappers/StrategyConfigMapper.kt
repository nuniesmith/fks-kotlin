package xyz.fkstrading.shared.data.mappers

import kotlinx.datetime.Instant
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import xyz.fkstrading.shared.data.db.StrategyConfigEntity
import xyz.fkstrading.shared.domain.models.StrategyConfig
import xyz.fkstrading.shared.domain.models.TimeInForce
import xyz.fkstrading.shared.domain.strategy.models.ExecutionConfig
import xyz.fkstrading.shared.domain.strategy.models.ExecutionMode
import xyz.fkstrading.shared.domain.strategy.models.PositionSizingMethod
import xyz.fkstrading.shared.domain.strategy.models.StopLossMethod
import xyz.fkstrading.shared.domain.strategy.models.TakeProfitMethod
import xyz.fkstrading.shared.domain.strategy.models.OrderType as StrategyOrderType

/**
 * Mapper functions for converting between StrategyConfig domain model
 * and StrategyConfigEntity database entity.
 */

private val json = Json { ignoreUnknownKeys = true }

/**
 * Maps a StrategyConfigEntity to a StrategyConfig domain model.
 */
fun StrategyConfigEntity.toDomain(): StrategyConfig {
    val executionConfig =
        ExecutionConfig(
            mode = ExecutionMode.valueOf(mode),
            positionSizingMethod = PositionSizingMethod.valueOf(position_sizing_method),
            riskPerTrade = risk_per_trade,
            fixedPositionSize = fixed_position_size,
            accountPercentage = account_percentage,
            kellyFraction = kelly_fraction,
            maxPositions = max_positions.toInt(),
            maxPositionsPerAsset = max_positions_per_asset.toInt(),
            minSignalConfidence = min_signal_confidence,
            stopLossMethod = StopLossMethod.valueOf(stop_loss_method),
            stopLossPercentage = stop_loss_percentage,
            stopLossAtrMultiple = stop_loss_atr_multiplier,
            takeProfitMethod = TakeProfitMethod.valueOf(take_profit_method),
            takeProfitPercentage = take_profit_percentage,
            takeProfitAtrMultiple = take_profit_atr_multiplier,
            riskRewardRatio = risk_reward_ratio,
            defaultOrderType = StrategyOrderType.valueOf(default_order_type),
            limitOrderOffset = limit_order_offset,
            dryRunMode = dry_run_mode != 0L,
            requireConfirmation = require_confirmation != 0L,
            assetWhitelist = parseStringList(asset_whitelist),
            assetBlacklist = parseStringList(asset_blacklist),
            timeInForce = TimeInForce.valueOf(time_in_force),
            closePositionsEOD = close_positions_eod != 0L,
            maxSlippagePercent = max_slippage_percent,
        )

    return StrategyConfig(
        configId = config_id,
        name = name,
        description = description,
        executionConfig = executionConfig,
        isActive = is_active != 0L,
        isDefault = is_default != 0L,
        createdAt = Instant.fromEpochMilliseconds(created_at),
        updatedAt = Instant.fromEpochMilliseconds(updated_at),
    )
}

/**
 * Maps a StrategyConfig domain model to parameters for database insertion.
 * Returns a map of parameters that can be used with the insertOrReplace query.
 */
fun StrategyConfig.toEntityParams(): Map<String, Any> {
    val config = executionConfig

    return mapOf(
        "config_id" to configId,
        "name" to name,
        "description" to (description ?: ""),
        "mode" to config.mode.name,
        "position_sizing_method" to config.positionSizingMethod.name,
        "risk_per_trade" to config.riskPerTrade,
        // Provide default values for nullable fields - database schema requires NOT NULL
        "fixed_position_size" to (config.fixedPositionSize ?: config.fixedAmount ?: 0.0),
        "account_percentage" to (config.accountPercentage ?: config.fixedPercentage ?: 0.0),
        "kelly_fraction" to config.kellyFraction,
        "max_positions" to config.maxPositions.toLong(),
        "max_positions_per_asset" to config.maxPositionsPerAsset.toLong(),
        "min_signal_confidence" to config.minSignalConfidence,
        "stop_loss_method" to config.stopLossMethod.name,
        // Default stop loss percentage to 2% (0.02) if not set
        "stop_loss_percentage" to (config.stopLossPercentage ?: 0.02),
        "stop_loss_atr_multiplier" to config.stopLossAtrMultiple,
        "take_profit_method" to config.takeProfitMethod.name,
        // Default take profit percentage to 2% (0.02) if not set
        "take_profit_percentage" to (config.takeProfitPercentage ?: 0.02),
        // Default take profit ATR multiple to 3.0 if not set
        "take_profit_atr_multiplier" to (config.takeProfitAtrMultiple ?: 3.0),
        "risk_reward_ratio" to config.riskRewardRatio,
        "default_order_type" to config.defaultOrderType.name,
        "limit_order_offset" to config.limitOrderOffset,
        "time_in_force" to config.timeInForce.name,
        "dry_run_mode" to if (config.dryRunMode) 1L else 0L,
        "require_confirmation" to if (config.requireConfirmation) 1L else 0L,
        "close_positions_eod" to if (config.closePositionsEOD) 1L else 0L,
        "max_slippage_percent" to config.maxSlippagePercent,
        "asset_whitelist" to serializeStringList(config.assetWhitelist),
        "asset_blacklist" to serializeStringList(config.assetBlacklist),
        "is_active" to if (isActive) 1L else 0L,
        "is_default" to if (isDefault) 1L else 0L,
        "is_synced" to 0L, // Always mark as unsynced when inserting
        "created_at" to createdAt.toEpochMilliseconds(),
        "updated_at" to updatedAt.toEpochMilliseconds(),
    )
}

/**
 * Helper function to parse a JSON array string to a list of strings.
 */
private fun parseStringList(jsonString: String): List<String> {
    return try {
        if (jsonString.isBlank() || jsonString == "[]") {
            emptyList()
        } else {
            json.decodeFromString<List<String>>(jsonString)
        }
    } catch (e: Exception) {
        emptyList()
    }
}

/**
 * Helper function to serialize a list of strings to a JSON array string.
 */
private fun serializeStringList(list: List<String>): String {
    return if (list.isEmpty()) {
        "[]"
    } else {
        json.encodeToString(list)
    }
}
