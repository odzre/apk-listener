package com.notipay.listener

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import org.json.JSONObject
import java.security.MessageDigest
import java.time.Instant
import java.util.concurrent.TimeUnit

object CallbackSender {
    fun enqueueNotification(
        context: Context,
        packageName: String,
        title: String,
        text: String,
    ): Boolean {
        val url = Prefs.callbackUrl(context)
        val apiKey = Prefs.apiKey(context)
        if (url.isBlank() || apiKey.isBlank()) return false
        val payload = JSONObject().apply {
            put("apikey", apiKey)
            put("package", packageName)
            put("title", title.take(500))
            put("text", text.take(4000))
            put("device_id", Prefs.deviceId(context))
            put("timestamp", Instant.now().toString())
        }.toString()
        val uniqueKey = "notification-${sha256("$packageName|$title|$text").take(24)}"
        return enqueue(context, url, payload, uniqueKey)
    }

    fun enqueueTest(context: Context, url: String, apiKey: String, deviceId: String): Boolean {
        if (url.isBlank() || apiKey.isBlank()) return false
        val payload = JSONObject().apply {
            put("apikey", apiKey)
            put("package", "com.notipay.listener.test")
            put("title", "Tes koneksi")
            put("text", "Tes koneksi NotiPay Listener berhasil.")
            put("device_id", deviceId)
            put("timestamp", Instant.now().toString())
        }.toString()
        return enqueue(context, url, payload, "test-${System.currentTimeMillis()}")
    }

    private fun enqueue(context: Context, url: String, payload: String, uniqueKey: String): Boolean {
        val request = OneTimeWorkRequestBuilder<CallbackWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .setInputData(
                workDataOf(
                    CallbackWorker.KEY_URL to url,
                    CallbackWorker.KEY_PAYLOAD to payload,
                ),
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            uniqueKey,
            ExistingWorkPolicy.KEEP,
            request,
        )
        return true
    }

    private fun sha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}
