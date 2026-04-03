package xyz.fkstrading.shared.auth

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents the Tailscale identity of the connecting client.
 *
 * Populated from GET /api/tailscale/identity.
 *
 * When [connected] is true the user is on the tailnet and all identity
 * fields are populated.  When false only [clientIp] and [message] may
 * be present.
 */
@Serializable
data class TailscaleIdentity(
    val connected: Boolean,
    @SerialName("tailscale_available") val tailscaleAvailable: Boolean = false,
    @SerialName("client_ip") val clientIp: String? = null,
    @SerialName("tailscale_ip") val tailscaleIp: String? = null,
    @SerialName("login_name") val loginName: String? = null,
    @SerialName("display_name") val displayName: String? = null,
    val tailnet: String? = null,
    val device: String? = null,
    @SerialName("node_key") val nodeKey: String? = null,
    val os: String? = null,
    val message: String? = null,
    val error: String? = null,
) {
    /**
     * A human-readable name for the user.
     *
     * Preference order:
     * 1. `display_name` (e.g. "Jordan")
     * 2. local part of `login_name` (e.g. "jordan" from "jordan@example.com")
     * 3. "Unknown"
     */
    val friendlyName: String
        get() = displayName?.takeIf { it.isNotBlank() }
            ?: loginName?.substringBefore('@')?.takeIf { it.isNotBlank() }
            ?: "Unknown"

    /**
     * The best unique identifier available for this peer.
     *
     * Preference order: login_name → client_ip → "Unknown"
     */
    val identifier: String
        get() = loginName ?: clientIp ?: "Unknown"
}

/**
 * Represents the overall Tailscale daemon status on the backend host.
 *
 * Populated from GET /api/tailscale/status.
 */
@Serializable
data class TailscaleStatus(
    val available: Boolean,
    val online: Boolean = false,
    @SerialName("backend_state") val backendState: String? = null,
    val hostname: String? = null,
    @SerialName("tailnet_name") val tailnetName: String? = null,
    @SerialName("tailscale_ips") val tailscaleIps: List<String> = emptyList(),
    val message: String? = null,
    val error: String? = null,
)
