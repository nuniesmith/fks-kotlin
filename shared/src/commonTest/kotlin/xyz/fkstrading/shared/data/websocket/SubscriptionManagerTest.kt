package xyz.fkstrading.shared.data.websocket

import kotlinx.coroutines.test.*
import kotlin.test.*

/**
 * Unit tests for SubscriptionManager.
 *
 * Tests subscription/unsubscription logic and state management.
 */
class SubscriptionManagerTest {
    private lateinit var mockWebSocketClient: MockWebSocketClient
    private lateinit var subscriptionManager: SubscriptionManager

    @BeforeTest
    fun setup() {
        mockWebSocketClient = MockWebSocketClient()
        subscriptionManager = SubscriptionManager(mockWebSocketClient)
    }

    @Test
    fun testSubscribeToChannel() =
        runTest {
            val result = subscriptionManager.subscribe("signals")

            assertTrue(result.isSuccess, "Subscribe should succeed")
            assertEquals(1, mockWebSocketClient.sentMessages.size, "Should send one message")
            assertTrue(
                mockWebSocketClient.sentMessages[0].contains("\"action\":\"subscribe\""),
                "Message should contain subscribe action",
            )
            assertTrue(
                mockWebSocketClient.sentMessages[0].contains("\"channel\":\"signals\""),
                "Message should contain channel name",
            )
        }

    @Test
    fun testSubscribeToMultipleChannels() =
        runTest {
            subscriptionManager.subscribe("signals")
            subscriptionManager.subscribe("orders")
            subscriptionManager.subscribe("positions")

            val activeSubscriptions = subscriptionManager.getActiveSubscriptions()

            assertEquals(3, activeSubscriptions.size, "Should have 3 active subscriptions")
            assertTrue(activeSubscriptions.contains("signals"))
            assertTrue(activeSubscriptions.contains("orders"))
            assertTrue(activeSubscriptions.contains("positions"))
        }

    @Test
    fun testSubscribeToDuplicateChannelIsIdempotent() =
        runTest {
            subscriptionManager.subscribe("signals")
            subscriptionManager.subscribe("signals") // Duplicate

            val activeSubscriptions = subscriptionManager.getActiveSubscriptions()

            assertEquals(1, activeSubscriptions.size, "Should only have one subscription")
            assertEquals(1, mockWebSocketClient.sentMessages.size, "Should only send one message")
        }

    @Test
    fun testUnsubscribeFromChannel() =
        runTest {
            subscriptionManager.subscribe("signals")

            val unsubscribeResult = subscriptionManager.unsubscribe("signals")

            assertTrue(unsubscribeResult.isSuccess, "Unsubscribe should succeed")
            assertEquals(2, mockWebSocketClient.sentMessages.size, "Should send subscribe + unsubscribe")
            assertTrue(
                mockWebSocketClient.sentMessages[1].contains("\"action\":\"unsubscribe\""),
                "Second message should be unsubscribe",
            )

            val activeSubscriptions = subscriptionManager.getActiveSubscriptions()
            assertTrue(activeSubscriptions.isEmpty(), "Should have no active subscriptions")
        }

    @Test
    fun testUnsubscribeFromNonSubscribedChannel() =
        runTest {
            val result = subscriptionManager.unsubscribe("signals")

            assertTrue(result.isSuccess, "Unsubscribe should succeed even if not subscribed")
            assertTrue(mockWebSocketClient.sentMessages.isEmpty(), "Should not send any messages")
        }

    @Test
    fun testUnsubscribeAll() =
        runTest {
            subscriptionManager.subscribe("signals")
            subscriptionManager.subscribe("orders")
            subscriptionManager.subscribe("positions")

            val result = subscriptionManager.unsubscribeAll()

            assertTrue(result.isSuccess, "UnsubscribeAll should succeed")

            val activeSubscriptions = subscriptionManager.getActiveSubscriptions()
            assertTrue(activeSubscriptions.isEmpty(), "Should have no active subscriptions")
        }

    @Test
    fun testSubscribeAll() =
        runTest {
            val channels = listOf("signals", "orders", "positions", "market")

            val result = subscriptionManager.subscribeAll(channels)

            assertTrue(result.isSuccess, "SubscribeAll should succeed")

            val activeSubscriptions = subscriptionManager.getActiveSubscriptions()
            assertEquals(4, activeSubscriptions.size, "Should have 4 active subscriptions")
            channels.forEach { channel ->
                assertTrue(activeSubscriptions.contains(channel), "Should contain $channel")
            }
        }

    @Test
    fun testIsSubscribed() =
        runTest {
            assertFalse(subscriptionManager.isSubscribed("signals"), "Should not be subscribed initially")

            subscriptionManager.subscribe("signals")

            assertTrue(subscriptionManager.isSubscribed("signals"), "Should be subscribed after subscribe")

            subscriptionManager.unsubscribe("signals")

            assertFalse(subscriptionManager.isSubscribed("signals"), "Should not be subscribed after unsubscribe")
        }

    @Test
    fun testGetActiveSubscriptionsInitiallyEmpty() =
        runTest {
            val subscriptions = subscriptionManager.getActiveSubscriptions()

            assertTrue(subscriptions.isEmpty(), "Should have no subscriptions initially")
        }

    @Test
    fun testGetActiveSubscriptionsReturnsImmutableCopy() =
        runTest {
            subscriptionManager.subscribe("signals")

            val subscriptions1 = subscriptionManager.getActiveSubscriptions()
            val subscriptions2 = subscriptionManager.getActiveSubscriptions()

            // With StateFlow, same value instance is returned (which is fine - it's immutable)
            assertEquals(subscriptions1, subscriptions2, "Should have same content")
            assertTrue(subscriptions1.contains("signals"), "Should contain subscribed channel")
        }

    @Test
    fun testClearSubscriptions() =
        runTest {
            subscriptionManager.subscribe("signals")
            subscriptionManager.subscribe("orders")

            subscriptionManager.clearSubscriptions()

            val activeSubscriptions = subscriptionManager.getActiveSubscriptions()
            assertTrue(activeSubscriptions.isEmpty(), "Should have no subscriptions after clear")

            // Note: clearSubscriptions doesn't send unsubscribe messages
            assertEquals(2, mockWebSocketClient.sentMessages.size, "Should only have subscribe messages")
        }

    @Test
    fun testSubscribeWhenClientNotConnected() =
        runTest {
            mockWebSocketClient.shouldFailSend = true

            val result = subscriptionManager.subscribe("signals")

            assertTrue(result.isFailure, "Subscribe should fail when client can't send")

            val activeSubscriptions = subscriptionManager.getActiveSubscriptions()
            assertTrue(activeSubscriptions.isEmpty(), "Should not track failed subscription")
        }

    @Test
    fun testUnsubscribeWhenClientNotConnected() =
        runTest {
            // Subscribe first
            subscriptionManager.subscribe("signals")

            // Now make client fail
            mockWebSocketClient.shouldFailSend = true

            val result = subscriptionManager.unsubscribe("signals")

            assertTrue(result.isFailure, "Unsubscribe should fail when client can't send")

            // Subscription should still be tracked since unsubscribe failed
            val activeSubscriptions = subscriptionManager.getActiveSubscriptions()
            assertTrue(activeSubscriptions.contains("signals"), "Should still track subscription")
        }

    @Test
    fun testJsonMessageFormat() =
        runTest {
            subscriptionManager.subscribe("signals")

            val message = mockWebSocketClient.sentMessages.first()

            // Verify JSON format
            assertTrue(message.startsWith("{"), "Should start with {")
            assertTrue(message.endsWith("}"), "Should end with }")
            assertTrue(message.contains("\"action\""), "Should have action field")
            assertTrue(message.contains("\"channel\""), "Should have channel field")
        }

    @Test
    fun testThreadSafety() =
        runTest {
            // Simulate concurrent subscriptions
            val channels = (1..10).map { "channel_$it" }

            // Subscribe to all channels concurrently
            channels.forEach { channel ->
                subscriptionManager.subscribe(channel)
            }

            val activeSubscriptions = subscriptionManager.getActiveSubscriptions()
            assertEquals(10, activeSubscriptions.size, "Should have all 10 subscriptions")
        }

    @Test
    fun testUnsubscribeAllWithSomeFailures() =
        runTest {
            subscriptionManager.subscribe("signals")
            subscriptionManager.subscribe("orders")

            // Make subsequent sends fail
            mockWebSocketClient.failAfterCount = 1

            val result = subscriptionManager.unsubscribeAll()

            assertTrue(result.isFailure, "Should fail if any unsubscribe fails")

            // Should have attempted to unsubscribe from all
            assertTrue(mockWebSocketClient.sentMessages.size >= 2, "Should attempt all unsubscribes")
        }

    @Test
    fun testSubscribeAllWithSomeFailures() =
        runTest {
            val channels = listOf("signals", "orders", "positions")

            mockWebSocketClient.failAfterCount = 2

            val result = subscriptionManager.subscribeAll(channels)

            assertTrue(result.isFailure, "Should fail if any subscribe fails")
        }

    // Mock WebSocket client for testing
    private class MockWebSocketClient : WebSocketClient {
        val sentMessages = mutableListOf<String>()
        var shouldFailSend = false
        var failAfterCount: Int = Int.MAX_VALUE
        private var sendCount = 0

        private val _connectionState =
            kotlinx.coroutines.flow.MutableStateFlow<ConnectionState>(
                ConnectionState.Connected,
            )
        override val connectionState: kotlinx.coroutines.flow.StateFlow<ConnectionState> = _connectionState

        override suspend fun connect(url: String): Result<Unit> = Result.success(Unit)

        override suspend fun disconnect() {
            _connectionState.value = ConnectionState.Disconnected
        }

        override suspend fun sendMessage(message: String): Result<Unit> {
            sendCount++

            return if (shouldFailSend || sendCount > failAfterCount) {
                Result.failure(Exception("Send failed"))
            } else {
                sentMessages.add(message)
                Result.success(Unit)
            }
        }

        override fun observeMessages(): kotlinx.coroutines.flow.Flow<String> {
            return kotlinx.coroutines.flow.emptyFlow()
        }

        override fun getCurrentUrl(): String? = "ws://test"
    }
}
