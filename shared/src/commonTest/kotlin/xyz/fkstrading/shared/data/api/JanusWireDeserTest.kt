package xyz.fkstrading.shared.data.api

import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import xyz.fkstrading.shared.domain.models.OrderSide
import xyz.fkstrading.shared.domain.models.PositionStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Wire-contract smoke test for the janus read reconcile.
 *
 * Deserializes REAL janus response bodies (captured 2026-07-01 from a running janus in paper mode,
 * forward module started) into the fks-kotlin read DTOs and asserts the fields populate with the
 * real values — proving the snake_case `@SerialName` mapping is correct and nothing silently
 * defaults. Assertions deliberately use values that differ from the Kotlin defaults (uptime 3042,
 * total_value 100000, active_modules 5, …) so a missing/wrong `@SerialName` would fail, not pass.
 *
 * Live paper data was empty (0 signals, 0 positions), so the populated shapes — a position with
 * side "Long"/"Short" (janus `PositionDto`, services/forward/src/api/risk_rest.rs) and non-object
 * raw signal elements (`Vec<serde_json::Value>`) — are covered with synthetic bodies matching the
 * janus struct/handler source.
 */
class JanusWireDeserTest {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Test
    fun health_real_body_populates_all_fields() {
        val body = """{"status":"healthy","uptime_seconds":3042,"signals_generated":0,"signals_persisted":0,"modules":[{"name":"api","healthy":true,"message":"starting"},{"name":"cns","healthy":true,"message":"running"},{"name":"data","healthy":true,"message":"live: 10 assets"},{"name":"backward","healthy":true,"message":"running"},{"name":"forward","healthy":true,"message":"running"}],"shutdown_requested":false,"service_state":"running"}"""
        val h = json.decodeFromString<JanusHealthResponse>(body)
        assertEquals("healthy", h.status)
        assertEquals(3042L, h.uptimeSeconds) // @SerialName("uptime_seconds") — not the default 0
        assertEquals(5, h.modules.size)
        assertEquals("api", h.modules[0].name)
        assertTrue(h.modules[0].healthy)
        assertEquals("running", h.serviceState) // @SerialName("service_state")
        assertEquals(false, h.shutdownRequested)
    }

    @Test
    fun signalsLatest_empty_and_populated_including_nonObject_element() {
        val empty = json.decodeFromString<SignalsLatestResponse>("""{"count":0,"signals":[]}""")
        assertEquals(0, empty.count)
        assertTrue(empty.signals.isEmpty())

        // Raw Redis values are Vec<serde_json::Value>: elements may be objects OR non-objects.
        // List<JsonElement> must accept a mix without throwing the whole response (regression guard
        // for the earlier List<JsonObject> which would throw on any non-object element).
        val pop = json.decodeFromString<SignalsLatestResponse>(
            """{"count":3,"signals":[{"symbol":"BTCUSDT","dir":"long"},"raw-string-blob",42]}""",
        )
        assertEquals(3, pop.count)
        assertEquals(3, pop.signals.size)
        assertTrue(pop.signals[0].jsonObject.containsKey("symbol"))
    }

    @Test
    fun dashboardOverview_body_populates_all_fields() {
        val body = """{"active_modules":5,"healthy_modules":5,"module_status":[{"healthy":true,"message":"starting","name":"api"}],"performance":{"avg_latency_ms":1.5,"error_rate":0.0,"persistence_rate":0.0,"signal_generation_rate":0.0},"recent_signals":[],"total_persisted":0,"total_signals":7,"uptime_seconds":3042}"""
        val d = json.decodeFromString<DashboardOverviewResponse>(body)
        assertEquals(5, d.activeModules) // @SerialName("active_modules")
        assertEquals(5, d.healthyModules)
        assertEquals(3042L, d.uptimeSeconds)
        assertEquals(7L, d.totalSignals) // @SerialName("total_signals")
        assertEquals(1, d.moduleStatus.size)
        assertEquals("api", d.moduleStatus[0].name)
        assertEquals(1.5, d.performance.avgLatencyMs) // nested @SerialName("avg_latency_ms")
    }

    @Test
    fun riskPortfolio_empty_real_body_populates() {
        val body = """{"positions":{},"daily_pnl":0.0,"total_value":100000.0,"total_exposure":-0.0,"position_count":0,"exposure_percentage":-0.0}"""
        val p = json.decodeFromString<PortfolioStateDto>(body)
        assertTrue(p.positions.isEmpty())
        assertEquals(100000.0, p.totalValue) // @SerialName("total_value") — not the default 0.0
        assertEquals(0, p.positionCount)
    }

    @Test
    fun riskPortfolio_populated_position_maps_to_domain_Position() {
        // Synthetic PositionDto matching janus risk_rest.rs (side emitted as "Long"/"Short").
        val body = """{"positions":{"BTCUSDT":{"symbol":"BTCUSDT","entry_price":42000.5,"quantity":0.25,"side":"Long","stop_loss":41000.0,"take_profit":45000.0,"position_value":10500.12,"risk_amount":250.0}},"daily_pnl":12.3,"total_value":100000.0,"total_exposure":10500.12,"position_count":1,"exposure_percentage":10.5}"""
        val p = json.decodeFromString<PortfolioStateDto>(body)
        assertEquals(1, p.positionCount)
        val dto = p.positions.getValue("BTCUSDT")
        assertEquals(42000.5, dto.entryPrice) // @SerialName("entry_price")
        assertEquals("Long", dto.side)
        assertEquals(41000.0, dto.stopLoss) // @SerialName("stop_loss")
        assertEquals(10500.12, dto.positionValue) // @SerialName("position_value")

        val pos = dto.toPosition("BTCUSDT")
        assertEquals("BTCUSDT", pos.positionId) // portfolio map key -> positionId
        assertEquals(OrderSide.BUY, pos.side) // "Long" -> BUY
        assertEquals(42000.5, pos.entryPrice)
        assertEquals(42000.5, pos.currentPrice) // placeholder = entry (janus has no mark price here)
        assertEquals(PositionStatus.OPEN, pos.status)
        assertEquals(Instant.DISTANT_PAST, pos.openedAt)
        assertEquals(10500.12, pos.value)

        assertEquals(OrderSide.SELL, dto.copy(side = "Short").toPosition("X").side) // "Short" -> SELL
    }
}
