package xyz.fkstrading.shared.di

import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.dsl.KoinAppDeclaration
import org.koin.dsl.module
import xyz.fkstrading.shared.data.storage.SettingsStorage
import xyz.fkstrading.shared.domain.usecases.*

/**
 * Main application module that combines all Koin modules
 *
 * This is the central configuration for dependency injection throughout
 * the application. Add new modules here as they are created.
 */
val appModule =
    module {
        // Settings storage (cross-platform persistence)
        single { SettingsStorage() }
    }

/**
 * Use case module containing business logic use cases
 *
 * Each use case encapsulates a single piece of business logic and
 * depends on repositories or API clients provided by other modules.
 */
val useCaseModule =
    module {
        // Signal use cases
        factory { GetRecentSignalsUseCase(get()) }
        factory { GetSignalsBySymbolUseCase(get()) }
        factory { GetSignalByIdUseCase(get()) }

        // Order use cases
        factory { GetRecentOrdersUseCase(get()) }
        factory { GetActiveOrdersUseCase(get()) }
        factory { GetOrdersBySymbolUseCase(get()) }
        factory { CreateOrderUseCase(get(), get()) }
        factory { CancelOrderUseCase(get(), get()) }

        // Position use cases
        factory { GetPositionsUseCase(get()) }
        factory { GetOpenPositionsUseCase(get()) }
        factory { GetClosedPositionsUseCase(get()) }
        factory { GetPositionsBySymbolUseCase(get()) }
        factory { ClosePositionUseCase(get(), get()) }

        // System use cases
        factory { GetSystemHealthUseCase(get()) }
        factory { GetSystemStatusUseCase(get()) }

        // Sync use cases
        factory { SyncAllDataUseCase(get(), get(), get()) }
        factory { RefreshAllDataUseCase(get(), get(), get()) }
    }

/**
 * All application modules combined
 */
val allModules: List<Module> =
    listOf(
        networkModule,
        webSocketModule,
        databaseModule,
        appModule,
        useCaseModule,
        authModule,
    )

/**
 * Initializes Koin dependency injection
 *
 * Call this function at application startup before accessing any dependencies.
 *
 * @param appDeclaration Optional configuration for Koin
 */
fun initKoin(appDeclaration: KoinAppDeclaration = {}) {
    startKoin {
        appDeclaration()
        modules(allModules)
    }
}

/**
 * Initializes Koin with custom modules
 *
 * Useful for testing or platform-specific configurations
 *
 * @param modules Custom list of modules to use
 * @param appDeclaration Optional configuration for Koin
 */
fun initKoin(
    modules: List<Module>,
    appDeclaration: KoinAppDeclaration = {},
) {
    startKoin {
        appDeclaration()
        modules(modules)
    }
}
