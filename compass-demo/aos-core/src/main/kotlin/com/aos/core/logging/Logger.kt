package com.aos.core.logging

import timber.log.Timber

/**
 * Banking-agnostic logging façade. Release builds get a no-op tree by default;
 * the consuming app may plant a Crashlytics tree on top.
 */
object Logger {

    fun installDebug() {
        if (Timber.forest().isEmpty()) {
            Timber.plant(Timber.DebugTree())
        }
    }

    fun d(tag: String, message: String) = Timber.tag(tag).d(message)
    fun w(tag: String, message: String, t: Throwable? = null) = Timber.tag(tag).w(t, message)
    fun e(tag: String, message: String, t: Throwable? = null) = Timber.tag(tag).e(t, message)
}
