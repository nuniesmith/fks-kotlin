package xyz.fkstrading.clients.di

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.koin.dsl.module
import xyz.fkstrading.clients.domain.viewmodel.AuthViewModel

/**
 * Koin Dependency Injection Module for FKS Trading Desktop App
 *
 * This module provides:
 * - ViewModels (single instance for app lifecycle to maintain state consistency)
 * - Repositories (already singletons via object)
 * - Other dependencies as needed
 *
 * NOTE: AuthViewModel is a KMP class (not Android ViewModel), so we use
 * single scope to ensure one instance across the app for state consistency.
 */
val appModule =
    module {
        // Provide CoroutineScope for ViewModels
        // Using Main dispatcher for JVM/Desktop UI context
        factory<CoroutineScope> { CoroutineScope(Dispatchers.Main) }

        // ViewModels - using single scope ensures one instance across the app
        // This fixes the state desync bug where AuthViewModel was instantiated multiple times
        single<AuthViewModel> {
            AuthViewModel(get()) // Injects CoroutineScope from factory above
        }
    }
