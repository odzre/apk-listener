package com.notipay.listener

import android.content.Context
import android.content.SharedPreferences
import androidx.core.app.NotificationManagerCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

object Prefs {
    private const val FILE = "notipay_prefs"
    private const val KEY_URL = "callback_url"
    private const val KEY_API = "api_key"
    private const val KEY_DEVICE = "device_id"
    private const val KEY_APPS = "selected_packages"
    private const val KEY_SERVICE = "service_enabled"
    private const val KEY_LISTENER = "listener_connected"
    private const val KEY_LAST_STATUS = "last_status"
    private const val KEY_LAST_TIME = "last_time"
    private const val KEY_LAST_BODY = "last_body"

    fun get(context: Context): SharedPreferences =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun callbackUrl(context: Context): String = get(context).getString(KEY_URL, "").orEmpty()

    fun apiKey(context: Context): String = get(context).getString(KEY_API, "").orEmpty()

    fun deviceId(context: Context): String {
        val prefs = get(context)
        val existing = prefs.getString(KEY_DEVICE, "").orEmpty()
        if (existing.isNotBlank()) return existing
        val generated = "android-${UUID.randomUUID().toString().take(8)}"
        prefs.edit().putString(KEY_DEVICE, generated).apply()
        return generated
    }

    fun selectedPackages(context: Context): MutableSet<String> =
        get(context).getStringSet(KEY_APPS, emptySet())?.toMutableSet() ?: mutableSetOf()

    fun saveConfig(context: Context, url: String, apiKey: String, deviceId: String, packages: Set<String>) {
        get(context).edit()
            .putString(KEY_URL, url.trim())
            .putString(KEY_API, apiKey.trim())
            .putString(KEY_DEVICE, deviceId.trim())
            .putStringSet(KEY_APPS, packages.toSet())
            .apply()
    }

    fun isServiceEnabled(context: Context): Boolean = get(context).getBoolean(KEY_SERVICE, false)

    fun setServiceEnabled(context: Context, enabled: Boolean) {
        get(context).edit().putBoolean(KEY_SERVICE, enabled).apply()
    }

    fun hasNotificationAccess(context: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    fun setListenerConnected(context: Context, connected: Boolean) {
        get(context).edit().putBoolean(KEY_LISTENER, connected).apply()
    }

    fun isListenerConnected(context: Context): Boolean = get(context).getBoolean(KEY_LISTENER, false)

    fun setLastStatus(context: Context, code: Int, body: String) {
        val time = SimpleDateFormat("dd MMM yyyy HH:mm:ss", Locale("id", "ID")).format(Date())
        get(context).edit()
            .putInt(KEY_LAST_STATUS, code)
            .putString(KEY_LAST_TIME, time)
            .putString(KEY_LAST_BODY, body.take(280))
            .apply()
    }

    fun lastStatus(context: Context): String {
        val prefs = get(context)
        val code = prefs.getInt(KEY_LAST_STATUS, -1)
        if (code < 0) return "Callback terakhir: belum ada"
        val time = prefs.getString(KEY_LAST_TIME, "").orEmpty()
        val body = prefs.getString(KEY_LAST_BODY, "").orEmpty()
        val ok = code in 200..299
        return buildString {
            append(if (ok) "Callback terakhir: " else "Callback gagal: ")
            append("HTTP ")
            append(code)
            if (time.isNotBlank()) {
                append(" · ")
                append(time)
            }
            if (body.isNotBlank()) {
                append("\n")
                append(body.replace("\n", " ").take(220))
            }
        }
    }
}
