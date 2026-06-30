package xyz.fkstrading.shared.di

import org.koin.dsl.module
import xyz.fkstrading.shared.config.AppConfig
import xyz.fkstrading.shared.data.api.*
import xyz.fkstrading.shared.data.bridge.WebSocketRepositoryBridge
import xyz.fkstrading.shared.data.db.DatabaseWrapper
import xyz.fkstrading.shared.data.mock.MockOrderDataSource
import xyz.fkstrading.shared.data.mock.MockPositionDataSource
import xyz.fkstrading.shared.data.repository.*
import xyz.fkstrading.shared.data.sync.SyncConfig
import xyz.fkstrading.shared.data.sync.SyncEngine

/**
 * Database and persistence module
 *
 * Provides:
 * - DatabaseWrapper for SQLDelight access
 * - Repository implementations with offline-first support
 * - SyncEngine for coordinating data synchronization
 */
val databaseModule =
    module {

        // Application Configuration
        single {
            // Development config now points at janus (brain :8080 / forward :8081).
            // useMockData stays TRUE until the live janus read path is smoke-tested — flip to
            // false to activate the real OrderApiDataSource/PositionApiDataSource against janus.
            AppConfig.development(useMockData = true)
        }

        // Database wrapper (expects DatabaseDriverFactory to be provided by platform module)
        single {
            DatabaseWrapper(get())
        }

        // API Client (janus: brain = apiBaseUrl, forward REST = forwardBaseUrl)
        single {
            val config: AppConfig = get()
            FksApiClient(
                baseUrl = config.apiBaseUrl,
                forwardBaseUrl = config.forwardBaseUrl,
                httpClient = FksApiClient.createDefaultHttpClient(),
                authToken = config.authToken,
            )
        }

        // Remote Data Sources (Mock or Real based on config)
        single<SignalRemoteDataSource> {
            val config: AppConfig = get()
            if (config.shouldUseMockData()) {
                // Return mock for signals when needed
                SignalApiDataSource(get())
            } else {
                SignalApiDataSource(get())
            }
        }

        single<OrderRemoteDataSource> {
            val config: AppConfig = get()
            if (config.shouldUseMockData()) {
                MockOrderDataSource()
            } else {
                OrderApiDataSource(get())
            }
        }

        single<PositionRemoteDataSource> {
            val config: AppConfig = get()
            if (config.shouldUseMockData()) {
                MockPositionDataSource()
            } else {
                PositionApiDataSource(get())
            }
        }

        // Signal Repository
        single<SignalRepository> {
            SignalRepositoryImpl(
                database = get(),
                remoteDataSource = get(),
            )
        }

        // Order Repository
        single<OrderRepository> {
            OrderRepositoryImpl(
                database = get(),
                remoteDataSource = get(),
            )
        }

        // Position Repository
        single<PositionRepository> {
            PositionRepositoryImpl(
                database = get(),
                remoteDataSource = get(),
            )
        }

        // WebSocket Repository Bridge
        single {
            WebSocketRepositoryBridge(
                dataStream = get(),
                signalRepository = get(),
                orderRepository = get(),
                positionRepository = get(),
            )
        }

        // Sync Engine Configuration
        single {
            SyncConfig(
                syncIntervalMillis = 60_000L, // 1 minute
                enablePeriodicSync = true,
                enableBackgroundSync = true,
                retryAttempts = 3,
                retryDelayMillis = 5_000L,
            )
        }

        // Sync Engine
        single {
            SyncEngine(
                signalRepository = get(),
                orderRepository = get(),
                positionRepository = get(),
                syncIntervalMillis = get<SyncConfig>().syncIntervalMillis,
            )
        }
    }
