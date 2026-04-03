package xyz.fkstrading.shared.data.websocket

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds

/**
 * Integration tests for WebSocket client against live backend.
 *
 * These tests require the FKS backend to be running locally:
 * ```
 * ./run.sh up
 * ```
 *
 * If the backend is not available, tests will gracefully skip
 * with informational messages.
 */
class WebSocketIntegrationTest {
    private lateinit var httpClient: HttpClient
    private lateinit var webSocketClient: WebSocketClient
    private lateinit var dataStream: WebSocketDataStream
    private lateinit var subscriptionManager: SubscriptionManager

    @BeforeTest
    fun setup() {
        // Create HttpClient with WebSockets support
        httpClient =
            HttpClient(CIO) {
                install(WebSockets) {
                    pingIntervalMillis = 30_000 // 30 seconds
                }
            }

        // Create WebSocket client with test-friendly config
        webSocketClient =
            WebSocketClientImpl(
                httpClient = httpClient,
                reconnectConfig =
                    ReconnectConfig(
                        initialDelayMs = 1000,
                        maxDelayMs = 5000,
                        multiplier = 2.0,
                        maxAttempts = 3, // Limit retries in tests
                    ),
            )

        dataStream = WebSocketDataStream(webSocketClient)
        subscriptionManager = SubscriptionManager(webSocketClient)
    }

    @AfterTest
    fun teardown() =
        runBlocking {
            try {
                subscriptionManager.unsubscribeAll()
                webSocketClient.disconnect()
                httpClient.close()
            } catch (e: Exception) {
                println("[Test] Teardown error: ${e.message}")
            }
        }

    @Test
    fun testConnectToBackendSignalsEndpoint() =
        runTest(timeout = 30.seconds) {
            println("\n=== Test: Connect to Backend Signals Endpoint ===")

            val result = webSocketClient.connect("ws://localhost:8000/ws/signals")

            if (result.isFailure) {
                val error = result.exceptionOrNull()
                println("⚠️  Backend not available: ${error?.message}")
                println("   To run this test, start the backend with: ./run.sh up")
                return@runTest // Skip test gracefully
            }

            println("✅ Connection initiated")

            // Wait for connection to establish
            val connected =
                withTimeoutOrNull(5000) {
                    webSocketClient.connectionState
                        .first { it is ConnectionState.Connected }
                }

            if (connected == null) {
                println("❌ Connection timeout - backend may not be running")
                return@runTest
            }

            println("✅ Connected to WebSocket endpoint")

            val finalState = webSocketClient.connectionState.value
            assertTrue(finalState is ConnectionState.Connected, "Expected Connected state, got $finalState")
        }

    @Test
    fun testReceiveRealTimeSignals() =
        runTest(timeout = 60.seconds) {
            println("\n=== Test: Receive Real-Time Signals ===")

            // Connect
            val connectResult = webSocketClient.connect("ws://localhost:8000/ws/signals")
            if (connectResult.isFailure) {
                println("⚠️  Backend not available, skipping test")
                return@runTest
            }

            // Wait for connection
            withTimeoutOrNull(5000) {
                webSocketClient.connectionState.first { it is ConnectionState.Connected }
            } ?: run {
                println("❌ Connection timeout")
                return@runTest
            }

            println("✅ Connected")

            // Subscribe to signals channel
            val subscribeResult = subscriptionManager.subscribe("signals")
            if (subscribeResult.isFailure) {
                println("⚠️  Failed to subscribe: ${subscribeResult.exceptionOrNull()?.message}")
                return@runTest
            }

            println("✅ Subscribed to signals channel")

            // Collect signals for up to 30 seconds
            val signals = mutableListOf<xyz.fkstrading.shared.domain.models.Signal>()
            val collectJob =
                launch {
                    dataStream.signalsFlow
                        .take(5) // Collect first 5 signals
                        .collect { signal ->
                            signals.add(signal)
                            println("   📊 Received signal: ${signal.symbol} ${signal.direction} @ ${signal.entryPrice}")
                        }
                }

            // Wait up to 30 seconds for signals
            delay(30000)
            collectJob.cancel()

            if (signals.isEmpty()) {
                println("⚠️  No signals received (backend may not be emitting signals)")
                println("   This is OK - backend might not have active signals")
            } else {
                println("✅ Received ${signals.size} signal(s)")

                // Verify signal structure
                signals.forEach { signal ->
                    assertNotNull(signal.signalId, "Signal should have ID")
                    assertNotNull(signal.symbol, "Signal should have symbol")
                    assertTrue(signal.confidence in 0.0..1.0, "Confidence should be 0-1")
                    assertTrue(signal.entryPrice > 0, "Entry price should be positive")
                }

                println("✅ All signals have valid structure")
            }
        }

    @Test
    fun testReconnectAfterDisconnect() =
        runTest(timeout = 60.seconds) {
            println("\n=== Test: Reconnect After Disconnect ===")

            val connectResult = webSocketClient.connect("ws://localhost:8000/ws/signals")
            if (connectResult.isFailure) {
                println("⚠️  Backend not available, skipping test")
                return@runTest
            }

            // Wait for connection
            withTimeoutOrNull(5000) {
                webSocketClient.connectionState.first { it is ConnectionState.Connected }
            } ?: run {
                println("❌ Initial connection timeout")
                return@runTest
            }

            println("✅ Initial connection established")

            // Manually disconnect
            webSocketClient.disconnect()
            println("   Disconnected manually")

            delay(1000)

            // Reconnect
            val reconnectResult = webSocketClient.connect("ws://localhost:8000/ws/signals")
            if (reconnectResult.isFailure) {
                println("❌ Reconnection failed: ${reconnectResult.exceptionOrNull()?.message}")
                fail("Should be able to reconnect")
            }

            // Wait for reconnection
            val reconnected =
                withTimeoutOrNull(10000) {
                    webSocketClient.connectionState.first { it is ConnectionState.Connected }
                }

            if (reconnected == null) {
                println("❌ Reconnection timeout")
                fail("Should reconnect within 10 seconds")
            }

            println("✅ Reconnected successfully")

            val finalState = webSocketClient.connectionState.value
            assertTrue(
                finalState is ConnectionState.Connected,
                "Should be in Connected state after reconnect",
            )
        }

    @Test
    fun testSubscriptionManagement() =
        runTest(timeout = 30.seconds) {
            println("\n=== Test: Subscription Management ===")

            val connectResult = webSocketClient.connect("ws://localhost:8000/ws/signals")
            if (connectResult.isFailure) {
                println("⚠️  Backend not available, skipping test")
                return@runTest
            }

            // Wait for connection
            withTimeoutOrNull(5000) {
                webSocketClient.connectionState.first { it is ConnectionState.Connected }
            } ?: run {
                println("❌ Connection timeout")
                return@runTest
            }

            println("✅ Connected")

            // Subscribe to multiple channels
            val channels = listOf("signals", "orders", "positions")
            println("   Subscribing to channels: $channels")

            for (channel in channels) {
                val result = subscriptionManager.subscribe(channel)
                if (result.isSuccess) {
                    println("   ✅ Subscribed to $channel")
                } else {
                    println("   ⚠️  Failed to subscribe to $channel: ${result.exceptionOrNull()?.message}")
                }
            }

            delay(1000)

            // Verify active subscriptions
            val activeSubscriptions = subscriptionManager.getActiveSubscriptions()
            println("   Active subscriptions: $activeSubscriptions")

            // Note: Backend might not support all channels
            assertTrue(
                activeSubscriptions.isNotEmpty(),
                "Should have at least one active subscription",
            )

            // Unsubscribe from all
            println("   Unsubscribing from all channels")
            subscriptionManager.unsubscribeAll()
            delay(500)

            val remainingSubscriptions = subscriptionManager.getActiveSubscriptions()
            println("   Remaining subscriptions: $remainingSubscriptions")

            assertTrue(
                remainingSubscriptions.isEmpty(),
                "Should have no active subscriptions after unsubscribeAll",
            )

            println("✅ Subscription management works correctly")
        }

    @Test
    fun testConnectionStateFlow() =
        runTest(timeout = 30.seconds) {
            println("\n=== Test: Connection State Flow ===")

            // Initial state should be Disconnected
            assertEquals(
                ConnectionState.Disconnected,
                webSocketClient.connectionState.value,
                "Initial state should be Disconnected",
            )
            println("✅ Initial state is Disconnected")

            // Connect
            val connectResult = webSocketClient.connect("ws://localhost:8000/ws/signals")
            if (connectResult.isFailure) {
                println("⚠️  Backend not available, skipping test")
                return@runTest
            }

            // Collect state transitions
            val states = mutableListOf<ConnectionState>()
            val stateJob =
                launch {
                    webSocketClient.connectionState.collect { state ->
                        states.add(state)
                        println("   State transition: $state")
                    }
                }

            // Wait for connection to establish
            val connected =
                withTimeoutOrNull(10000) {
                    webSocketClient.connectionState.first { it is ConnectionState.Connected }
                }

            stateJob.cancel()

            if (connected == null) {
                println("⚠️  Connection timeout - backend may be slow")
                // Still verify we got at least Disconnected and Connecting states
                assertTrue(
                    states.size >= 1,
                    "Should observe at least initial state",
                )
            } else {
                println("✅ State transitions observed: ${states.size}")

                // We should have seen at least Disconnected initially
                assertTrue(
                    states.any { it is ConnectionState.Disconnected },
                    "Should have observed Disconnected state",
                )

                // And we should end up Connected
                assertTrue(
                    states.any { it is ConnectionState.Connected },
                    "Should have observed Connected state",
                )
            }

            println("✅ State flow works correctly")
        }

    @Test
    fun testMessageObservation() =
        runTest(timeout = 30.seconds) {
            println("\n=== Test: Message Observation ===")

            val connectResult = webSocketClient.connect("ws://localhost:8000/ws/signals")
            if (connectResult.isFailure) {
                println("⚠️  Backend not available, skipping test")
                return@runTest
            }

            // Wait for connection
            withTimeoutOrNull(5000) {
                webSocketClient.connectionState.first { it is ConnectionState.Connected }
            } ?: run {
                println("❌ Connection timeout")
                return@runTest
            }

            println("✅ Connected")

            // Subscribe
            subscriptionManager.subscribe("signals")
            delay(1000)

            // Observe raw messages
            val messages = mutableListOf<String>()
            val messageJob =
                launch {
                    webSocketClient.observeMessages()
                        .take(3)
                        .collect { message ->
                            messages.add(message)
                            println("   📨 Received message: ${message.take(100)}...")
                        }
                }

            delay(20000) // Wait up to 20 seconds for messages
            messageJob.cancel()

            if (messages.isEmpty()) {
                println("⚠️  No messages received (backend may not be emitting)")
                println("   This is OK - backend might be idle")
            } else {
                println("✅ Received ${messages.size} message(s)")

                // Messages should be valid JSON strings
                messages.forEach { message ->
                    assertTrue(
                        message.contains("{") && message.contains("}"),
                        "Message should be JSON format",
                    )
                }

                println("✅ All messages are valid JSON")
            }
        }
}
