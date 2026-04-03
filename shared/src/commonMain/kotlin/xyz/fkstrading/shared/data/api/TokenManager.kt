package xyz.fkstrading.shared.data.api

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages authentication tokens for API communication.
 *
 * Provides centralized token storage and access for use by
 * HttpClientFactory and other components that need to attach
 * authentication headers to outgoing requests.
 *
 * Usage:
 * ```kotlin
 * // After login
 * TokenManager.setTokens(accessToken = "eyJ...", refreshToken = "dGhpcyBpcyBh...")
 *
 * // Check auth state
 * if (TokenManager.isAuthenticated()) { ... }
 *
 * // Get token for manual header attachment
 * val token = TokenManager.getAccessToken()
 *
 * // On logout
 * TokenManager.clearTokens()
 * ```
 */
object TokenManager {
    private var accessToken: String? = null
    private var refreshToken: String? = null

    private val _authenticated = MutableStateFlow(false)

    /**
     * Observable authentication state.
     * Emits `true` when tokens are set, `false` when cleared.
     */
    val authenticated: StateFlow<Boolean> = _authenticated.asStateFlow()

    /**
     * Store access and refresh tokens after successful authentication.
     *
     * @param access The JWT access token
     * @param refresh Optional refresh token for token renewal
     */
    fun setTokens(access: String, refresh: String? = null) {
        accessToken = access
        refreshToken = refresh
        _authenticated.value = true
    }

    /**
     * Retrieve the current access token, if available.
     *
     * @return The access token string, or null if not authenticated
     */
    fun getAccessToken(): String? = accessToken

    /**
     * Retrieve the current refresh token, if available.
     *
     * @return The refresh token string, or null if not set
     */
    fun getRefreshToken(): String? = refreshToken

    /**
     * Clear all stored tokens (logout).
     *
     * This resets the authentication state and removes both
     * the access and refresh tokens from memory.
     */
    fun clearTokens() {
        accessToken = null
        refreshToken = null
        _authenticated.value = false
    }

    /**
     * Check whether the user is currently authenticated.
     *
     * @return `true` if an access token is stored, `false` otherwise
     */
    fun isAuthenticated(): Boolean = accessToken != null
}
