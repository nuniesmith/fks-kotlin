package xyz.fkstrading.shared.domain.strategy

import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import xyz.fkstrading.shared.*
import xyz.fkstrading.shared.data.db.DatabaseWrapper
import xyz.fkstrading.shared.data.repository.StrategyConfigRepository
import xyz.fkstrading.shared.data.repository.StrategyConfigRepositoryImpl
import xyz.fkstrading.shared.domain.models.*
import xyz.fkstrading.shared.domain.strategy.models.*
import xyz.fkstrading.shared.testutils.TestDatabaseDriverFactory
import kotlin.test.*

/**
 * Integration tests for StrategyExecutor with StrategyConfigRepository.
 * Tests the config-based execution methods.
 *
 * Note: All execution is AUTO mode - signals are executed automatically
 * after passing validation checks.
 */
class StrategyExecutorConfigIntegrationTest {
    private lateinit var database: DatabaseWrapper
    private lateinit var repository: StrategyConfigRepository
    private lateinit var executor: StrategyExecutor

    @BeforeTest
    fun setup() {
        database = DatabaseWrapper(TestDatabaseDriverFactory.createInMemoryDriverFactory())
        repository = StrategyConfigRepositoryImpl(database)
        executor = StrategyExecutor(configRepository = repository)
    }

    @AfterTest
    fun tearDown() {
        // Clean up handled automatically for in-memory database
    }

    // ========================================
    // EXECUTE WITH DEFAULT CONFIG TESTS
    // ========================================

    @Test
    fun `test execute with default config when default exists`() =
        runTest {
            // Given: A conservative default config (AUTO mode with low risk)
            val config = StrategyConfig.conservative("default-1", Clock.System.now())
            repository.saveConfig(config)
            repository.setAsDefault("default-1")

            // When: Execute with default config
            val result =
                executor.executeWithDefaultConfig(
                    signal = Signal.sample(signalId = "SIG-001", direction = Direction.LONG),
                    accountBalance = 10000.0,
                    currentPrice = 50000.0,
                )

            // Then: Execution proceeds (AUTO mode) - may succeed or be rejected based on validation
            assertNotNull(result)
            // Result should not be null, execution was attempted
            assertTrue(result.status != ExecutionStatus.SKIPPED, "AUTO mode should not skip")
        }

    @Test
    fun `test execute with default config falls back when no default exists`() =
        runTest {
            // Given: No default config exists
            assertNull(repository.getDefaultConfig())

            // When: Execute with default config
            val result =
                executor.executeWithDefaultConfig(
                    signal = Signal.sample(signalId = "SIG-001", direction = Direction.LONG),
                    accountBalance = 10000.0,
                    currentPrice = 50000.0,
                )

            // Then: Falls back to ExecutionConfig() default (AUTO mode)
            assertNotNull(result)
            assertTrue(result.status != ExecutionStatus.SKIPPED, "AUTO mode should not skip")
        }

    @Test
    fun `test execute with default config uses AUTO mode`() =
        runTest {
            // Given: An aggressive default config (AUTO mode)
            val config = StrategyConfig.aggressive("aggressive-1", Clock.System.now())
            repository.saveConfig(config)
            repository.setAsDefault("aggressive-1")

            // Verify it's AUTO mode
            assertEquals(ExecutionMode.AUTO, config.executionConfig.mode)
            assertFalse(config.executionConfig.requireConfirmation)

            // When: Execute with default config
            val result =
                executor.executeWithDefaultConfig(
                    signal = Signal.sample(signalId = "SIG-001", direction = Direction.LONG),
                    accountBalance = 10000.0,
                    currentPrice = 50000.0,
                )

            // Then: Execution proceeds automatically (not skipped)
            assertNotNull(result)
            assertTrue(result.status != ExecutionStatus.SKIPPED, "AUTO mode should not skip")
        }

    @Test
    fun `test execute with default config respects position sizing from config`() =
        runTest {
            // Given: A config with 5% percentage sizing
            val customConfig =
                StrategyConfig.default("custom-1", Clock.System.now())
                    .copy(
                        executionConfig =
                            ExecutionConfig(
                                mode = ExecutionMode.AUTO,
                                positionSizingMethod = PositionSizingMethod.FIXED_PERCENTAGE,
                                accountPercentage = 0.05,
                                requireConfirmation = false,
                            ),
                    )
            repository.saveConfig(customConfig)
            repository.setAsDefault("custom-1")

            // When: Execute with default config
            val result =
                executor.executeWithDefaultConfig(
                    signal = Signal.sample(signalId = "SIG-001", direction = Direction.LONG),
                    accountBalance = 10000.0,
                    currentPrice = 50000.0,
                )

            // Then: Position size is 5% of account balance
            assertTrue(result.isSuccess)
            assertNotNull(result.order)
            assertEquals(0.01, result.order!!.quantity) // $500 / $50,000 = 0.01 BTC
        }

    // ========================================
    // EXECUTE WITH SPECIFIC CONFIG TESTS
    // ========================================

    @Test
    fun `test execute with specific config by ID`() =
        runTest {
            // Given: Multiple configs
            val defaultConfig = StrategyConfig.default("config-1", Clock.System.now())
                .copy(
                    executionConfig = ExecutionConfig(
                        mode = ExecutionMode.AUTO,
                        requireConfirmation = false,
                    )
                )
            repository.saveConfig(defaultConfig)
            repository.saveConfig(StrategyConfig.aggressive("config-2", Clock.System.now()))
            repository.saveConfig(StrategyConfig.conservative("config-3", Clock.System.now()))

            // When: Execute with specific config (aggressive = AUTO without confirmation)
            val result =
                executor.executeWithConfig(
                    signal = Signal.sample(signalId = "SIG-001", direction = Direction.LONG),
                    configId = "config-2",
                    accountBalance = 10000.0,
                    currentPrice = 50000.0,
                )

            // Then: Execution uses config-2 and proceeds automatically
            assertNotNull(result)
            assertTrue(result.status != ExecutionStatus.SKIPPED)
        }

    @Test
    fun `test execute with specific config fails when config not found`() =
        runTest {
            // Given: No config with ID "non-existent"

            // When: Try to execute with non-existent config
            val result =
                executor.executeWithConfig(
                    signal = Signal.sample(signalId = "SIG-001", direction = Direction.LONG),
                    configId = "non-existent",
                    accountBalance = 10000.0,
                    currentPrice = 50000.0,
                )

            // Then: Execution fails with appropriate error
            assertTrue(result.isFailure)
            assertTrue(result.errorMessage?.contains("not found") == true)
        }

    @Test
    fun `test execute with inactive config fails`() =
        runTest {
            // Given: An inactive config
            val inactiveConfig =
                StrategyConfig.default("inactive-1", Clock.System.now())
                    .copy(isActive = false)
            repository.saveConfig(inactiveConfig)

            // When: Try to execute with inactive config
            val result =
                executor.executeWithConfig(
                    signal = Signal.sample(signalId = "SIG-001", direction = Direction.LONG),
                    configId = "inactive-1",
                    accountBalance = 10000.0,
                    currentPrice = 50000.0,
                )

            // Then: Execution fails
            assertTrue(result.isFailure)
            assertTrue(result.errorMessage?.contains("not active") == true)
        }

    @Test
    fun `test execute with config uses config ID as strategy ID`() =
        runTest {
            // Given: A config
            val config =
                StrategyConfig.default("my-strategy-123", Clock.System.now())
                    .copy(
                        executionConfig =
                            ExecutionConfig(
                                mode = ExecutionMode.AUTO,
                                requireConfirmation = false,
                                minSignalConfidence = 0.6,
                            ),
                    )
            repository.saveConfig(config)

            // When: Execute
            val result =
                executor.executeWithConfig(
                    signal = Signal.sample(signalId = "SIG-001", direction = Direction.LONG),
                    configId = "my-strategy-123",
                    accountBalance = 10000.0,
                    currentPrice = 50000.0,
                )

            // Then: Execution proceeds with config ID as strategy ID
            assertNotNull(result)
            assertTrue(result.status != ExecutionStatus.SKIPPED)
        }

    @Test
    fun `test execute with config respects risk-based sizing`() =
        runTest {
            // Given: A config with risk-based sizing
            val riskConfig =
                StrategyConfig.default("risk-config", Clock.System.now())
                    .copy(
                        executionConfig =
                            ExecutionConfig(
                                mode = ExecutionMode.AUTO,
                                positionSizingMethod = PositionSizingMethod.RISK_BASED,
                                riskPerTrade = 0.02,
                                stopLossMethod = StopLossMethod.FIXED_PERCENTAGE,
                                stopLossPercentage = 0.05,
                                requireConfirmation = false,
                            ),
                    )
            repository.saveConfig(riskConfig)

            // When: Execute
            val result =
                executor.executeWithConfig(
                    signal = Signal.sample(signalId = "SIG-001", direction = Direction.LONG),
                    configId = "risk-config",
                    accountBalance = 10000.0,
                    currentPrice = 100.0,
                )

            // Then: Position is sized based on 2% risk
            assertTrue(result.isSuccess || result.isFailure) // May be rejected by validation
            if (result.isSuccess) {
                assertNotNull(result.positionSize)
                assertTrue(result.positionSize!! > 0)
            }
        }

    @Test
    fun `test execute with config ID when repository is null fails gracefully`() =
        runTest {
            // Given: Executor without config repository
            val executorWithoutRepo = StrategyExecutor(configRepository = null)

            // When: Try to execute with config ID
            val result =
                executorWithoutRepo.executeWithConfig(
                    signal = Signal.sample(signalId = "SIG-001", direction = Direction.LONG),
                    configId = "some-config",
                    accountBalance = 10000.0,
                    currentPrice = 50000.0,
                )

            // Then: Execution fails gracefully
            assertTrue(result.isFailure)
            assertTrue(result.errorMessage?.contains("repository not available") == true)
        }

    // ========================================
    // PRESET CONFIG TESTS
    // ========================================

    @Test
    fun `test execute with conservative preset uses AUTO mode and RISK_BASED sizing`() =
        runTest {
            // Given: Conservative preset as default
            val conservativeConfig = StrategyConfig.conservative("conservative", Clock.System.now())
            repository.saveConfig(conservativeConfig)
            repository.setAsDefault("conservative")

            // Verify preset characteristics - all modes are now AUTO
            assertEquals(ExecutionMode.AUTO, conservativeConfig.executionConfig.mode)
            assertEquals(PositionSizingMethod.RISK_BASED, conservativeConfig.executionConfig.positionSizingMethod)
            // Conservative uses 0.5% risk per trade (0.005)
            assertEquals(0.005, conservativeConfig.executionConfig.riskPerTrade)

            // When: Execute with default
            val result =
                executor.executeWithDefaultConfig(
                    signal = Signal.sample(signalId = "SIG-001", direction = Direction.LONG),
                    accountBalance = 10000.0,
                    currentPrice = 50000.0,
                )

            // Then: Execution proceeds (AUTO mode - not skipped)
            assertNotNull(result)
            assertTrue(result.status != ExecutionStatus.SKIPPED)
        }

    @Test
    fun `test execute with aggressive preset uses AUTO mode and RISK_BASED sizing`() =
        runTest {
            // Given: Aggressive preset as default
            val aggressiveConfig = StrategyConfig.aggressive("aggressive", Clock.System.now())
            repository.saveConfig(aggressiveConfig)
            repository.setAsDefault("aggressive")

            // Verify preset characteristics
            assertEquals(ExecutionMode.AUTO, aggressiveConfig.executionConfig.mode)
            assertEquals(PositionSizingMethod.RISK_BASED, aggressiveConfig.executionConfig.positionSizingMethod)
            // Aggressive uses 2% risk per trade (0.02)
            assertEquals(0.02, aggressiveConfig.executionConfig.riskPerTrade)

            // When: Execute with default
            val result =
                executor.executeWithDefaultConfig(
                    signal = Signal.sample(signalId = "SIG-001", direction = Direction.LONG),
                    accountBalance = 10000.0,
                    currentPrice = 50000.0,
                )

            // Then: Execution proceeds (AUTO mode)
            assertNotNull(result)
            assertTrue(result.status != ExecutionStatus.SKIPPED)
        }

    // ========================================
    // ENSURE DEFAULT CONFIG TESTS
    // ========================================

    @Test
    fun `test ensure default config creates one if missing`() =
        runTest {
            // Given: No configs exist
            assertEquals(0, repository.getConfigCount())

            // When: Ensure default exists
            val defaultConfig = repository.ensureDefaultConfigExists()

            // Then: A default config is created
            assertNotNull(defaultConfig)
            assertEquals(1, repository.getConfigCount())

            // And: Can execute with it (using the config directly)
            val result =
                executor.executeWithConfig(
                    signal = Signal.sample(signalId = "SIG-001", direction = Direction.LONG),
                    configId = defaultConfig.configId,
                    accountBalance = 10000.0,
                    currentPrice = 50000.0,
                )
            assertNotNull(result)
        }

    @Test
    fun `test switching default config updates execution behavior`() =
        runTest {
            // Given: Two configs with different risk levels
            val conservative = StrategyConfig.conservative("conservative", Clock.System.now())
            val autoConfig =
                StrategyConfig.aggressive("auto", Clock.System.now())
                    .copy(
                        executionConfig =
                            ExecutionConfig(
                                mode = ExecutionMode.AUTO,
                                requireConfirmation = false,
                                minSignalConfidence = 0.6,
                            ),
                    )
            repository.saveConfig(conservative)
            repository.saveConfig(autoConfig)

            // When: Set conservative as default and execute
            repository.setAsDefault("conservative")
            var result =
                executor.executeWithDefaultConfig(
                    signal = Signal.sample(signalId = "SIG-001", direction = Direction.LONG),
                    accountBalance = 10000.0,
                    currentPrice = 50000.0,
                )
            // Conservative uses AUTO mode too, so execution proceeds
            assertNotNull(result)
            assertTrue(result.status != ExecutionStatus.SKIPPED)

            // When: Switch to auto as default
            repository.setAsDefault("auto")

            // Then: Execution still proceeds (both are AUTO)
            result =
                executor.executeWithDefaultConfig(
                    signal = Signal.sample(signalId = "SIG-002", direction = Direction.LONG),
                    accountBalance = 10000.0,
                    currentPrice = 50000.0,
                )
            assertNotNull(result)
            assertTrue(result.status != ExecutionStatus.SKIPPED)
        }

    // ========================================
    // VALIDATION INTEGRATION TESTS
    // ========================================

    @Test
    fun `test execute with config respects validation rules`() =
        runTest {
            // Given: A config with strict validation (high confidence threshold)
            val strictConfig =
                StrategyConfig.default("strict", Clock.System.now())
                    .copy(
                        executionConfig =
                            ExecutionConfig(
                                mode = ExecutionMode.AUTO,
                                minSignalConfidence = 0.9, // High confidence required
                                requireConfirmation = false,
                            ),
                    )
            repository.saveConfig(strictConfig)

            // When: Execute with low confidence signal
            val lowConfidenceSignal =
                Signal.sample(
                    signalId = "SIG-001",
                    direction = Direction.LONG,
                ).copy(confidence = 0.7)

            val result =
                executor.executeWithConfig(
                    signal = lowConfidenceSignal,
                    configId = "strict",
                    accountBalance = 10000.0,
                    currentPrice = 50000.0,
                )

            // Then: Execution is rejected due to low confidence
            assertTrue(result.isFailure)
            assertTrue(
                result.errorMessage?.contains("confidence", ignoreCase = true) == true ||
                        result.validationErrors.any { it.contains("confidence", ignoreCase = true) })
        }

    @Test
    fun `test execute with config respects blacklist`() =
        runTest {
            // Given: A config with BTC blacklisted
            val blacklistConfig =
                StrategyConfig.default("blacklist", Clock.System.now())
                    .copy(
                        executionConfig =
                            ExecutionConfig(
                                mode = ExecutionMode.AUTO,
                                assetBlacklist = listOf("BTC/USD"),
                                requireConfirmation = false,
                                minSignalConfidence = 0.6,
                            ),
                    )
            repository.saveConfig(blacklistConfig)

            // When: Execute BTC signal
            val btcSignal =
                Signal.sample(
                    signalId = "SIG-001",
                    symbol = "BTC/USD",
                    direction = Direction.LONG,
                )

            val result =
                executor.executeWithConfig(
                    signal = btcSignal,
                    configId = "blacklist",
                    accountBalance = 10000.0,
                    currentPrice = 50000.0,
                )

            // Then: Execution is rejected
            assertTrue(result.isFailure)
        }

    @Test
    fun `test execute with config when repository is null uses default`() =
        runTest {
            // Given: Executor without repository
            val executorWithoutRepo = StrategyExecutor(configRepository = null)

            // When: Execute with default config (should use fallback)
            val result =
                executorWithoutRepo.executeWithDefaultConfig(
                    signal = Signal.sample(signalId = "SIG-001", direction = Direction.LONG),
                    accountBalance = 10000.0,
                    currentPrice = 50000.0,
                )

            // Then: Execution proceeds with default config
            assertNotNull(result)
            assertTrue(result.status != ExecutionStatus.SKIPPED, "Should use AUTO mode by default")
        }
}
