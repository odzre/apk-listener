package com.notipay.listener

import android.content.Context
import android.content.Intent
import org.json.JSONArray
import org.json.JSONObject

data class NotificationLogEntry(
    val id: String,
    val timestamp: Long,
    val packageName: String,
    val appLabel: String,
    val title: String,
    val text: String,
    val status: String,
)

object LogStore {
    const val ACTION_LOGS_UPDATED = "com.notipay.listener.LOGS_UPDATED"
    private const val FILE = "notipay_logs"
    private const val KEY_LOGS = "notification_logs"
    private const val MAX_ENTRIES = 150

    @Synchronized
    fun add(context: Context, entry: NotificationLogEntry) {
        val logs = readArray(context)
        val next = JSONArray().put(entry.toJson())
        for (index in 0 until minOf(logs.length(), MAX_ENTRIES - 1)) {
            next.put(logs.getJSONObject(index))
        }
        writeArray(context, next)
        notifyChanged(context)
    }

    @Synchronized
    fun updateStatus(context: Context, id: String, status: String) {
        if (id.isBlank()) return
        val logs = readArray(context)
        var changed = false
        for (index in 0 until logs.length()) {
            val item = logs.getJSONObject(index)
            if (item.optString("id") == id) {
                item.put("status", status)
                changed = true
                break
            }
        }
        if (changed) {
            writeArray(context, logs)
            notifyChanged(context)
        }
    }

    @Synchronized
    fun list(context: Context): List<NotificationLogEntry> {
        val logs = readArray(context)
        val entries = mutableListOf<NotificationLogEntry>()
        for (index in 0 until logs.length()) {
            runCatching { entries.add(fromJson(logs.getJSONObject(index))) }
        }
        return entries
    }

    @Synchronized
    fun clear(context: Context) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().remove(KEY_LOGS).apply()
        notifyChanged(context)
    }

    private fun readArray(context: Context): JSONArray {
        val raw = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getString(KEY_LOGS, "[]")
            .orEmpty()
        return runCatching { JSONArray(raw) }.getOrElse { JSONArray() }
    }

    private fun writeArray(context: Context, logs: JSONArray) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LOGS, logs.toString())
            .apply()
    }

    private fun notifyChanged(context: Context) {
        context.sendBroadcast(Intent(ACTION_LOGS_UPDATED).setPackage(context.packageName))
    }

    private fun NotificationLogEntry.toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("timestamp", timestamp)
        put("package", packageName)
        put("app", appLabel)
        put("title", title)
        put("text", text)
        put("status", status)
    }

    private fun fromJson(json: JSONObject): NotificationLogEntry = NotificationLogEntry(
        id = json.optString("id"),
        timestamp = json.optLong("timestamp", System.currentTimeMillis()),
        packageName = json.optString("package"),
        appLabel = json.optString("app"),
        title = json.optString("title"),
        text = json.optString("text"),
        status = json.optString("status"),
    )
}
