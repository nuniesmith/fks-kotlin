package xyz.fkstrading.shared.data.websocket

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Manages WebSocket channel subscriptions.
 *
 * This class handles subscribing and unsubscribing to different data channels
 * on the WebSocket connection. It maintains a thread-safe set of active
 * subscriptions and prevents duplicate subscription requests.
 *
 * Supported channels:
 * - "signals" - Trading signals
 * - "orders" - Order updates
 * - "positions" - Position updates
 * - "market" - Market data
 *
 * Example usage:
 * ```
 * val manager: SubscriptionManager = get() // from DI
 *
 * // Subscribe to signals
 * manager.subscribe("signals")
 *     .onSuccess { println("Subscribed to signals") }
 *     .onFailure { println("Failed: $it") }
 *
 * // Unsubscribe when done
 * manager.unsubscribe("signals")
 *
 * // Unsubscribe from all
 * manager.unsubscribeAll()
 * ```
 *
 * @param webSocketClient The WebSocket client to send subscription messages through
 */
class SubscriptionManager(
    private val webSocketClient: WebSocketClient,
) {
    private val mutex = Mutex()
    private val _activeSubscriptions = MutableStateFlow<Set<String>>(emptySet())

    /**
     * StateFlow of currently active subscriptions.
     *
     * Observers can collect this flow to reactively track subscription changes.
     */
    val activeSubscriptions: StateFlow<Set<String>> = _activeSubscriptions.asStateFlow()

    private val json =
        Json {
            ignoreUnknownKeys = true
            prettyPrint = false
        }

    /**
     * Subscribe to a data channel.
     *
     * Sends a subscription message to the backend and tracks the subscription
     * in the active subscriptions set. If already subscribed to the channel,
     * this method will succeed without sending a duplicate request.
     *
     * @param channel Channel name (e.g., "signals", "orders", "positions", "market")
     * @return Result indicating success or failure
     */
    suspend fun subscribe(channel: String): Result<Unit> =
        runCatching {
            mutex.withLock {
                if (_activeSubscriptions.value.contains(channel)) {
                    println("[SubscriptionManager] Already subscribed to $channel")
                    return Result.success(Unit)
                }

                val subscribeMessage =
                    SubscriptionRequest(
                        action = "subscribe",
                        channel = channel,
                    )

                val messageJson = json.encodeToString(subscribeMessage)

                webSocketClient.sendMessage(messageJson)
                    .onSuccess {
                        _activeSubscriptions.value = _activeSubscriptions.value + channel
                        println("[SubscriptionManager] Subscribed to $channel")
                    }
                    .onFailure { error ->
                        println("[SubscriptionManager] Failed to subscribe to $channel: ${error.message}")
                        throw error
                    }
                    .getOrThrow()
            }
        }

    /**
     * Unsubscribe from a data channel.
     *
     * Sends an unsubscribe message to the backend and removes the subscription
     * from the active subscriptions set. If not subscribed to the channel,
     * this method will succeed without sending a request.
     *
     * @param channel Channel name to unsubscribe from
     * @return Result indicating success or failure
     */
    suspend fun unsubscribe(channel: String): Result<Unit> =
        runCatching {
            mutex.withLock {
                if (!_activeSubscriptions.value.contains(channel)) {
                    println("[SubscriptionManager] Not subscribed to $channel")
                    return Result.success(Unit)
                }

                val unsubscribeMessage =
                    SubscriptionRequest(
                        action = "unsubscribe",
                        channel = channel,
                    )

                val messageJson = json.encodeToString(unsubscribeMessage)

                webSocketClient.sendMessage(messageJson)
                    .onSuccess {
                        _activeSubscriptions.value = _activeSubscriptions.value - channel
                        println("[SubscriptionManager] Unsubscribed from $channel")
                    }
                    .onFailure { error ->
                        println("[SubscriptionManager] Failed to unsubscribe from $channel: ${error.message}")
                        throw error
                    }
                    .getOrThrow()
            }
        }

    /**
     * Get a list of currently active subscriptions.
     *
     * @return Immutable set of active channel names
     */
    suspend fun getActiveSubscriptions(): Set<String> = activeSubscriptions.value

    /**
     * Check if currently subscribed to a specific channel.
     *
     * @param channel Channel name to check
     * @return True if subscribed, false otherwise
     */
    suspend fun isSubscribed(channel: String): Boolean = activeSubscriptions.value.contains(channel)

    /**
     * Unsubscribe from all currently active channels.
     *
     * This method sends unsubscribe messages for all active subscriptions
     * and clears the active subscriptions set.
     *
     * @return Result indicating success or failure. If any unsubscribe fails,
     *         the first error is returned but all unsubscribe attempts are made.
     */
    suspend fun unsubscribeAll(): Result<Unit> =
        runCatching {
            val channels = getActiveSubscriptions()
            var firstError: Throwable? = null

            channels.forEach { channel ->
                unsubscribe(channel).onFailure { error ->
                    if (firstError == null) {
                        firstError = error
                    }
                }
            }

            firstError?.let { throw it }
            println("[SubscriptionManager] Unsubscribed from all channels")
        }

    /**
     * Subscribe to multiple channels at once.
     *
     * This is a convenience method for subscribing to multiple channels.
     * If any subscription fails, the method continues attempting to subscribe
     * to the remaining channels and returns the first error encountered.
     *
     * @param channels List of channel names to subscribe to
     * @return Result indicating success or failure
     */
    suspend fun subscribeAll(channels: List<String>): Result<Unit> =
        runCatching {
            var firstError: Throwable? = null

            channels.forEach { channel ->
                subscribe(channel).onFailure { error ->
                    if (firstError == null) {
                        firstError = error
                    }
                }
            }

            firstError?.let { throw it }
            println("[SubscriptionManager] Subscribed to ${channels.size} channels")
        }

    /**
     * Clear all subscription tracking without sending unsubscribe messages.
     *
     * This should be called when the WebSocket connection is lost to reset
     * the subscription state. When reconnecting, subscriptions will need to
     * be re-established.
     */
    suspend fun clearSubscriptions() {
        mutex.withLock {
            println("[SubscriptionManager] Clearing ${_activeSubscriptions.value.size} subscriptions")
            _activeSubscriptions.value = emptySet()
        }
    }
}

/**
 * Subscription request message format.
 *
 * This is the message format expected by the backend for subscription actions.
 */
@Serializable
private data class SubscriptionRequest(
    val action: String, // "subscribe" or "unsubscribe"
    val channel: String, // channel name
)
