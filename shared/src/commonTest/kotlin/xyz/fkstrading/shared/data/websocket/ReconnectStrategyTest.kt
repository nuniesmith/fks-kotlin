package xyz.fkstrading.shared.data.websocket

import kotlin.test.*

/**
 * Unit tests for ReconnectStrategy.
 *
 * Tests the exponential backoff algorithm and reconnection logic.
 */
class ReconnectStrategyTest {
    @Test
    fun testDefaultConfiguration() {
        val strategy = ReconnectStrategy()
        val config = ReconnectConfig()

        assertEquals(1000, config.initialDelayMs, "Default initial delay should be 1000ms")
        assertEquals(60000, config.maxDelayMs, "Default max delay should be 60000ms")
        assertEquals(2.0, config.multiplier, "Default multiplier should be 2.0")
        assertNull(config.maxAttempts, "Default max attempts should be null (infinite)")
        assertEquals(30000, config.connectionTimeoutMs, "Default connection timeout should be 30000ms")
    }

    @Test
    fun testCustomConfiguration() {
        val config =
            ReconnectConfig(
                initialDelayMs = 500,
                maxDelayMs = 30000,
                multiplier = 3.0,
                maxAttempts = 5,
                connectionTimeoutMs = 15000,
            )
        val strategy = ReconnectStrategy(config)

        assertEquals(500, config.initialDelayMs)
        assertEquals(30000, config.maxDelayMs)
        assertEquals(3.0, config.multiplier)
        assertEquals(5, config.maxAttempts)
        assertEquals(15000, config.connectionTimeoutMs)
    }

    @Test
    fun testInitialAttemptNumber() {
        val strategy = ReconnectStrategy()

        assertEquals(0, strategy.getCurrentAttempt(), "Initial attempt should be 0")
    }

    @Test
    fun testFirstReconnectDelay() {
        val strategy = ReconnectStrategy()

        val delay = strategy.getNextDelay()

        assertEquals(1000, delay, "First reconnect delay should be 1000ms (initial delay)")
    }

    @Test
    fun testExponentialBackoffProgression() {
        val strategy = ReconnectStrategy()

        // First attempt (before recording)
        assertEquals(1000, strategy.getNextDelay())
        strategy.recordAttempt()

        // Second attempt
        assertEquals(2000, strategy.getNextDelay())
        strategy.recordAttempt()

        // Third attempt
        assertEquals(4000, strategy.getNextDelay())
        strategy.recordAttempt()

        // Fourth attempt
        assertEquals(8000, strategy.getNextDelay())
        strategy.recordAttempt()

        // Fifth attempt
        assertEquals(16000, strategy.getNextDelay())
        strategy.recordAttempt()

        // Sixth attempt
        assertEquals(32000, strategy.getNextDelay())
        strategy.recordAttempt()

        // Seventh attempt - should hit max delay
        assertEquals(60000, strategy.getNextDelay())
    }

    @Test
    fun testMaxDelayIsRespected() {
        val config =
            ReconnectConfig(
                initialDelayMs = 1000,
                maxDelayMs = 10000,
                multiplier = 2.0,
            )
        val strategy = ReconnectStrategy(config)

        // Record many attempts
        repeat(20) {
            strategy.recordAttempt()
        }

        // Delay should never exceed maxDelayMs
        val delay = strategy.getNextDelay()
        assertTrue(delay <= 10000, "Delay should not exceed max delay of 10000ms")
        assertEquals(10000, delay, "Delay should be capped at max delay")
    }

    @Test
    fun testShouldRetryWithInfiniteAttempts() {
        val strategy =
            ReconnectStrategy(
                ReconnectConfig(maxAttempts = null),
            )

        // Record 100 attempts
        repeat(100) {
            assertTrue(strategy.shouldRetry(), "Should always retry with infinite attempts")
            strategy.recordAttempt()
        }

        assertTrue(strategy.shouldRetry(), "Should still retry after 100 attempts")
    }

    @Test
    fun testShouldRetryWithMaxAttempts() {
        val strategy =
            ReconnectStrategy(
                ReconnectConfig(maxAttempts = 3),
            )

        // Attempt 1
        assertTrue(strategy.shouldRetry())
        strategy.recordAttempt()

        // Attempt 2
        assertTrue(strategy.shouldRetry())
        strategy.recordAttempt()

        // Attempt 3
        assertTrue(strategy.shouldRetry())
        strategy.recordAttempt()

        // Attempt 4 - should not retry
        assertFalse(strategy.shouldRetry(), "Should not retry after max attempts reached")
    }

    @Test
    fun testResetAfterSuccessfulConnection() {
        val strategy = ReconnectStrategy()

        // Record several failed attempts
        strategy.recordAttempt()
        strategy.recordAttempt()
        strategy.recordAttempt()

        assertEquals(3, strategy.getCurrentAttempt())

        // Reset after successful connection
        strategy.reset()

        assertEquals(0, strategy.getCurrentAttempt(), "Attempt count should reset to 0")
        assertEquals(1000, strategy.getNextDelay(), "Delay should reset to initial delay")
    }

    @Test
    fun testRecordAttemptIncrementsCounter() {
        val strategy = ReconnectStrategy()

        assertEquals(0, strategy.getCurrentAttempt())

        strategy.recordAttempt()
        assertEquals(1, strategy.getCurrentAttempt())

        strategy.recordAttempt()
        assertEquals(2, strategy.getCurrentAttempt())

        strategy.recordAttempt()
        assertEquals(3, strategy.getCurrentAttempt())
    }

    @Test
    fun testGetMaxAttemptsReturnsCorrectValue() {
        val strategyWithMax =
            ReconnectStrategy(
                ReconnectConfig(maxAttempts = 5),
            )
        assertEquals(5, strategyWithMax.getMaxAttempts())

        val strategyWithoutMax =
            ReconnectStrategy(
                ReconnectConfig(maxAttempts = null),
            )
        assertNull(strategyWithoutMax.getMaxAttempts())
    }

    @Test
    fun testIsMaxAttemptsReachedWithLimit() {
        val strategy =
            ReconnectStrategy(
                ReconnectConfig(maxAttempts = 3),
            )

        assertFalse(strategy.isMaxAttemptsReached())

        strategy.recordAttempt()
        assertFalse(strategy.isMaxAttemptsReached())

        strategy.recordAttempt()
        assertFalse(strategy.isMaxAttemptsReached())

        strategy.recordAttempt()
        assertTrue(strategy.isMaxAttemptsReached(), "Should reach max attempts after 3 attempts")
    }

    @Test
    fun testIsMaxAttemptsReachedWithInfinite() {
        val strategy =
            ReconnectStrategy(
                ReconnectConfig(maxAttempts = null),
            )

        repeat(100) {
            assertFalse(strategy.isMaxAttemptsReached(), "Should never reach max with infinite attempts")
            strategy.recordAttempt()
        }
    }

    @Test
    fun testGetStatusString() {
        val strategy =
            ReconnectStrategy(
                ReconnectConfig(maxAttempts = 5),
            )

        val status = strategy.getStatusString()
        assertTrue(status.contains("Attempt"), "Status should contain 'Attempt'")
        assertTrue(status.contains("delay"), "Status should contain 'delay'")
        assertTrue(status.contains("1000ms"), "Status should show initial delay")
    }

    @Test
    fun testCustomMultiplier() {
        val config =
            ReconnectConfig(
                initialDelayMs = 1000,
                maxDelayMs = 100000,
                multiplier = 3.0,
            )
        val strategy = ReconnectStrategy(config)

        assertEquals(1000, strategy.getNextDelay())
        strategy.recordAttempt()

        assertEquals(3000, strategy.getNextDelay())
        strategy.recordAttempt()

        assertEquals(9000, strategy.getNextDelay())
        strategy.recordAttempt()

        assertEquals(27000, strategy.getNextDelay())
    }

    @Test
    fun testConfigValidationInitialDelayPositive() {
        assertFails {
            ReconnectConfig(initialDelayMs = 0)
        }

        assertFails {
            ReconnectConfig(initialDelayMs = -1000)
        }
    }

    @Test
    fun testConfigValidationMaxDelayGreaterThanInitial() {
        assertFails {
            ReconnectConfig(
                initialDelayMs = 5000,
                maxDelayMs = 1000,
            )
        }
    }

    @Test
    fun testConfigValidationMultiplierGreaterThanOne() {
        assertFails {
            ReconnectConfig(multiplier = 1.0)
        }

        assertFails {
            ReconnectConfig(multiplier = 0.5)
        }
    }

    @Test
    fun testConfigValidationMaxAttemptsPositiveOrNull() {
        // Null is valid
        val configNull = ReconnectConfig(maxAttempts = null)
        assertNull(configNull.maxAttempts)

        // Positive is valid
        val configPositive = ReconnectConfig(maxAttempts = 1)
        assertEquals(1, configPositive.maxAttempts)

        // Zero or negative should fail
        assertFails {
            ReconnectConfig(maxAttempts = 0)
        }

        assertFails {
            ReconnectConfig(maxAttempts = -5)
        }
    }

    @Test
    fun testConfigValidationConnectionTimeoutPositive() {
        assertFails {
            ReconnectConfig(connectionTimeoutMs = 0)
        }

        assertFails {
            ReconnectConfig(connectionTimeoutMs = -1000)
        }
    }

    @Test
    fun testReconnectScenarioSimulation() {
        // Simulate a real reconnection scenario
        val strategy =
            ReconnectStrategy(
                ReconnectConfig(
                    initialDelayMs = 1000,
                    maxDelayMs = 30000,
                    multiplier = 2.0,
                    maxAttempts = 5,
                ),
            )

        val delays = mutableListOf<Long>()

        // First connection attempt fails
        assertTrue(strategy.shouldRetry())
        delays.add(strategy.getNextDelay())
        strategy.recordAttempt()

        // Second attempt fails
        assertTrue(strategy.shouldRetry())
        delays.add(strategy.getNextDelay())
        strategy.recordAttempt()

        // Third attempt succeeds - reset
        strategy.reset()

        // Connection lost again - should start from beginning
        assertEquals(0, strategy.getCurrentAttempt())
        assertEquals(1000, strategy.getNextDelay())

        // Verify exponential backoff worked in first sequence
        assertEquals(1000, delays[0])
        assertEquals(2000, delays[1])
    }

    @Test
    fun testZeroInitialAttemptMeansNoRetriesYet() {
        val strategy = ReconnectStrategy()

        assertEquals(0, strategy.getCurrentAttempt())
        assertTrue(strategy.shouldRetry(), "Should be able to retry when no attempts made yet")
    }

    @Test
    fun testLargeNumberOfRetries() {
        val strategy =
            ReconnectStrategy(
                ReconnectConfig(
                    initialDelayMs = 100,
                    maxDelayMs = 60000,
                    multiplier = 2.0,
                    maxAttempts = null,
                ),
            )

        // Simulate 50 retries
        repeat(50) {
            assertTrue(strategy.shouldRetry())
            val delay = strategy.getNextDelay()
            assertTrue(delay <= 60000, "Delay should never exceed max")
            strategy.recordAttempt()
        }

        // Should still be able to retry
        assertTrue(strategy.shouldRetry())
        assertEquals(50, strategy.getCurrentAttempt())
    }
}
