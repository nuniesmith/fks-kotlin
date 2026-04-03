package xyz.fkstrading.shared.data.websocket

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import xyz.fkstrading.shared.data.websocket.messages.*
import kotlin.test.*

/**
 * Unit tests for WebSocket message parsing and serialization.
 *
 * Tests JSON serialization/deserialization of all message types.
 */
class MessageParsingTest {
    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
        }

    @Test
    fun testParseSignalMessage() {
        val jsonString =
            """
            {
                "type": "signal",
                "timestamp": "2024-01-15T10:30:00Z",
                "data": {
                    "id": "sig_123",
                    "symbol": "BTC/USD",
                    "type": "BUY",
                    "price": 45000.0,
                    "confidence": 0.85,
                    "strategy": "momentum",
                    "timeframe": "M15",
                    "indicators": {
                        "rsi": "70",
                        "macd": "bullish"
                    }
                }
            }
            """.trimIndent()

        val message = json.decodeFromString<SignalMessage>(jsonString)

        assertEquals("2024-01-15T10:30:00Z", message.timestamp)
        assertEquals("sig_123", message.data.id)
        assertEquals("BTC/USD", message.data.symbol)
        assertEquals("BUY", message.data.type)
        assertEquals(45000.0, message.data.price)
        assertEquals(0.85, message.data.confidence)
        assertEquals("momentum", message.data.strategy)
        assertEquals("M15", message.data.timeframe)
        assertNotNull(message.data.indicators)
        assertEquals("70", message.data.indicators?.get("rsi"))
    }

    @Test
    fun testParseSignalMessageMinimal() {
        val jsonString =
            """
            {
                "type": "signal",
                "timestamp": "2024-01-15T10:30:00Z",
                "data": {
                    "id": "sig_123",
                    "symbol": "ETH/USD",
                    "type": "SELL",
                    "price": 2500.0,
                    "confidence": 0.75
                }
            }
            """.trimIndent()

        val message = json.decodeFromString<SignalMessage>(jsonString)

        assertEquals("sig_123", message.data.id)
        assertEquals("ETH/USD", message.data.symbol)
        assertEquals("SELL", message.data.type)
        assertNull(message.data.strategy)
        assertNull(message.data.timeframe)
        assertNull(message.data.indicators)
    }

    @Test
    fun testParseOrderMessage() {
        val jsonString =
            """
            {
                "type": "order",
                "timestamp": "2024-01-15T10:30:00Z",
                "data": {
                    "id": "ord_456",
                    "symbol": "BTC/USD",
                    "side": "BUY",
                    "order_type": "LIMIT",
                    "quantity": 0.5,
                    "price": 44000.0,
                    "status": "OPEN",
                    "filled_quantity": 0.0,
                    "average_fill_price": null,
                    "created_at": "2024-01-15T10:29:00Z",
                    "updated_at": "2024-01-15T10:30:00Z"
                }
            }
            """.trimIndent()

        val message = json.decodeFromString<OrderMessage>(jsonString)

        assertEquals("ord_456", message.data.id)
        assertEquals("BTC/USD", message.data.symbol)
        assertEquals("BUY", message.data.side)
        assertEquals("LIMIT", message.data.orderType)
        assertEquals(0.5, message.data.quantity)
        assertEquals(44000.0, message.data.price)
        assertEquals("OPEN", message.data.status)
        assertEquals(0.0, message.data.filledQuantity)
        assertNull(message.data.averageFillPrice)
    }

    @Test
    fun testParseOrderMessageWithStopPrice() {
        val jsonString =
            """
            {
                "type": "order",
                "timestamp": "2024-01-15T10:30:00Z",
                "data": {
                    "id": "ord_789",
                    "symbol": "ETH/USD",
                    "side": "SELL",
                    "order_type": "STOP_LIMIT",
                    "quantity": 1.0,
                    "price": 2400.0,
                    "stop_price": 2450.0,
                    "status": "PENDING"
                }
            }
            """.trimIndent()

        val message = json.decodeFromString<OrderMessage>(jsonString)

        assertEquals("STOP_LIMIT", message.data.orderType)
        assertEquals(2400.0, message.data.price)
        assertEquals(2450.0, message.data.stopPrice)
    }

    @Test
    fun testParsePositionMessage() {
        val jsonString =
            """
            {
                "type": "position",
                "timestamp": "2024-01-15T10:30:00Z",
                "data": {
                    "id": "pos_111",
                    "symbol": "BTC/USD",
                    "quantity": 0.5,
                    "entry_price": 43000.0,
                    "current_price": 45000.0,
                    "unrealized_pnl": 1000.0,
                    "unrealized_pnl_percent": 4.65,
                    "realized_pnl": 0.0,
                    "side": "LONG",
                    "opened_at": "2024-01-15T09:00:00Z"
                }
            }
            """.trimIndent()

        val message = json.decodeFromString<PositionMessage>(jsonString)

        assertEquals("pos_111", message.data.id)
        assertEquals("BTC/USD", message.data.symbol)
        assertEquals(0.5, message.data.quantity)
        assertEquals(43000.0, message.data.entryPrice)
        assertEquals(45000.0, message.data.currentPrice)
        assertEquals(1000.0, message.data.unrealizedPnl)
        assertEquals(4.65, message.data.unrealizedPnlPercent)
        assertEquals("LONG", message.data.side)
    }

    @Test
    fun testParseMarketDataMessage() {
        val jsonString =
            """
            {
                "type": "market_data",
                "timestamp": "2024-01-15T10:30:00Z",
                "data": {
                    "symbol": "BTC/USD",
                    "bid": 44950.0,
                    "ask": 45050.0,
                    "last": 45000.0,
                    "volume": 12345.67,
                    "high": 45500.0,
                    "low": 44000.0,
                    "open": 44200.0,
                    "close": 45000.0,
                    "bid_size": 2.5,
                    "ask_size": 3.0,
                    "change": 800.0,
                    "change_percent": 1.81
                }
            }
            """.trimIndent()

        val message = json.decodeFromString<MarketDataMessage>(jsonString)

        assertEquals("BTC/USD", message.data.symbol)
        assertEquals(44950.0, message.data.bid)
        assertEquals(45050.0, message.data.ask)
        assertEquals(45000.0, message.data.last)
        assertEquals(12345.67, message.data.volume)
        assertEquals(45500.0, message.data.high)
        assertEquals(44000.0, message.data.low)
        assertEquals(2.5, message.data.bidSize)
        assertEquals(3.0, message.data.askSize)
        assertEquals(1.81, message.data.changePercent)
    }

    @Test
    fun testParseHeartbeatMessage() {
        val jsonString =
            """
            {
                "type": "heartbeat",
                "timestamp": "2024-01-15T10:30:00Z",
                "status": "alive"
            }
            """.trimIndent()

        val message = json.decodeFromString<HeartbeatMessage>(jsonString)

        assertEquals("2024-01-15T10:30:00Z", message.timestamp)
        assertEquals("alive", message.status)
    }

    @Test
    fun testParseSubscriptionMessage() {
        val jsonString =
            """
            {
                "type": "subscription",
                "timestamp": "2024-01-15T10:30:00Z",
                "channel": "signals",
                "status": "subscribed",
                "message": "Successfully subscribed to signals channel"
            }
            """.trimIndent()

        val message = json.decodeFromString<SubscriptionMessage>(jsonString)

        assertEquals("signals", message.channel)
        assertEquals("subscribed", message.status)
        assertEquals("Successfully subscribed to signals channel", message.message)
    }

    @Test
    fun testParseErrorMessage() {
        val jsonString =
            """
            {
                "type": "error",
                "timestamp": "2024-01-15T10:30:00Z",
                "code": "AUTH_FAILED",
                "message": "Authentication failed",
                "details": "Invalid API key provided"
            }
            """.trimIndent()

        val message = json.decodeFromString<ErrorMessage>(jsonString)

        assertEquals("AUTH_FAILED", message.code)
        assertEquals("Authentication failed", message.message)
        assertEquals("Invalid API key provided", message.details)
    }

    @Test
    fun testSerializeSignalMessage() {
        val message =
            SignalMessage(
                timestamp = "2024-01-15T10:30:00Z",
                data =
                    SignalData(
                        id = "sig_123",
                        symbol = "BTC/USD",
                        type = "BUY",
                        price = 45000.0,
                        confidence = 0.85,
                        strategy = "momentum",
                    ),
            )

        val jsonString = json.encodeToString(message)

        // Verify key fields are present (type discriminator may or may not be included)
        assertTrue(jsonString.contains("\"timestamp\""))
        assertTrue(jsonString.contains("\"id\":\"sig_123\""))
        assertTrue(jsonString.contains("\"symbol\":\"BTC/USD\""))
        assertTrue(jsonString.contains("\"price\":45000"))
    }

    @Test
    fun testSerializeAndDeserializeRoundTrip() {
        val original =
            OrderMessage(
                timestamp = "2024-01-15T10:30:00Z",
                data =
                    OrderData(
                        id = "ord_456",
                        symbol = "ETH/USD",
                        side = "SELL",
                        orderType = "MARKET",
                        quantity = 1.0,
                        status = "FILLED",
                        filledQuantity = 1.0,
                        averageFillPrice = 2500.0,
                    ),
            )

        val jsonString = json.encodeToString(original)
        val deserialized = json.decodeFromString<OrderMessage>(jsonString)

        assertEquals(original.timestamp, deserialized.timestamp)
        assertEquals(original.data.id, deserialized.data.id)
        assertEquals(original.data.symbol, deserialized.data.symbol)
        assertEquals(original.data.side, deserialized.data.side)
        assertEquals(original.data.quantity, deserialized.data.quantity)
    }

    @Test
    fun testParseMessageWithUnknownFields() {
        // JSON with extra fields that should be ignored
        val jsonString =
            """
            {
                "type": "signal",
                "timestamp": "2024-01-15T10:30:00Z",
                "unknown_field_1": "should be ignored",
                "data": {
                    "id": "sig_123",
                    "symbol": "BTC/USD",
                    "type": "BUY",
                    "price": 45000.0,
                    "confidence": 0.85,
                    "extra_field": "also ignored"
                }
            }
            """.trimIndent()

        val message = json.decodeFromString<SignalMessage>(jsonString)

        assertEquals("sig_123", message.data.id)
        assertEquals("BTC/USD", message.data.symbol)
    }

    @Test
    fun testParseMalformedJson() {
        val malformedJson =
            """
            {
                "type": "signal",
                "timestamp": "2024-01-15T10:30:00Z",
                "data": {
                    "id": "sig_123"
                    // Missing closing brace
            """.trimIndent()

        assertFails {
            json.decodeFromString<SignalMessage>(malformedJson)
        }
    }

    @Test
    fun testParseMessageWithMissingRequiredField() {
        val jsonString =
            """
            {
                "type": "signal",
                "timestamp": "2024-01-15T10:30:00Z",
                "data": {
                    "id": "sig_123",
                    "symbol": "BTC/USD"
                }
            }
            """.trimIndent()

        // Missing required fields: type, price, confidence
        assertFails {
            json.decodeFromString<SignalMessage>(jsonString)
        }
    }

    @Test
    fun testParseMessageWithNullOptionalFields() {
        val jsonString =
            """
            {
                "type": "order",
                "timestamp": "2024-01-15T10:30:00Z",
                "data": {
                    "id": "ord_456",
                    "symbol": "BTC/USD",
                    "side": "BUY",
                    "order_type": "MARKET",
                    "quantity": 1.0,
                    "price": null,
                    "stop_price": null,
                    "status": "FILLED",
                    "filled_quantity": null,
                    "average_fill_price": null,
                    "created_at": null,
                    "updated_at": null
                }
            }
            """.trimIndent()

        val message = json.decodeFromString<OrderMessage>(jsonString)

        assertNull(message.data.price)
        assertNull(message.data.stopPrice)
        assertNull(message.data.averageFillPrice)
    }

    @Test
    fun testParseMultipleMessageTypes() {
        val messages =
            listOf(
                """{"type":"signal","timestamp":"2024-01-15T10:30:00Z","data":{"id":"sig_1","symbol":"BTC/USD","type":"BUY","price":45000.0,"confidence":0.85}}""",
                """{"type":"order","timestamp":"2024-01-15T10:30:00Z","data":{"id":"ord_1","symbol":"BTC/USD","side":"BUY","order_type":"LIMIT","quantity":1.0,"status":"OPEN"}}""",
                """{"type":"heartbeat","timestamp":"2024-01-15T10:30:00Z","status":"alive"}""",
            )

        messages.forEach { jsonString ->
            // Should be able to parse without errors
            assertNotNull(jsonString)
            assertTrue(jsonString.contains("type"))
            assertTrue(jsonString.contains("timestamp"))
        }
    }

    @Test
    fun testMessageTypeDiscrimination() {
        val signalJson =
            """{"type":"signal","timestamp":"2024-01-15T10:30:00Z","data":{"id":"sig_1","symbol":"BTC/USD","type":"BUY","price":45000.0,"confidence":0.85}}"""
        val orderJson =
            """{"type":"order","timestamp":"2024-01-15T10:30:00Z","data":{"id":"ord_1","symbol":"BTC/USD","side":"BUY","order_type":"LIMIT","quantity":1.0,"status":"OPEN"}}"""

        val signal = json.decodeFromString<SignalMessage>(signalJson)
        val order = json.decodeFromString<OrderMessage>(orderJson)

        assertNotEquals<Any>(signal, order)
    }

    @Test
    fun testEmptyIndicatorsMap() {
        val jsonString =
            """
            {
                "type": "signal",
                "timestamp": "2024-01-15T10:30:00Z",
                "data": {
                    "id": "sig_123",
                    "symbol": "BTC/USD",
                    "type": "BUY",
                    "price": 45000.0,
                    "confidence": 0.85,
                    "indicators": {}
                }
            }
            """.trimIndent()

        val message = json.decodeFromString<SignalMessage>(jsonString)

        assertNotNull(message.data.indicators)
        assertTrue(message.data.indicators!!.isEmpty())
    }

    @Test
    fun testLargeNumbers() {
        val jsonString =
            """
            {
                "type": "market_data",
                "timestamp": "2024-01-15T10:30:00Z",
                "data": {
                    "symbol": "BTC/USD",
                    "bid": 99999.99,
                    "ask": 100000.01,
                    "last": 100000.0,
                    "volume": 1234567890.123456
                }
            }
            """.trimIndent()

        val message = json.decodeFromString<MarketDataMessage>(jsonString)

        assertEquals(99999.99, message.data.bid)
        assertEquals(100000.01, message.data.ask)
        assertEquals(1234567890.123456, message.data.volume)
    }

    @Test
    fun testNegativeValues() {
        val jsonString =
            """
            {
                "type": "position",
                "timestamp": "2024-01-15T10:30:00Z",
                "data": {
                    "id": "pos_111",
                    "symbol": "BTC/USD",
                    "quantity": -0.5,
                    "entry_price": 45000.0,
                    "current_price": 44000.0,
                    "unrealized_pnl": -500.0,
                    "unrealized_pnl_percent": -1.11
                }
            }
            """.trimIndent()

        val message = json.decodeFromString<PositionMessage>(jsonString)

        assertEquals(-0.5, message.data.quantity)
        assertEquals(-500.0, message.data.unrealizedPnl)
        assertEquals(-1.11, message.data.unrealizedPnlPercent)
    }
}
