package com.menu.paywatch

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val cfg = Config(context)
        if (!cfg.enabled || !cfg.ready) return

        // Long messages arrive in parts: join the parts of each sender.
        val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        val bySender = LinkedHashMap<String, StringBuilder>()
        for (m in parts) {
            bySender.getOrPut(m.originatingAddress ?: "?") { StringBuilder() }.append(m.messageBody ?: "")
        }
        val allowed = cfg.senders.split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }
        for ((sender, sb) in bySender) {
            Forwarder.handle(context, cfg, "sms", sender, sb.toString(), allowed.any { sender.lowercase().contains(it) })
        }
    }
}
