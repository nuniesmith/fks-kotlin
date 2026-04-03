package xyz.fkstrading.client.di

import org.koin.dsl.module
import xyz.fkstrading.client.features.auth.AuthViewModel
import xyz.fkstrading.client.features.orders.OrdersViewModel
import xyz.fkstrading.client.features.portfolio.PortfolioViewModel
import xyz.fkstrading.client.features.realtime.RealTimeSignalsViewModel
import xyz.fkstrading.client.features.settings.StrategyConfigViewModel
import xyz.fkstrading.client.features.settings.SystemSettingsViewModel

/**
 * App-level Koin DI module
 *
 * Registers ViewModels and app-specific dependencies.
 * This module is combined with shared modules (API, WebSocket, etc.)
 * during app initialization.
 */
val appModule =
    module {
        // Auth ViewModel — factory so each call-site gets its own coroutine scope.
        // The underlying TailscaleAuthRepository is a singleton and is shared.
        factory { AuthViewModel(get()) }

        // ViewModels
        single { RealTimeSignalsViewModel(get(), get(), get(), get(), get(), get()) }
        single { StrategyConfigViewModel(get()) }
        single { SystemSettingsViewModel() }
        single { PortfolioViewModel(get()) }
        single { OrdersViewModel(get()) }
    }
