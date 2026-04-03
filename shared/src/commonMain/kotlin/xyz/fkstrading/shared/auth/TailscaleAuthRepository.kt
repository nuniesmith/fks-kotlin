package xyz.fkstrading.shared.auth

import kotlinx.coroutines.flow.StateFlow

/**
 * Repository interface for Tailscale authentication.
 *
 * Provides a [StateFlow]-based observable auth state plus imperative
 * operations to refresh, inspect, and clear the Tailscale identity.
 *
 * The concrete implementation ([TailscaleAuthRepositoryImpl]) is wired
 * into Koin via [xyz.fkstrading.shared.di.authModule].
 *
 * Typical call-site pattern in a Compose composable:
 * ```
 * val repo = koinInject<TailscaleAuthRepository>()
 * val state by repo.authState.collectAsState()
 * LaunchedEffect(Unit) { repo.fetchIdentity() }
 * ```
 */
interface TailscaleAuthRepository {

    /**
     * Observable stream of the current authentication state.
     *
     * - Starts as [TailscaleAuthState.Loading] (or a cached
     *   [TailscaleAuthState.Connected] if a prior login was persisted).
     * - Updated after each call to [fetchIdentity].
     * - Reverts to [TailscaleAuthState.NotConnected] after [clearIdentity].
     */
    val authState: StateFlow<TailscaleAuthState>

    /**
     * Fetches the Tailscale identity from the backend
     * (`GET /api/tailscale/identity`) and updates [authState] accordingly.
     *
     * - On success with `connected == true`  → emits [TailscaleAuthState.Connected]
     *   and persists key identity fields to local storage.
     * - On success with `connected == false` → emits [TailscaleAuthState.NotConnected].
     * - On network/IO error                  → emits [TailscaleAuthState.NotConnected]
     *   with `isNetworkError = true`.
     *
     * @return The raw [TailscaleIdentity] deserialized from the response, or
     *         `null` if the request failed.
     */
    suspend fun fetchIdentity(): TailscaleIdentity?

    /**
     * Queries the Tailscale daemon status on the backend host
     * (`GET /api/tailscale/status`).
     *
     * This is a read-only probe that does **not** update [authState].
     *
     * @return A [TailscaleStatus] snapshot, or `null` if the request failed.
     */
    suspend fun checkBackendStatus(): TailscaleStatus?

    /**
     * Removes all persisted Tailscale identity data from local storage and
     * resets [authState] to [TailscaleAuthState.NotConnected].
     *
     * Call this on logout or when the user explicitly disconnects from the
     * tailnet.
     */
    fun clearIdentity()
}
