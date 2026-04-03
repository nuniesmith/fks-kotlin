package xyz.fkstrading.shared.data.db

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOne
import app.cash.sqldelight.coroutines.mapToOneOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Instant
import xyz.fkstrading.shared.data.mappers.toDomain
import xyz.fkstrading.shared.data.mappers.toEntityParams
import xyz.fkstrading.shared.domain.models.Direction
import xyz.fkstrading.shared.domain.models.Order
import xyz.fkstrading.shared.domain.models.OrderSide
import xyz.fkstrading.shared.domain.models.OrderStatus
import xyz.fkstrading.shared.domain.models.OrderType
import xyz.fkstrading.shared.domain.models.Position
import xyz.fkstrading.shared.domain.models.PositionStatus
import xyz.fkstrading.shared.domain.models.Signal
import xyz.fkstrading.shared.domain.models.SignalType
import xyz.fkstrading.shared.domain.models.StrategyConfig
import xyz.fkstrading.shared.domain.models.StrategyType
import xyz.fkstrading.shared.domain.models.TimeInForce
import xyz.fkstrading.shared.domain.models.Timeframe

/**
 * Wrapper around FksDatabase that provides higher-level API
 * and handles mapping between domain models and database entities
 */
class DatabaseWrapper(driverFactory: DatabaseDriverFactory) {
    private val driver = driverFactory.createDriver()
    private val database = FksDatabase(driver)

    // Queries
    private val signalQueries = database.signalQueries
    private val orderQueries = database.orderQueries
    private val positionQueries = database.positionQueries
    private val syncMetadataQueries = database.syncMetadataQueries
    private val strategyConfigQueries = database.strategyConfigQueries

    // ========================================
    // SIGNAL OPERATIONS
    // ========================================

    suspend fun insertOrReplaceSignal(
        signal: Signal,
        isSynced: Boolean = false,
    ) {
        val now = currentTimeMillis()
        signalQueries.insertOrReplace(
            signal_id = signal.signalId,
            signal_type = signal.signalType.name,
            symbol = signal.symbol,
            timeframe = signal.timeframe?.name ?: "H1",
            direction = signal.direction.name,
            entry_price = signal.entryPrice,
            stop_loss = signal.stopLoss,
            take_profit = signal.takeProfit,
            confidence = signal.confidence,
            timestamp = signal.timestamp.toEpochMilliseconds(),
            strategy_name = signal.strategyName,
            strategy_type = signal.strategyType?.name,
            risk_reward_ratio = signal.riskRewardRatio,
            expires_at = signal.expiresAt?.toEpochMilliseconds(),
            is_synced = if (isSynced) 1 else 0,
            created_at = now,
            updated_at = now,
        )
    }

    fun getSignalById(signalId: String): Flow<Signal?> {
        return signalQueries.getById(signalId)
            .asFlow()
            .mapToOneOrNull(Dispatchers.IO)
            .mapToSignal()
    }

    fun getAllSignals(): Flow<List<Signal>> {
        return signalQueries.getAll()
            .asFlow()
            .mapToList(Dispatchers.IO)
            .mapToSignalList()
    }

    fun getSignalsBySymbol(symbol: String): Flow<List<Signal>> {
        return signalQueries.getBySymbol(symbol)
            .asFlow()
            .mapToList(Dispatchers.IO)
            .mapToSignalList()
    }

    fun getRecentSignals(limit: Long): Flow<List<Signal>> {
        return signalQueries.getRecent(limit)
            .asFlow()
            .mapToList(Dispatchers.IO)
            .mapToSignalList()
    }

    fun getUnsyncedSignals(): Flow<List<Signal>> {
        return signalQueries.getUnsynced()
            .asFlow()
            .mapToList(Dispatchers.IO)
            .mapToSignalList()
    }

    suspend fun markSignalAsSynced(signalId: String) {
        signalQueries.markAsSynced(currentTimeMillis(), signalId)
    }

    suspend fun deleteSignal(signalId: String) {
        signalQueries.deleteById(signalId)
    }

    suspend fun deleteOldSignals(olderThanMillis: Long) {
        signalQueries.deleteOlderThan(olderThanMillis)
    }

    // ========================================
    // ORDER OPERATIONS
    // ========================================

    suspend fun insertOrReplaceOrder(
        order: Order,
        isSynced: Boolean = false,
    ) {
        val now = currentTimeMillis()
        orderQueries.insertOrReplace(
            order_id = order.orderId,
            symbol = order.symbol,
            side = order.side.name,
            order_type = order.orderType.name,
            quantity = order.quantity,
            price = order.price,
            stop_price = order.stopPrice,
            status = order.status.name,
            time_in_force = order.timeInForce.name,
            filled_quantity = order.filledQuantity,
            average_fill_price = order.averageFillPrice,
            timestamp = order.timestamp.toEpochMilliseconds(),
            submitted_at = order.submittedAt?.toEpochMilliseconds(),
            updated_at = order.updatedAt?.toEpochMilliseconds(),
            completed_at = order.completedAt?.toEpochMilliseconds(),
            signal_id = order.signalId,
            strategy_id = order.strategyId,
            fees = order.fees ?: 0.0,
            commission = order.commission ?: 0.0,
            error_message = order.errorMessage,
            is_synced = if (isSynced) 1 else 0,
            created_at = now,
            local_updated_at = now,
        )
    }

    fun getOrderById(orderId: String): Flow<Order?> {
        return orderQueries.getById(orderId)
            .asFlow()
            .mapToOneOrNull(Dispatchers.IO)
            .mapToOrder()
    }

    fun getAllOrders(): Flow<List<Order>> {
        return orderQueries.getAll()
            .asFlow()
            .mapToList(Dispatchers.IO)
            .mapToOrderList()
    }

    fun getActiveOrders(): Flow<List<Order>> {
        return orderQueries.getActive()
            .asFlow()
            .mapToList(Dispatchers.IO)
            .mapToOrderList()
    }

    fun getOrdersBySymbol(symbol: String): Flow<List<Order>> {
        return orderQueries.getBySymbol(symbol)
            .asFlow()
            .mapToList(Dispatchers.IO)
            .mapToOrderList()
    }

    fun getOrdersBySignalId(signalId: String): Flow<List<Order>> {
        return orderQueries.getBySignalId(signalId)
            .asFlow()
            .mapToList(Dispatchers.IO)
            .mapToOrderList()
    }

    fun getUnsyncedOrders(): Flow<List<Order>> {
        return orderQueries.getUnsynced()
            .asFlow()
            .mapToList(Dispatchers.IO)
            .mapToOrderList()
    }

    suspend fun markOrderAsSynced(orderId: String) {
        orderQueries.markAsSynced(currentTimeMillis(), orderId)
    }

    suspend fun updateOrderStatus(
        orderId: String,
        status: OrderStatus,
    ) {
        val now = currentTimeMillis()
        orderQueries.updateStatus(status.name, now, now, orderId)
    }

    suspend fun deleteOrder(orderId: String) {
        orderQueries.deleteById(orderId)
    }

    // ========================================
    // POSITION OPERATIONS
    // ========================================

    suspend fun insertOrReplacePosition(
        position: Position,
        isSynced: Boolean = false,
    ) {
        val now = currentTimeMillis()
        positionQueries.insertOrReplace(
            position_id = position.positionId,
            symbol = position.symbol,
            side = position.side.name,
            quantity = position.quantity,
            entry_price = position.entryPrice,
            current_price = position.currentPrice,
            stop_loss = position.stopLoss,
            take_profit = position.takeProfit,
            status = position.status.name,
            opened_at = position.openedAt.toEpochMilliseconds(),
            closed_at = position.closedAt?.toEpochMilliseconds(),
            updated_at = position.updatedAt?.toEpochMilliseconds(),
            unrealized_pnl = position.unrealizedPnL,
            realized_pnl = position.realizedPnL,
            fees = position.fees ?: 0.0,
            commission = position.commission ?: 0.0,
            order_id = position.orderId,
            signal_id = position.signalId,
            strategy_id = position.strategyId,
            is_synced = if (isSynced) 1 else 0,
            created_at = now,
            local_updated_at = now,
        )
    }

    fun getPositionById(positionId: String): Flow<Position?> {
        return positionQueries.getById(positionId)
            .asFlow()
            .mapToOneOrNull(Dispatchers.IO)
            .mapToPosition()
    }

    fun getAllPositions(): Flow<List<Position>> {
        return positionQueries.getAll()
            .asFlow()
            .mapToList(Dispatchers.IO)
            .mapToPositionList()
    }

    fun getOpenPositions(): Flow<List<Position>> {
        return positionQueries.getOpen()
            .asFlow()
            .mapToList(Dispatchers.IO)
            .mapToPositionList()
    }

    fun getClosedPositions(): Flow<List<Position>> {
        return positionQueries.getClosed()
            .asFlow()
            .mapToList(Dispatchers.IO)
            .mapToPositionList()
    }

    fun getPositionsBySymbol(symbol: String): Flow<List<Position>> {
        return positionQueries.getBySymbol(symbol)
            .asFlow()
            .mapToList(Dispatchers.IO)
            .mapToPositionList()
    }

    fun getUnsyncedPositions(): Flow<List<Position>> {
        return positionQueries.getUnsynced()
            .asFlow()
            .mapToList(Dispatchers.IO)
            .mapToPositionList()
    }

    suspend fun markPositionAsSynced(positionId: String) {
        positionQueries.markAsSynced(currentTimeMillis(), positionId)
    }

    suspend fun updatePositionPriceAndPnL(
        positionId: String,
        currentPrice: Double,
        unrealizedPnL: Double,
    ) {
        val now = currentTimeMillis()
        positionQueries.updatePriceAndPnL(currentPrice, unrealizedPnL, now, now, positionId)
    }

    suspend fun closePosition(
        positionId: String,
        realizedPnL: Double,
    ) {
        val now = currentTimeMillis()
        positionQueries.closePosition(now, realizedPnL, now, now, positionId)
    }

    suspend fun deletePosition(positionId: String) {
        positionQueries.deleteById(positionId)
    }

    // ========================================
    // SYNC METADATA OPERATIONS
    // ========================================

    suspend fun updateSyncMetadata(
        entityType: String,
        entityId: String,
        hash: String? = null,
    ) {
        val now = currentTimeMillis()
        val existing = syncMetadataQueries.getByEntity(entityType, entityId).executeAsOneOrNull()

        if (existing != null) {
            if (hash != null) {
                syncMetadataQueries.updateSyncTimestampAndHash(now, hash, entityType, entityId)
            } else {
                syncMetadataQueries.updateSyncTimestamp(now, entityType, entityId)
            }
        } else {
            syncMetadataQueries.insertOrReplace(
                entity_type = entityType,
                entity_id = entityId,
                last_synced_at = now,
                version = 1,
                hash = hash,
                conflict_resolution = null,
            )
        }
    }

    // ========================================
    // CLEANUP OPERATIONS
    // ========================================

    suspend fun clearAllData() {
        signalQueries.deleteAll()
        orderQueries.deleteAll()
        positionQueries.deleteAll()
        syncMetadataQueries.deleteAll()
    }

    fun close() {
        driver.close()
    }

    // ========================================
    // PRIVATE HELPERS
    // ========================================

    private fun currentTimeMillis(): Long = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()

    private fun Flow<SignalEntity?>.mapToSignal(): Flow<Signal?> {
        return map { entity ->
            entity?.let { mapSignalEntityToDomain(it) }
        }
    }

    private fun Flow<List<SignalEntity>>.mapToSignalList(): Flow<List<Signal>> {
        return map { entities ->
            entities.map { mapSignalEntityToDomain(it) }
        }
    }

    private fun Flow<OrderEntity?>.mapToOrder(): Flow<Order?> {
        return map { entity ->
            entity?.let { mapOrderEntityToDomain(it) }
        }
    }

    private fun Flow<List<OrderEntity>>.mapToOrderList(): Flow<List<Order>> {
        return map { entities ->
            entities.map { mapOrderEntityToDomain(it) }
        }
    }

    private fun Flow<PositionEntity?>.mapToPosition(): Flow<Position?> {
        return map { entity ->
            entity?.let { mapPositionEntityToDomain(it) }
        }
    }

    private fun Flow<List<PositionEntity>>.mapToPositionList(): Flow<List<Position>> {
        return map { entities ->
            entities.map { mapPositionEntityToDomain(it) }
        }
    }

    private fun mapSignalEntityToDomain(entity: SignalEntity): Signal {
        return Signal(
            signalId = entity.signal_id,
            signalType = SignalType.valueOf(entity.signal_type),
            symbol = entity.symbol,
            timeframe = entity.timeframe?.let { Timeframe.valueOf(it) },
            direction = Direction.valueOf(entity.direction),
            strength = entity.confidence, // Use confidence as strength fallback
            confidence = entity.confidence,
            price = entity.entry_price,
            entryPrice = entity.entry_price,
            stopLoss = entity.stop_loss,
            takeProfit = entity.take_profit,
            timestamp = Instant.fromEpochMilliseconds(entity.timestamp),
            strategyName = entity.strategy_name,
            strategyType = entity.strategy_type?.let { StrategyType.valueOf(it) },
            riskRewardRatio = entity.risk_reward_ratio,
            expiresAt = entity.expires_at?.let { Instant.fromEpochMilliseconds(it) },
            metadata = emptyMap(),
        )
    }

    private fun mapOrderEntityToDomain(entity: OrderEntity): Order {
        return Order(
            orderId = entity.order_id,
            symbol = entity.symbol,
            side = OrderSide.valueOf(entity.side),
            orderType = OrderType.valueOf(entity.order_type),
            quantity = entity.quantity,
            price = entity.price,
            stopPrice = entity.stop_price,
            status = OrderStatus.valueOf(entity.status),
            timeInForce = TimeInForce.valueOf(entity.time_in_force),
            filledQuantity = entity.filled_quantity,
            averageFillPrice = entity.average_fill_price,
            timestamp = Instant.fromEpochMilliseconds(entity.timestamp),
            submittedAt = entity.submitted_at?.let { Instant.fromEpochMilliseconds(it) },
            updatedAt = entity.updated_at?.let { Instant.fromEpochMilliseconds(it) },
            completedAt = entity.completed_at?.let { Instant.fromEpochMilliseconds(it) },
            signalId = entity.signal_id,
            strategyId = entity.strategy_id,
            fees = entity.fees,
            commission = entity.commission,
            errorMessage = entity.error_message,
            metadata = emptyMap<String, String>(), // Not storing metadata map in DB for simplicity
        )
    }

    private fun mapPositionEntityToDomain(entity: PositionEntity): Position {
        return Position(
            positionId = entity.position_id,
            symbol = entity.symbol,
            side = OrderSide.valueOf(entity.side),
            quantity = entity.quantity,
            entryPrice = entity.entry_price,
            currentPrice = entity.current_price,
            stopLoss = entity.stop_loss,
            takeProfit = entity.take_profit,
            status = PositionStatus.valueOf(entity.status),
            openedAt = Instant.fromEpochMilliseconds(entity.opened_at),
            closedAt = entity.closed_at?.let { Instant.fromEpochMilliseconds(it) },
            updatedAt = entity.updated_at?.let { Instant.fromEpochMilliseconds(it) },
            unrealizedPnL = entity.unrealized_pnl,
            realizedPnL = entity.realized_pnl,
            fees = entity.fees,
            commission = entity.commission,
            orderId = entity.order_id,
            signalId = entity.signal_id,
            strategyId = entity.strategy_id,
            metadata = emptyMap<String, String>(), // Not storing metadata map in DB for simplicity
        )
    }

    // ========================================
    // STRATEGY CONFIG OPERATIONS
    // ========================================

    suspend fun insertOrReplaceStrategyConfig(
        config: StrategyConfig,
        isSynced: Boolean = false,
    ) {
        val params = config.toEntityParams()
        strategyConfigQueries.insertOrReplace(
            config_id = params["config_id"] as String,
            name = params["name"] as String,
            description = params["description"] as String,
            mode = params["mode"] as String,
            position_sizing_method = params["position_sizing_method"] as String,
            risk_per_trade = params["risk_per_trade"] as Double,
            fixed_position_size = params["fixed_position_size"] as Double,
            account_percentage = params["account_percentage"] as Double,
            kelly_fraction = params["kelly_fraction"] as Double,
            max_positions = params["max_positions"] as Long,
            max_positions_per_asset = params["max_positions_per_asset"] as Long,
            min_signal_confidence = params["min_signal_confidence"] as Double,
            stop_loss_method = params["stop_loss_method"] as String,
            stop_loss_percentage = params["stop_loss_percentage"] as Double,
            stop_loss_atr_multiplier = params["stop_loss_atr_multiplier"] as Double,
            take_profit_method = params["take_profit_method"] as String,
            take_profit_percentage = params["take_profit_percentage"] as Double,
            take_profit_atr_multiplier = params["take_profit_atr_multiplier"] as Double,
            risk_reward_ratio = params["risk_reward_ratio"] as Double,
            default_order_type = params["default_order_type"] as String,
            limit_order_offset = params["limit_order_offset"] as Double,
            time_in_force = params["time_in_force"] as String,
            dry_run_mode = params["dry_run_mode"] as Long,
            require_confirmation = params["require_confirmation"] as Long,
            close_positions_eod = params["close_positions_eod"] as Long,
            max_slippage_percent = params["max_slippage_percent"] as Double,
            asset_whitelist = params["asset_whitelist"] as String,
            asset_blacklist = params["asset_blacklist"] as String,
            is_active = params["is_active"] as Long,
            is_default = params["is_default"] as Long,
            is_synced = if (isSynced) 1 else 0,
            created_at = params["created_at"] as Long,
            updated_at = params["updated_at"] as Long,
        )
    }

    fun getStrategyConfigById(configId: String): Flow<StrategyConfig?> {
        return strategyConfigQueries.getById(configId)
            .asFlow()
            .mapToOneOrNull(Dispatchers.IO)
            .mapToStrategyConfig()
    }

    fun getAllStrategyConfigs(): Flow<List<StrategyConfig>> {
        return strategyConfigQueries.getAll()
            .asFlow()
            .mapToList(Dispatchers.IO)
            .mapToStrategyConfigList()
    }

    fun getActiveStrategyConfigs(): Flow<List<StrategyConfig>> {
        return strategyConfigQueries.getActive()
            .asFlow()
            .mapToList(Dispatchers.IO)
            .mapToStrategyConfigList()
    }

    fun getDefaultStrategyConfig(): Flow<StrategyConfig?> {
        return strategyConfigQueries.getDefault()
            .asFlow()
            .mapToOneOrNull(Dispatchers.IO)
            .mapToStrategyConfig()
    }

    fun getStrategyConfigByName(name: String): Flow<StrategyConfig?> {
        return strategyConfigQueries.getByName(name)
            .asFlow()
            .mapToOneOrNull(Dispatchers.IO)
            .mapToStrategyConfig()
    }

    fun getUnsyncedStrategyConfigs(): Flow<List<StrategyConfig>> {
        return strategyConfigQueries.getUnsynced()
            .asFlow()
            .mapToList(Dispatchers.IO)
            .mapToStrategyConfigList()
    }

    suspend fun clearStrategyConfigDefaultFlags(timestamp: Long) {
        strategyConfigQueries.clearDefaultFlags(timestamp)
    }

    suspend fun setStrategyConfigAsDefault(
        configId: String,
        timestamp: Long,
    ) {
        strategyConfigQueries.setAsDefault(timestamp, configId)
    }

    suspend fun markStrategyConfigAsSynced(configId: String) {
        strategyConfigQueries.markAsSynced(currentTimeMillis(), configId)
    }

    suspend fun updateStrategyConfigActiveStatus(
        configId: String,
        isActive: Boolean,
        timestamp: Long,
    ) {
        strategyConfigQueries.updateActiveStatus(if (isActive) 1 else 0, timestamp, configId)
    }

    suspend fun deleteStrategyConfigById(configId: String) {
        strategyConfigQueries.deleteById(configId)
    }

    suspend fun deleteAllStrategyConfigs() {
        strategyConfigQueries.deleteAll()
    }

    fun countAllStrategyConfigs(): Flow<Long> {
        return strategyConfigQueries.countAll()
            .asFlow()
            .mapToOne(Dispatchers.IO)
    }

    fun countActiveStrategyConfigs(): Flow<Long> {
        return strategyConfigQueries.countActive()
            .asFlow()
            .mapToOne(Dispatchers.IO)
    }

    fun countUnsyncedStrategyConfigs(): Flow<Long> {
        return strategyConfigQueries.countUnsynced()
            .asFlow()
            .mapToOne(Dispatchers.IO)
    }

    fun searchStrategyConfigsByName(pattern: String): Flow<List<StrategyConfig>> {
        return strategyConfigQueries.searchByName(pattern)
            .asFlow()
            .mapToList(Dispatchers.IO)
            .mapToStrategyConfigList()
    }

    // Mapping functions for StrategyConfig
    private fun Flow<StrategyConfigEntity?>.mapToStrategyConfig(): Flow<StrategyConfig?> {
        return map { entity ->
            entity?.toDomain()
        }
    }

    private fun Flow<List<StrategyConfigEntity>>.mapToStrategyConfigList(): Flow<List<StrategyConfig>> {
        return map { entities ->
            entities.map { it.toDomain() }
        }
    }
}
