package xyz.fkstrading.shared.data.storage

import java.util.prefs.Preferences

/**
 * Desktop (JVM) implementation of [SettingsStorage] using java.util.prefs.Preferences.
 *
 * Preferences are stored in the platform-specific backing store:
 * - **Windows**: Windows Registry under HKEY_CURRENT_USER\Software\JavaSoft\Prefs
 * - **macOS**: ~/Library/Preferences/com.apple.java.util.prefs.plist
 * - **Linux**: ~/.java/.userPrefs/
 *
 * All values are stored under the node path for the FKS trading application.
 */
actual class SettingsStorage actual constructor() {

    private val prefs: Preferences =
        Preferences.userRoot().node("xyz/fkstrading/settings")

    actual fun getString(key: String, default: String): String {
        return prefs.get(key, default)
    }

    actual fun putString(key: String, value: String) {
        prefs.put(key, value)
        prefs.flush()
    }

    actual fun getBoolean(key: String, default: Boolean): Boolean {
        return prefs.getBoolean(key, default)
    }

    actual fun putBoolean(key: String, value: Boolean) {
        prefs.putBoolean(key, value)
        prefs.flush()
    }

    actual fun getInt(key: String, default: Int): Int {
        return prefs.getInt(key, default)
    }

    actual fun putInt(key: String, value: Int) {
        prefs.putInt(key, value)
        prefs.flush()
    }

    actual fun getLong(key: String, default: Long): Long {
        return prefs.getLong(key, default)
    }

    actual fun putLong(key: String, value: Long) {
        prefs.putLong(key, value)
        prefs.flush()
    }

    actual fun getDouble(key: String, default: Double): Double {
        return prefs.getDouble(key, default)
    }

    actual fun putDouble(key: String, value: Double) {
        prefs.putDouble(key, value)
        prefs.flush()
    }

    actual fun hasKey(key: String): Boolean {
        return prefs.get(key, null) != null
    }

    actual fun remove(key: String) {
        prefs.remove(key)
        prefs.flush()
    }

    actual fun clear() {
        prefs.clear()
        prefs.flush()
    }
}
