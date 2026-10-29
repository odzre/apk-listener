package com.notipay.listener

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class CallbackWorker(
    context: Context,
    params: WorkerParameters,
) : Worker(context, params) {

    override fun doWork(): Result {
        val url = inputData.getString(KEY_URL).orEmpty()
        val payload = inputData.getString(KEY_PAYLOAD).orEmpty()
        val logId = inputData.getString(KEY_LOG_ID).orEmpty()
        if (url.isBlank() || payload.isBlank()) {
            LogStore.updateStatus(applicationContext, logId, "Gagal: payload kosong")
            return Result.failure()
        }

        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 20_000
                readTimeout = 20_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "NotiPay-Listener/1.1")
            }
            connection.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            Prefs.setLastStatus(applicationContext, code, body)

            when {
                code in 200..299 -> {
                    LogStore.updateStatus(applicationContext, logId, "Sukses HTTP $code")
                    Result.success()
                }
                code == 408 || code == 429 || code >= 500 -> {
                    LogStore.updateStatus(applicationContext, logId, "Retry HTTP $code")
                    Result.retry()
                }
                else -> {
                    LogStore.updateStatus(applicationContext, logId, "Gagal HTTP $code")
                    Result.failure()
                }
            }
        } catch (error: IOException) {
            Prefs.setLastStatus(applicationContext, 0, "Gagal terhubung: ${error.message.orEmpty()}")
            LogStore.updateStatus(applicationContext, logId, "Retry: koneksi gagal")
            Result.retry()
        } catch (error: Exception) {
            Prefs.setLastStatus(applicationContext, 0, "Error: ${error.message.orEmpty()}")
            LogStore.updateStatus(applicationContext, logId, "Retry: ${error.message.orEmpty().take(80)}")
            Result.retry()
        } finally {
            connection?.disconnect()
        }
    }

    companion object {
        const val KEY_URL = "callback_url"
        const val KEY_PAYLOAD = "callback_payload"
        const val KEY_LOG_ID = "log_id"
    }
}
