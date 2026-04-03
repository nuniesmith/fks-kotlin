package xyz.fkstrading.shared.data.storage

import android.content.Context
import android.content.SharedPreferences

/**
 * Android implementation of [SettingsStorage] using SharedPreferences.
 *
 * Uses the application-level SharedPreferences with the key "fks_settings"
 * for persistent storage across app restarts.
 */
actual class SettingsStorage actual constructor() {

    private val prefs: SharedPreferences by lazy {
        SettingsStorageInitializer.context
            ?.getSharedPreferences("fks_settings", Context.MODE_PRIVATE)
            ?: throw IllegalStateException(
                "SettingsStorage not initialized. Call SettingsStorageInitializer.init(context) first."
            )
    }

    actual fun getString(key: String, default: String): String {
        return prefs.getString(key, default) ?: default
    }

    actual fun putString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    actual fun getBoolean(key: String, default: Boolean): Boolean {
        return prefs.getBoolean(key, default)
    }

    actual fun putBoolean(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
    }

    actual fun getInt(key: String, default: Int): Int {
        return prefs.getInt(key, default)
    }

    actual fun putInt(key: String, value: Int) {
        prefs.edit().putInt(key, value).apply()
    }

    actual fun getLong(key: String, default: Long): Long {
        return prefs.getLong(key, default)
    }

    actual fun putLong(key: String, value: Long) {
        prefs.edit().putLong(key, value).apply()
    }

    actual fun getDouble(key: String, default: Double): Double {
        return if (prefs.contains(key)) {
            Double.fromBits(prefs.getLong(key, default.toBits()))
        } else {
            default
        }
    }

    actual fun putDouble(key: String, value: Double) {
        prefs.edit().putLong(key, value.toBits()).apply()
    }

    actual fun hasKey(key: String): Boolean {
        return prefs.contains(key)
    }

    actual fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }

    actual fun clear() {
        prefs.edit().clear().apply()
    }
}

/**
 * Initializer for Android SettingsStorage.
 *
 * Must be called during application startup (e.g., in Application.onCreate())
 * before any SettingsStorage instances are used.
 *
 * ```kotlin
 * class MyApplication : Application() {
 *     override fun onCreate() {
 *         super.onCreate()
 *         SettingsStorageInitializer.init(this)
 *     }
 * }
 * ```
 */
object SettingsStorageInitializer {
    internal var context: Context? = null
        private set

    /**
     * Initialize SettingsStorage with the application context.
     *
     * @param appContext The application context (will be stored as applicationContext)
     */
    fun init(appContext: Context) {
        context = appContext.applicationContext
    }
}
