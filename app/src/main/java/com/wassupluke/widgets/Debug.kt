package com.wassupluke.widgets

import android.util.Log

/**
 * Tiny logcat wrapper for tracking widget behaviour over adb:
 *
 *     adb logcat -s uWidgets
 *
 * Debug builds log everything. Release builds are quiet until you opt in with
 *
 *     adb shell setprop log.tag.uWidgets DEBUG
 *
 * (the property survives until reboot). Warnings always go through.
 */
internal object Debug {
    const val TAG = "uWidgets"

    private val enabled: Boolean
        get() = BuildConfig.DEBUG || Log.isLoggable(TAG, Log.DEBUG)

    fun log(message: String) {
        if (enabled) Log.d(TAG, message)
    }

    fun warn(message: String, error: Throwable? = null) {
        Log.w(TAG, message, error)
    }
}
