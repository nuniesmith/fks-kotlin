package xyz.fkstrading.shared.di

import io.ktor.client.*
import org.koin.dsl.module
import xyz.fkstrading.shared.data.websocket.*

/**
 * Koin module for WebSocket-related dependencies.
 *
 * This module provides:
 * - WebSocketClient: Core WebSocket client with auto-reconnect
 * - WebSocketDataStream: Real-time data stream processor
 * - SubscriptionManager: Channel subscription management
 *
 * All components are singletons to maintain connection state
 * and share data streams across the application.
 */
val webSocketModule =
    module {
        /**
         * WebSocket client singleton.
         *
         * Configured with default reconnection settings:
         * - Initial delay: 1 second
         * - Max delay: 60 seconds
         * - Exponential multiplier: 2.0
         * - Infinite retry attempts
         */
        single<WebSocketClient> {
            WebSocketClientImpl(
                httpClient = get<HttpClient>(),
                reconnectConfig =
                    ReconnectConfig(
                        initialDelayMs = 1000,
                        maxDelayMs = 60000,
                        multiplier = 2.0,
                        maxAttempts = null, // Infinite retries
                    ),
            )
        }

        /**
         * WebSocket data stream singleton.
         *
         * Processes incoming WebSocket messages and distributes them
         * to type-specific flows (signals, orders, positions, market data).
         */
        single {
            WebSocketDataStream(
                webSocketClient = get(),
            )
        }

        /**
         * Subscription manager singleton.
         *
         * Manages channel subscriptions and unsubscriptions.
         */
        single {
            SubscriptionManager(
                webSocketClient = get(),
            )
        }
    }
