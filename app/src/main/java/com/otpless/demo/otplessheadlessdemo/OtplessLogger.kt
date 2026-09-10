package com.otpless.demo.otplessheadlessdemo

import android.util.Log
import com.otpless.v2.android.sdk.dto.OtplessResponse
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * One row in the event log. A callback row (from [OtplessLogger.logResponse]) carries
 * [source]/[responseType]/[statusCode]/[data] so the UI can render a colored badge and
 * pretty-printed payload; a plain info row (from [OtplessLogger.log]) only has [text].
 */
data class LogEntry(
    val timestamp: String,
    val text: String,
    val source: String? = null,
    val responseType: String? = null,
    val statusCode: Int? = null,
    val data: String? = null,
)

/**
 * Mirrors every OtplessSDK callback into Logcat (tag "OTPLESS") and into an in-app
 * log feed so the callback stream is visible on-screen, not just in adb logcat.
 */
object OtplessLogger {

    private const val TAG = "OTPLESS"
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)

    private val entries = mutableListOf<LogEntry>()
    private val listeners = mutableListOf<(LogEntry) -> Unit>()
    private val clearListeners = mutableListOf<() -> Unit>()

    fun log(message: String) {
        val entry = LogEntry(timestamp = now(), text = message)
        entries.add(entry)
        Log.d(TAG, message)
        listeners.forEach { it(entry) }
    }

    fun logResponse(source: String, response: OtplessResponse) {
        val entry = LogEntry(
            timestamp = now(),
            text = "$source callback",
            source = source,
            responseType = response.responseType.name,
            statusCode = response.statusCode,
            data = response.response?.toString(),
        )
        entries.add(entry)
        Log.d(TAG, "$source callback -> type=${response.responseType} status=${response.statusCode} data=${response.response}")
        listeners.forEach { it(entry) }
    }

    fun addListener(listener: (LogEntry) -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: (LogEntry) -> Unit) {
        listeners.remove(listener)
    }

    fun addClearListener(listener: () -> Unit) {
        clearListeners.add(listener)
    }

    fun removeClearListener(listener: () -> Unit) {
        clearListeners.remove(listener)
    }

    fun history(): List<LogEntry> = entries.toList()

    fun clear() {
        entries.clear()
        clearListeners.forEach { it() }
    }

    private fun now(): String = timeFormat.format(System.currentTimeMillis())
}
