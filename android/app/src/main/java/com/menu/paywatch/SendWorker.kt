package com.menu.paywatch

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object Api {
    /** Calls a Postgres function through the Supabase REST API. Returns HTTP code and body. */
    fun rpc(cfg: Config, fn: String, body: JSONObject): Pair<Int, String> {
        val c = URL("${cfg.serverUrl}/rest/v1/rpc/$fn").openConnection() as HttpURLConnection
        try {
            c.requestMethod = "POST"
            c.connectTimeout = 15000
            c.readTimeout = 20000
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json")
            c.setRequestProperty("apikey", cfg.anonKey)
            if (!cfg.anonKey.startsWith("sb_")) c.setRequestProperty("Authorization", "Bearer ${cfg.anonKey}")
            c.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
            return code to text
        } finally {
            c.disconnect()
        }
    }
}

class SendWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val cfg = Config(applicationContext)
        if (!cfg.ready) return@withContext Result.failure()
        val d = inputData
        val amount = d.getDouble("amount", -1.0)
        val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
            .format(Date(d.getLong("time", System.currentTimeMillis())))
        val body = JSONObject()
            .put("p_key", cfg.deviceKey)
            .put("p_provider", d.getString("provider") ?: "other")
            .put("p_amount", if (amount >= 0) amount else JSONObject.NULL)
            .put("p_txn", (d.getString("txn") ?: "").ifEmpty { JSONObject.NULL })
            .put("p_sender", d.getString("sender") ?: "")
            .put("p_raw", d.getString("raw") ?: "")
            .put("p_device_time", iso)
        try {
            val (code, text) = Api.rpc(cfg, "ingest_payment", body)
            when {
                code in 200..299 -> { cfg.log("sent ok: $text"); Result.success() }
                code == 429 || code >= 500 -> { cfg.log("server busy ($code), will retry"); Result.retry() }
                else -> { cfg.log("rejected ($code): $text"); Result.failure() }  // wrong key etc: retrying will not help
            }
        } catch (e: Exception) {
            cfg.log("network error, will retry: ${e.javaClass.simpleName}")
            Result.retry()
        }
    }
}
