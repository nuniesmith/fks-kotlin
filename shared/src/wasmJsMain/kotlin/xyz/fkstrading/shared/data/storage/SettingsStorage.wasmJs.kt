package xyz.fkstrading.shared.data.storage

import kotlinx.browser.localStorage
import org.w3c.dom.get
import org.w3c.dom.set

/**
 * WASM/JS implementation of [SettingsStorage] using window.localStorage.
 *
 * Uses the browser's localStorage API for persistent storage.
 * All keys are prefixed with "fks_" to avoid collisions with other
 * applications or libraries using localStorage on the same origin.
 *
 * Note: localStorage has a ~5MB storage limit per origin in most browsers.
 * All values are stored as strings and converted to/from the appropriate
 * types on read/write.
 */
actual class SettingsStorage actual constructor() {

    private val prefix = "fks_"

    private fun prefixedKey(key: String): String = "$prefix$key"

    actual fun getString(key: String, default: String): String {
        return localStorage[prefixedKey(key)] ?: default
    }

    actual fun putString(key: String, value: String) {
        localStorage[prefixedKey(key)] = value
    }

    actual fun getBoolean(key: String, default: Boolean): Boolean {
        val stored = localStorage[prefixedKey(key)] ?: return default
        return stored.toBooleanStrictOrNull() ?: default
    }

    actual fun putBoolean(key: String, value: Boolean) {
        localStorage[prefixedKey(key)] = value.toString()
    }

    actual fun getInt(key: String, default: Int): Int {
        val stored = localStorage[prefixedKey(key)] ?: return default
        return stored.toIntOrNull() ?: default
    }

    actual fun putInt(key: String, value: Int) {
        localStorage[prefixedKey(key)] = value.toString()
    }

    actual fun getLong(key: String, default: Long): Long {
        val stored = localStorage[prefixedKey(key)] ?: return default
        return stored.toLongOrNull() ?: default
    }

    actual fun putLong(key: String, value: Long) {
        localStorage[prefixedKey(key)] = value.toString()
    }

    actual fun getDouble(key: String, default: Double): Double {
        val stored = localStorage[prefixedKey(key)] ?: return default
        return stored.toDoubleOrNull() ?: default
    }

    actual fun putDouble(key: String, value: Double) {
        localStorage[prefixedKey(key)] = value.toString()
    }

    actual fun hasKey(key: String): Boolean {
        return localStorage[prefixedKey(key)] != null
    }

    actual fun remove(key: String) {
        localStorage.removeItem(prefixedKey(key))
    }

    actual fun clear() {
        val keysToRemove = mutableListOf<String>()
        for (i in 0 until localStorage.length) {
            val storageKey = localStorage.key(i) ?: continue
            if (storageKey.startsWith(prefix)) {
                keysToRemove.add(storageKey)
            }
        }
        keysToRemove.forEach { localStorage.removeItem(it) }
    }
}
