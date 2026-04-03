package xyz.fkstrading.client.features.auth

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import xyz.fkstrading.shared.auth.TailscaleAuthRepository
import xyz.fkstrading.shared.auth.TailscaleAuthState

/**
 * ViewModel for Tailscale authentication.
 *
 * Wraps [TailscaleAuthRepository] and exposes the auth state as a [StateFlow]
 * so that composables can observe it reactively.
 *
 * Follows the same manual-[CoroutineScope] convention used by every other
 * ViewModel in this project (e.g. [xyz.fkstrading.client.features.orders.OrdersViewModel])
 * to avoid taking a hard dependency on `androidx.lifecycle:lifecycle-viewmodel`
 * which is not declared in the version catalog.
 *
 * Usage in a composable:
 * ```kotlin
 * val vm = remember { AuthViewModel(koinInject()) }
 * val state by vm.authState.collectAsState()
 * LaunchedEffect(Unit) { vm.checkAuth() }
 * ```
 *
 * Call [onCleared] when the host composable leaves the composition to release
 * the internal coroutine scope.
 */
class AuthViewModel(private val repo: TailscaleAuthRepository) {

    /**
     * Internal coroutine scope tied to the ViewModel's lifetime.
     *
     * [SupervisorJob] ensures that a failing child coroutine (e.g. a network
     * request) does not cancel sibling coroutines (e.g. an in-flight retry).
     * [Dispatchers.Main] keeps state updates on the UI thread so that
     * [StateFlow] collectors do not need to switch contexts.
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /**
     * The current Tailscale authentication state, backed by the repository's
     * [StateFlow].  Collecting this flow in a composable will automatically
     * trigger recomposition on every state change.
     */
    val authState: StateFlow<TailscaleAuthState> = repo.authState

    /**
     * Triggers an initial authentication check by fetching the Tailscale
     * identity from the backend.
     *
     * Typically called once inside a `LaunchedEffect(Unit)` block.  Subsequent
     * checks (e.g. after the user taps "Retry") should go through [retry].
     */
    fun checkAuth() {
        scope.launch { repo.fetchIdentity() }
    }

    /**
     * Re-fetches the Tailscale identity, intended for use by a "Retry" button
     * after a [TailscaleAuthState.NotConnected] or [TailscaleAuthState.Error]
     * state is displayed to the user.
     */
    fun retry() {
        scope.launch { repo.fetchIdentity() }
    }

    /**
     * Cancels the internal [CoroutineScope] and frees resources.
     *
     * Call this from a `DisposableEffect` or equivalent lifecycle hook when
     * the composable that owns this ViewModel is removed from the composition.
     */
    fun onCleared() {
        scope.cancel()
    }
}
