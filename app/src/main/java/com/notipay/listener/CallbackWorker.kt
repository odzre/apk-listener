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
        if (url.isBlank() || payload.isBlank()) return Result.failure()

        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 20_000
                readTimeout = 20_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "NotiPay-Listener/1.0")
            }
            connection.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            Prefs.setLastStatus(applicationContext, code, body)

            when {
                code in 200..299 -> Result.success()
                code == 408 || code == 429 || code >= 500 -> Result.retry()
                else -> Result.failure()
            }
        } catch (error: IOException) {
            Prefs.setLastStatus(applicationContext, 0, "Gagal terhubung: ${error.message.orEmpty()}")
            Result.retry()
        } catch (error: Exception) {
            Prefs.setLastStatus(applicationContext, 0, "Error: ${error.message.orEmpty()}")
            Result.retry()
        } finally {
            connection?.disconnect()
        }
    }

    companion object {
        const val KEY_URL = "callback_url"
        const val KEY_PAYLOAD = "callback_payload"
    }
}
