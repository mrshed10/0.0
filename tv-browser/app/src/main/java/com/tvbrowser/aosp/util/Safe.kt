package com.tvbrowser.aosp.util

import android.util.Log

/**
 * Runs [block] and swallows anything it throws (including OutOfMemoryError from a heavy
 * page or a misbehaving extension callback). Used around every engine callback and UI
 * action so a single failure never takes the whole browser down.
 */
inline fun safe(tag: String, block: () -> Unit) {
    try {
        block()
    } catch (t: Throwable) {
        Log.e(tag, "recovered from error", t)
        CrashGuard.recordSoftError(tag, t)
    }
}

inline fun <T> safeOr(tag: String, fallback: T, block: () -> T): T =
    try {
        block()
    } catch (t: Throwable) {
        Log.e(tag, "recovered from error", t)
        CrashGuard.recordSoftError(tag, t)
        fallback
    }
