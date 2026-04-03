package xyz.fkstrading.shared.data.repository

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import xyz.fkstrading.shared.*
import xyz.fkstrading.shared.data.db.DatabaseWrapper
import xyz.fkstrading.shared.domain.models.*
import xyz.fkstrading.shared.domain.strategy.models.ExecutionMode
import xyz.fkstrading.shared.domain.strategy.models.PositionSizingMethod
import xyz.fkstrading.shared.testutils.TestDatabaseDriverFactory
import kotlin.test.*

/**
 * Unit tests for StrategyConfigRepository.
 */
class StrategyConfigRepositoryTest {
    private lateinit var database: DatabaseWrapper
    private lateinit var repository: StrategyConfigRepositoryImpl

    @BeforeTest
    fun setup() {
        // Create in-memory database for testing
        database = DatabaseWrapper(TestDatabaseDriverFactory.createInMemoryDriverFactory())
        repository = StrategyConfigRepositoryImpl(database)
    }

    @AfterTest
    fun tearDown() {
        // Clean up is handled automatically for in-memory database
    }

    @Test
    fun `test save and retrieve config`() =
        runTest {
            // Given: A strategy config
            val config =
                StrategyConfig.default(
                    configId = "test-001",
                    timestamp = Clock.System.now(),
                )

            // When: Save the config
            val result = repository.saveConfig(config)

            // Then: Save succeeds
            assertTrue(result.isSuccess)

            // And: Config can be retrieved
            val retrieved = repository.getConfigById("test-001")
            assertNotNull(retrieved)
            assertEquals("test-001", retrieved.configId)
            assertEquals("Default Strategy", retrieved.name)
        }

    @Test
    fun `test save config validates input`() =
        runTest {
            // Given: A config with blank name
            val config =
                StrategyConfig.default(
                    configId = "test-002",
                    timestamp = Clock.System.now(),
                    name = "",
                )

            // When: Try to save
            val result = repository.saveConfig(config)

            // Then: Save fails with validation error
            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull()?.message?.contains("name cannot be blank") == true)
        }

    @Test
    fun `test observe config by ID`() =
        runTest {
            // Given: A saved config
            val config =
                StrategyConfig.conservative(
                    configId = "test-003",
                    timestamp = Clock.System.now(),
                )
            repository.saveConfig(config)

            // When: Observe the config
            val observed = repository.observeConfig("test-003").first()

            // Then: Config is observed
            assertNotNull(observed)
            assertEquals("test-003", observed.configId)
            assertEquals("Conservative Strategy", observed.name)
        }

    @Test
    fun `test get all configs`() =
        runTest {
            // Given: Multiple configs
            repository.saveConfig(StrategyConfig.default("config-1", Clock.System.now()))
            repository.saveConfig(StrategyConfig.conservative("config-2", Clock.System.now()))
            repository.saveConfig(StrategyConfig.aggressive("config-3", Clock.System.now()))

            // When: Get all configs
            val configs = repository.getAllConfigs()

            // Then: All configs are retrieved
            assertEquals(3, configs.size)
        }

    @Test
    fun `test get active configs only`() =
        runTest {
            // Given: Active and inactive configs
            val active1 = StrategyConfig.default("active-1", Clock.System.now())
            val active2 = StrategyConfig.conservative("active-2", Clock.System.now())
            val inactive =
                StrategyConfig.aggressive("inactive-1", Clock.System.now())
                    .copy(isActive = false)

            repository.saveConfig(active1)
            repository.saveConfig(active2)
            repository.saveConfig(inactive)

            // When: Get active configs
            val activeConfigs = repository.getActiveConfigs()

            // Then: Only active configs are retrieved
            assertEquals(2, activeConfigs.size)
            assertTrue(activeConfigs.all { it.isActive })
        }

    @Test
    fun `test set config as default`() =
        runTest {
            // Given: Multiple configs
            val config1 = StrategyConfig.default("config-1", Clock.System.now())
            val config2 = StrategyConfig.conservative("config-2", Clock.System.now())
            repository.saveConfig(config1)
            repository.saveConfig(config2)

            // When: Set config-2 as default
            val result = repository.setAsDefault("config-2")

            // Then: Operation succeeds
            assertTrue(result.isSuccess)

            // And: config-2 is now default
            val defaultConfig = repository.getDefaultConfig()
            assertNotNull(defaultConfig)
            assertEquals("config-2", defaultConfig.configId)
            assertTrue(defaultConfig.isDefault)

            // And: config-1 is no longer default
            val config1Updated = repository.getConfigById("config-1")
            assertNotNull(config1Updated)
            assertFalse(config1Updated.isDefault)
        }

    @Test
    fun `test set as default fails for non-existent config`() =
        runTest {
            // When: Try to set non-existent config as default
            val result = repository.setAsDefault("non-existent")

            // Then: Operation fails
            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull()?.message?.contains("not found") == true)
        }

    @Test
    fun `test update active status`() =
        runTest {
            // Given: An active config
            val config = StrategyConfig.default("test-004", Clock.System.now())
            repository.saveConfig(config)

            // When: Deactivate it
            val result = repository.updateActiveStatus("test-004", false)

            // Then: Operation succeeds
            assertTrue(result.isSuccess)

            // And: Config is now inactive
            val updated = repository.getConfigById("test-004")
            assertNotNull(updated)
            assertFalse(updated.isActive)
        }

    @Test
    fun `test delete config`() =
        runTest {
            // Given: A saved config
            val config = StrategyConfig.default("test-005", Clock.System.now())
            repository.saveConfig(config)

            // Verify it exists
            assertNotNull(repository.getConfigById("test-005"))

            // When: Delete the config
            val result = repository.deleteConfig("test-005")

            // Then: Operation succeeds
            assertTrue(result.isSuccess)

            // And: Config no longer exists
            assertNull(repository.getConfigById("test-005"))
        }

    @Test
    fun `test delete all configs`() =
        runTest {
            // Given: Multiple configs
            repository.saveConfig(StrategyConfig.default("config-1", Clock.System.now()))
            repository.saveConfig(StrategyConfig.conservative("config-2", Clock.System.now()))
            repository.saveConfig(StrategyConfig.aggressive("config-3", Clock.System.now()))

            // Verify they exist
            assertEquals(3, repository.getConfigCount())

            // When: Delete all
            val result = repository.deleteAllConfigs()

            // Then: Operation succeeds
            assertTrue(result.isSuccess)

            // And: No configs remain
            assertEquals(0, repository.getConfigCount())
        }

    @Test
    fun `test search configs by name`() =
        runTest {
            // Given: Configs with different names
            repository.saveConfig(StrategyConfig.default("config-1", Clock.System.now(), "Conservative Day Trading"))
            repository.saveConfig(
                StrategyConfig.conservative(
                    "config-2",
                    Clock.System.now(),
                    "Aggressive Swing Trading"
                )
            )
            repository.saveConfig(StrategyConfig.aggressive("config-3", Clock.System.now(), "Scalping Strategy"))

            // When: Search for "Trading"
            val results = repository.searchConfigsByName("Trading")

            // Then: Matching configs are returned
            assertEquals(2, results.size)
            assertTrue(results.all { it.name.contains("Trading") })
        }

    @Test
    fun `test get config count`() =
        runTest {
            // Given: Some configs
            repository.saveConfig(StrategyConfig.default("config-1", Clock.System.now()))
            repository.saveConfig(StrategyConfig.conservative("config-2", Clock.System.now()))

            // When: Get count
            val count = repository.getConfigCount()

            // Then: Count is correct
            assertEquals(2, count)
        }

    @Test
    fun `test get active config count`() =
        runTest {
            // Given: Active and inactive configs
            repository.saveConfig(StrategyConfig.default("active-1", Clock.System.now()))
            repository.saveConfig(StrategyConfig.conservative("active-2", Clock.System.now()))
            repository.saveConfig(StrategyConfig.aggressive("inactive-1", Clock.System.now()).copy(isActive = false))

            // When: Get active count
            val count = repository.getActiveConfigCount()

            // Then: Count is correct
            assertEquals(2, count)
        }

    @Test
    fun `test observe configs updates on changes`() =
        runTest {
            // Given: Observing all configs
            val flow = repository.observeAllConfigs()

            // When: Save a config
            repository.saveConfig(StrategyConfig.default("test-006", Clock.System.now()))

            // Then: Flow emits the updated list
            val configs = flow.first()
            assertEquals(1, configs.size)
            assertEquals("test-006", configs[0].configId)
        }

    @Test
    fun `test get config by name`() =
        runTest {
            // Given: A config with specific name
            repository.saveConfig(StrategyConfig.default("config-1", Clock.System.now(), "My Custom Strategy"))

            // When: Get by name
            val config = repository.getConfigByName("My Custom Strategy")

            // Then: Config is found
            assertNotNull(config)
            assertEquals("config-1", config.configId)
            assertEquals("My Custom Strategy", config.name)
        }

    @Test
    fun `test ensure default config exists creates one if missing`() =
        runTest {
            // Given: No configs exist
            assertEquals(0, repository.getConfigCount())

            // When: Ensure default exists
            val defaultConfig = repository.ensureDefaultConfigExists()

            // Then: A default config is created
            assertNotNull(defaultConfig)
            assertTrue(defaultConfig.isDefault)
            assertEquals(1, repository.getConfigCount())
        }

    @Test
    fun `test ensure default config exists returns existing if present`() =
        runTest {
            // Given: A default config already exists (with isDefault = true)
            val existing = StrategyConfig.default("existing-default", Clock.System.now())
                .copy(isDefault = true)
            repository.saveConfig(existing)

            // When: Ensure default exists
            val defaultConfig = repository.ensureDefaultConfigExists()

            // Then: The existing default is returned
            assertNotNull(defaultConfig)
            assertEquals("existing-default", defaultConfig.configId)
            assertEquals(1, repository.getConfigCount()) // No new config created
        }

    @Test
    fun `test save multiple configs in batch`() =
        runTest {
            // Given: Multiple configs
            val configs =
                listOf(
                    StrategyConfig.default("batch-1", Clock.System.now()),
                    StrategyConfig.conservative("batch-2", Clock.System.now()),
                    StrategyConfig.aggressive("batch-3", Clock.System.now()),
                )

            // When: Save in batch
            val result = repository.saveConfigs(configs)

            // Then: Operation succeeds
            assertTrue(result.isSuccess)

            // And: All configs are saved
            assertEquals(3, repository.getConfigCount())
        }

    @Test
    fun `test save batch validates all configs`() =
        runTest {
            // Given: Batch with one invalid config
            val configs =
                listOf(
                    StrategyConfig.default("good-1", Clock.System.now()),
                    StrategyConfig.conservative("bad", Clock.System.now(), ""), // Blank name
                )

            // When: Try to save batch
            val result = repository.saveConfigs(configs)

            // Then: Operation fails
            assertTrue(result.isFailure)

            // And: No configs are saved (atomic operation)
            assertEquals(0, repository.getConfigCount())
        }

    @Test
    fun `test config execution mode is persisted`() =
        runTest {
            // Given: Config with specific execution mode
            val config = StrategyConfig.aggressive("test-007", Clock.System.now())

            // When: Save and retrieve
            repository.saveConfig(config)
            val retrieved = repository.getConfigById("test-007")

            // Then: Execution mode is preserved
            assertNotNull(retrieved)
            assertEquals(ExecutionMode.AUTO, retrieved.executionConfig.mode)
        }

    @Test
    fun `test config position sizing method is persisted`() =
        runTest {
            // Given: Config with risk-based sizing
            val timestamp = Clock.System.now()
            val config = StrategyConfig.conservative("test-008", timestamp)

            // When: Save and retrieve
            repository.saveConfig(config)
            val retrieved = repository.getConfigById("test-008")

            // Then: Position sizing method is preserved
            assertNotNull(retrieved)
            assertEquals(PositionSizingMethod.RISK_BASED, retrieved.executionConfig.positionSizingMethod)
        }

    @Test
    fun `test config timestamps are updated on save`() =
        runTest {
            // Given: A config
            val initialTimestamp = Clock.System.now()
            val config = StrategyConfig.default("test-009", initialTimestamp)

            // When: Save the config (repository updates timestamp)
            val result = repository.saveConfig(config)

            // Then: Updated timestamp is applied
            assertTrue(result.isSuccess)
            val saved = result.getOrNull()
            assertNotNull(saved)
            // Updated timestamp should be >= initial (may be same if execution is very fast)
            assertTrue(saved.updatedAt >= initialTimestamp)
        }
}
