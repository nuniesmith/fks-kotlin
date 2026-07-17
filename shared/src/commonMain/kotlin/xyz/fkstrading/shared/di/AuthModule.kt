package xyz.fkstrading.shared.di

import org.koin.dsl.module
import xyz.fkstrading.shared.auth.TailscaleAuthRepository
import xyz.fkstrading.shared.auth.TailscaleAuthRepositoryImpl

/**
 * Koin module that wires Tailscale authentication dependencies.
 *
 * Provides a single [TailscaleAuthRepository] instance for the lifetime of
 * the application.  The repository is initialised with:
 *
 * - The shared [io.ktor.client.HttpClient] (already configured with JSON
 *   content negotiation and the correct base URL via [networkModule]).
 * - The API base URL resolved from Koin properties, falling back to the
 *   standard localhost development address.
 * - The shared [xyz.fkstrading.shared.data.storage.SettingsStorage] singleton
 *   used to persist the Tailscale identity across cold starts.
 *
 * Include this module in [allModules] (see [AppModule.kt]) so that any
 * call-site can simply do:
 * ```kotlin
 * val repo = koinInject<TailscaleAuthRepository>()
 * ```
 */
val authModule = module {

    single<TailscaleAuthRepository> {
        // Falls back to janus_api on the tailnet. NOTE: the `/api/tailscale/*` routes
        // this repository calls are not currently served by janus (legacy/unwired) —
        // tailnet membership itself is the trust boundary for now.
        val baseUrl: String = try {
            getProperty("API_BASE_URL") ?: "https://oryx.tailfef10.ts.net:8443"
        } catch (e: Exception) {
            "https://oryx.tailfef10.ts.net:8443"
        }

        TailscaleAuthRepositoryImpl(
            httpClient = get(),
            baseUrl    = baseUrl,
            settings   = get(),
        )
    }
}
