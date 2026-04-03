package xyz.fkstrading.shared.data.storage

/**
 * Cross-platform settings storage interface using Kotlin Multiplatform expect/actual pattern.
 *
 * Each platform provides its own implementation:
 * - **Android**: SharedPreferences
 * - **Desktop (JVM)**: java.util.prefs.Preferences
 * - **iOS**: NSUserDefaults
 * - **WASM/JS**: window.localStorage
 *
 * Usage:
 * ```kotlin
 * val storage = SettingsStorage()
 * storage.putString("api_base_url", "http://localhost:8000")
 * val url = storage.getString("api_base_url", "http://localhost:8000")
 * ```
 */
expect class SettingsStorage() {

    /**
     * Retrieve a string value from persistent storage.
     *
     * @param key The key to look up
     * @param default The default value if key is not found
     * @return The stored string value, or [default] if not found
     */
    fun getString(key: String, default: String): String

    /**
     * Store a string value in persistent storage.
     *
     * @param key The key to store under
     * @param value The string value to store
     */
    fun putString(key: String, value: String)

    /**
     * Retrieve a boolean value from persistent storage.
     *
     * @param key The key to look up
     * @param default The default value if key is not found
     * @return The stored boolean value, or [default] if not found
     */
    fun getBoolean(key: String, default: Boolean): Boolean

    /**
     * Store a boolean value in persistent storage.
     *
     * @param key The key to store under
     * @param value The boolean value to store
     */
    fun putBoolean(key: String, value: Boolean)

    /**
     * Retrieve an integer value from persistent storage.
     *
     * @param key The key to look up
     * @param default The default value if key is not found
     * @return The stored integer value, or [default] if not found
     */
    fun getInt(key: String, default: Int): Int

    /**
     * Store an integer value in persistent storage.
     *
     * @param key The key to store under
     * @param value The integer value to store
     */
    fun putInt(key: String, value: Int)

    /**
     * Retrieve a long value from persistent storage.
     *
     * @param key The key to look up
     * @param default The default value if key is not found
     * @return The stored long value, or [default] if not found
     */
    fun getLong(key: String, default: Long): Long

    /**
     * Store a long value in persistent storage.
     *
     * @param key The key to store under
     * @param value The long value to store
     */
    fun putLong(key: String, value: Long)

    /**
     * Retrieve a double value from persistent storage.
     *
     * @param key The key to look up
     * @param default The default value if key is not found
     * @return The stored double value, or [default] if not found
     */
    fun getDouble(key: String, default: Double): Double

    /**
     * Store a double value in persistent storage.
     *
     * @param key The key to store under
     * @param value The double value to store
     */
    fun putDouble(key: String, value: Double)

    /**
     * Check whether a given key exists in storage.
     *
     * @param key The key to check
     * @return `true` if the key exists, `false` otherwise
     */
    fun hasKey(key: String): Boolean

    /**
     * Remove a specific key from storage.
     *
     * @param key The key to remove
     */
    fun remove(key: String)

    /**
     * Remove all stored settings.
     */
    fun clear()
}
