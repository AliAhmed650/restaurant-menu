package com.menu.paywatch

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Reads WhatsApp notification text, for the case where the wallet or bank sends
 * its transfer confirmation on WhatsApp instead of SMS. Needs "Notification access"
 * (switched on by the owner in Android settings). Only text that looks like a
 * transfer is forwarded; other chats are ignored on the phone.
 */
class WhatsAppListener : NotificationListenerService() {
    private val apps = setOf("com.whatsapp", "com.whatsapp.w4b")

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName !in apps) return
        val cfg = Config(this)
        if (!cfg.enabled || !cfg.ready || !cfg.waEnabled) return
        val ex = sbn.notification.extras
        val title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: "?"
        val text = (ex.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: ex.getCharSequence(Notification.EXTRA_TEXT))?.toString() ?: return
        val allowed = cfg.senders.split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }
        Forwarder.handle(this, cfg, "whatsapp", title, text, allowed.any { title.lowercase().contains(it) })
    }
}
