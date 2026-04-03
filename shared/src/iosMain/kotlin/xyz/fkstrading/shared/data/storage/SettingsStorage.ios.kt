package xyz.fkstrading.shared.data.storage

import platform.Foundation.NSUserDefaults

/**
 * iOS implementation of [SettingsStorage] using NSUserDefaults.
 *
 * Uses NSUserDefaults.standardUserDefaults for persistent storage.
 * All keys are prefixed with "fks_" to avoid collisions with other
 * settings stored in the same defaults domain.
 */
actual class SettingsStorage actual constructor() {

    private val defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults

    private fun prefixedKey(key: String): String = "fks_$key"

    actual fun getString(key: String, default: String): String {
        val prefixed = prefixedKey(key)
        return if (defaults.objectForKey(prefixed) != null) {
            defaults.stringForKey(prefixed) ?: default
        } else {
            default
        }
    }

    actual fun putString(key: String, value: String) {
        defaults.setObject(value, forKey = prefixedKey(key))
        defaults.synchronize()
    }

    actual fun getBoolean(key: String, default: Boolean): Boolean {
        val prefixed = prefixedKey(key)
        return if (defaults.objectForKey(prefixed) != null) {
            defaults.boolForKey(prefixed)
        } else {
            default
        }
    }

    actual fun putBoolean(key: String, value: Boolean) {
        defaults.setBool(value, forKey = prefixedKey(key))
        defaults.synchronize()
    }

    actual fun getInt(key: String, default: Int): Int {
        val prefixed = prefixedKey(key)
        return if (defaults.objectForKey(prefixed) != null) {
            defaults.integerForKey(prefixed).toInt()
        } else {
            default
        }
    }

    actual fun putInt(key: String, value: Int) {
        defaults.setInteger(value.toLong(), forKey = prefixedKey(key))
        defaults.synchronize()
    }

    actual fun getLong(key: String, default: Long): Long {
        val prefixed = prefixedKey(key)
        return if (defaults.objectForKey(prefixed) != null) {
            defaults.integerForKey(prefixed)
        } else {
            default
        }
    }

    actual fun putLong(key: String, value: Long) {
        defaults.setInteger(value, forKey = prefixedKey(key))
        defaults.synchronize()
    }

    actual fun getDouble(key: String, default: Double): Double {
        val prefixed = prefixedKey(key)
        return if (defaults.objectForKey(prefixed) != null) {
            defaults.doubleForKey(prefixed)
        } else {
            default
        }
    }

    actual fun putDouble(key: String, value: Double) {
        defaults.setDouble(value, forKey = prefixedKey(key))
        defaults.synchronize()
    }

    actual fun hasKey(key: String): Boolean {
        return defaults.objectForKey(prefixedKey(key)) != null
    }

    actual fun remove(key: String) {
        defaults.removeObjectForKey(prefixedKey(key))
        defaults.synchronize()
    }

    actual fun clear() {
        val dict = defaults.dictionaryRepresentation()
        for (entry in dict) {
            val entryKey = entry.key as? String ?: continue
            if (entryKey.startsWith("fks_")) {
                defaults.removeObjectForKey(entryKey)
            }
        }
        defaults.synchronize()
    }
}
