package xyz.fkstrading.shared.auth

/**
 * Represents the current Tailscale authentication state of the application.
 *
 * State machine:
 *
 *   [Loading] ──► [Connected]     — identity fetched, peer is on the tailnet
 *             └──► [NotConnected] — peer is not on the tailnet, or backend unreachable
 *             └──► [Error]        — unexpected / unrecoverable failure
 *
 * [NotConnected.isNetworkError] distinguishes between:
 *  - `false` → the backend is reachable but the peer is definitively NOT on the tailnet
 *              (access should be blocked)
 *  - `true`  → the backend itself could not be reached (dev mode — allow through with a
 *              warning banner so development can proceed without Tailscale)
 */
sealed class TailscaleAuthState {

    /**
     * The identity check is in progress (initial load or retry in flight).
     */
    data object Loading : TailscaleAuthState()

    /**
     * The peer is authenticated and present on the FKS tailnet.
     *
     * @param identity Full identity details returned by the backend.
     */
    data class Connected(val identity: TailscaleIdentity) : TailscaleAuthState()

    /**
     * The peer is not connected to the tailnet, or the backend could not be reached.
     *
     * @param reason     Human-readable explanation shown in the UI.
     * @param isNetworkError `true` when the backend itself was unreachable (connection
     *                       refused, DNS failure, timeout, etc.) rather than the peer
     *                       being explicitly not on the tailnet.
     */
    data class NotConnected(
        val reason: String = "Not connected to Tailscale network",
        val isNetworkError: Boolean = false,
    ) : TailscaleAuthState()

    /**
     * An unexpected error occurred that is not simply "not connected".
     *
     * @param message Developer-facing error description.
     */
    data class Error(val message: String) : TailscaleAuthState()
}
