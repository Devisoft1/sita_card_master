package com.example.sitacardmaster

import platform.Foundation.NSUserDefaults

actual class SettingsStorage actual constructor() {
    private val delegate = NSUserDefaults.standardUserDefaults

    actual fun getString(key: String, defaultValue: String): String {
        return delegate.stringForKey(key) ?: defaultValue
    }

    actual fun putString(key: String, value: String) {
        delegate.setObject(value, forKey = key)
    }

    actual fun getBoolean(key: String, defaultValue: Boolean): Boolean {
        return if (delegate.objectForKey(key) != null) {
            delegate.boolForKey(key)
        } else {
            defaultValue
        }
    }

    actual fun putBoolean(key: String, value: Boolean) {
        delegate.setBool(value, forKey = key)
    }

    actual fun remove(key: String) {
        delegate.removeObjectForKey(key)
    }
}
