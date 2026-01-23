package com.example.sitacardmaster

import android.content.Context
import android.content.SharedPreferences

actual class SettingsStorage actual constructor() {
    companion object {
        private var sharedPreferences: SharedPreferences? = null

        fun init(context: Context) {
            sharedPreferences = context.getSharedPreferences("sitacard_settings", Context.MODE_PRIVATE)
        }
    }

    private val delegate: SharedPreferences
        get() = sharedPreferences ?: throw IllegalStateException("SettingsStorage not initialized. Call SettingsStorage.init(context) first.")

    actual fun getString(key: String, defaultValue: String): String {
        return delegate.getString(key, defaultValue) ?: defaultValue
    }

    actual fun putString(key: String, value: String) {
        delegate.edit().putString(key, value).apply()
    }

    actual fun getBoolean(key: String, defaultValue: Boolean): Boolean {
        return delegate.getBoolean(key, defaultValue)
    }

    actual fun putBoolean(key: String, value: Boolean) {
        delegate.edit().putBoolean(key, value).apply()
    }

    actual fun remove(key: String) {
        delegate.edit().remove(key).apply()
    }
}
