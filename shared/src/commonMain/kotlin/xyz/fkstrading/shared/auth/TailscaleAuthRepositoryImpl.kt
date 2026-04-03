package xyz.fkstrading.shared.auth

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import xyz.fkstrading.shared.data.storage.SettingsStorage

private const val KEY_LOGIN_NAME   = "ts_login_name"
private const val KEY_DISPLAY_NAME = "ts_display_name"
private const val KEY_TAILNET      = "ts_tailnet"
private const val KEY_DEVICE       = "ts_device"

/**
 * Concrete implementation of [TailscaleAuthRepository].
 *
 * Lifecycle
 * ---------
 * 1. On construction, checks [SettingsStorage] for a previously-persisted
 *    login name.  If one exists the initial state is a "cached"
 *    [TailscaleAuthState.Connected] so the UI can render immediately while
 *    the fresh network fetch is in flight.  If no cache exists, the initial
 *    state is [TailscaleAuthState.Loading].
 *
 * 2. [fetchIdentity] always contacts the backend.  If the peer is still
 *    connected, the cached state is silently replaced with the live identity.
 *    If the peer is no longer connected, or the backend is unreachable, the
 *    state is updated accordingly.
 *
 * Persistence
 * -----------
 * Only four fields are persisted (login_name, display_name, tailnet, device)
 * — enough to reconstruct a skeleton [TailscaleIdentity] for the offline /
 * cached-start case.  The full identity (node_key, IPs, etc.) is ephemeral.
 *
 * @param httpClient Ktor [HttpClient] configured with JSON content negotiation.
 *                   Typically injected from the shared [networkModule].
 * @param baseUrl    Backend API root, e.g. `"http://100.x.x.x:8000"`.
 * @param settings   Cross-platform key-value storage for identity persistence.
 */
class TailscaleAuthRepositoryImpl(
    private val httpClient: HttpClient,
    private val baseUrl: String,
    private val settings: SettingsStorage,
) : TailscaleAuthRepository {

    // -------------------------------------------------------------------------
    // State
    // -------------------------------------------------------------------------

    private val _authState: MutableStateFlow<TailscaleAuthState> =
        MutableStateFlow(buildInitialState())

    override val authState: StateFlow<TailscaleAuthState> = _authState.asStateFlow()

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    override suspend fun fetchIdentity(): TailscaleIdentity? {
        // Only flash the Loading indicator when there is no valid cached state
        // to show — avoids an unnecessary flicker on background refreshes.
        if (_authState.value !is TailscaleAuthState.Connected) {
            _authState.value = TailscaleAuthState.Loading
        }

        return try {
            val identity: TailscaleIdentity =
                httpClient.get("$baseUrl/api/tailscale/identity").body()

            if (identity.connected) {
                _authState.value = TailscaleAuthState.Connected(identity)
                persistIdentity(identity)
            } else {
                _authState.value = TailscaleAuthState.NotConnected(
                    reason = identity.message ?: "Not connected to Tailscale network",
                )
            }

            identity
        } catch (e: Exception) {
            _authState.value = TailscaleAuthState.NotConnected(
                reason = "Cannot reach backend. Check Tailscale is running.",
                isNetworkError = true,
            )
            null
        }
    }

    override suspend fun checkBackendStatus(): TailscaleStatus? =
        try {
            httpClient.get("$baseUrl/api/tailscale/status").body()
        } catch (e: Exception) {
            null
        }

    override fun clearIdentity() {
        settings.remove(KEY_LOGIN_NAME)
        settings.remove(KEY_DISPLAY_NAME)
        settings.remove(KEY_TAILNET)
        settings.remove(KEY_DEVICE)
        _authState.value = TailscaleAuthState.NotConnected()
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Builds the initial [TailscaleAuthState] from persisted storage.
     *
     * If a login name was saved during a previous session, returns a skeleton
     * [TailscaleAuthState.Connected] so the UI has something to render while
     * [fetchIdentity] runs in the background.  Otherwise returns
     * [TailscaleAuthState.Loading].
     */
    private fun buildInitialState(): TailscaleAuthState {
        if (!settings.hasKey(KEY_LOGIN_NAME)) {
            return TailscaleAuthState.Loading
        }

        val cachedLoginName   = settings.getString(KEY_LOGIN_NAME,   "")
        val cachedDisplayName = settings.getString(KEY_DISPLAY_NAME, "")
        val cachedTailnet     = settings.getString(KEY_TAILNET,      "")
        val cachedDevice      = settings.getString(KEY_DEVICE,       "")

        val skeletonIdentity = TailscaleIdentity(
            connected          = true,
            tailscaleAvailable = true,
            loginName          = cachedLoginName.ifBlank { null },
            displayName        = cachedDisplayName.ifBlank { null },
            tailnet            = cachedTailnet.ifBlank { null },
            device             = cachedDevice.ifBlank { null },
        )

        return TailscaleAuthState.Connected(skeletonIdentity)
    }

    /**
     * Persists the four identity fields that are used to reconstruct
     * the skeleton Connected state on the next cold start.
     */
    private fun persistIdentity(identity: TailscaleIdentity) {
        settings.putString(KEY_LOGIN_NAME,   identity.loginName   ?: "")
        settings.putString(KEY_DISPLAY_NAME, identity.displayName ?: "")
        settings.putString(KEY_TAILNET,      identity.tailnet     ?: "")
        settings.putString(KEY_DEVICE,       identity.device      ?: "")
    }
}
