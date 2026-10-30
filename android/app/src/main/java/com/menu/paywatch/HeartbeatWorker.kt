package com.menu.paywatch

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import org.json.JSONObject

/** Tells the dashboard this phone is alive (shown as "Online" in the Payments tab). */
class HeartbeatWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val cfg = Config(applicationContext)
        if (!cfg.ready) return Result.success()
        return try {
            Api.rpc(cfg, "device_heartbeat", JSONObject().put("p_key", cfg.deviceKey))
            Result.success()
        } catch (e: Exception) { Result.retry() }
    }
}
