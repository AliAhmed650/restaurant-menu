package com.menu.paywatch

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

/** Shared by the SMS receiver and the WhatsApp notification reader. */
object Forwarder {
    fun handle(context: Context, cfg: Config, source: String, sender: String, body: String, whitelisted: Boolean) {
        val p = SmsParser.parse(body)
        // Only forward messages that look like a transfer: a known provider in
        // the text, or a sender the owner whitelisted. Everything else (OTPs,
        // ads, personal chats) never leaves the phone.
        if (!(whitelisted || p.provider != "other")) return
        if (!p.incoming) { cfg.log("ignored outgoing ($source) from $sender"); return }
        if (p.amount == null && p.txn == null) { cfg.log("ignored, no amount or id ($source) from $sender"); return }

        val req = OneTimeWorkRequestBuilder<SendWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .setInputData(
                workDataOf(
                    "provider" to p.provider,
                    "amount" to (p.amount ?: -1.0),
                    "txn" to (p.txn ?: ""),
                    "sender" to "$source:$sender",
                    "raw" to body,
                    "time" to System.currentTimeMillis()
                )
            ).build()
        // Unique per message: a repeated broadcast or an updated notification is not queued twice.
        // (The server also rejects a repeated transaction id.)
        WorkManager.getInstance(context).enqueueUniqueWork(
            "msg-" + (sender + body).hashCode(), ExistingWorkPolicy.KEEP, req
        )
        cfg.log("queued ($source) ${p.provider} amount=${p.amount} id=${p.txn}")
    }
}
