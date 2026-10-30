package com.menu.paywatch

import android.content.Context

/** Settings and a small event log, kept in app-private storage. */
class Config(ctx: Context) {
    private val sp = ctx.applicationContext.getSharedPreferences("paywatch", Context.MODE_PRIVATE)

    var serverUrl: String
        get() = sp.getString("url", "") ?: ""
        set(v) = sp.edit().putString("url", v.trim().trimEnd('/')).apply()
    var anonKey: String
        get() = sp.getString("anon", "") ?: ""
        set(v) = sp.edit().putString("anon", v.trim()).apply()
    var deviceKey: String
        get() = sp.getString("dkey", "") ?: ""
        set(v) = sp.edit().putString("dkey", v.trim()).apply()
    /** Comma separated sender names to always accept (optional). */
    var senders: String
        get() = sp.getString("senders", "") ?: ""
        set(v) = sp.edit().putString("senders", v.trim()).apply()
    var enabled: Boolean
        get() = sp.getBoolean("on", true)
        set(v) = sp.edit().putBoolean("on", v).apply()

    /** Also read WhatsApp notifications (needs Notification access). */
    var waEnabled: Boolean
        get() = sp.getBoolean("wa", true)
        set(v) = sp.edit().putBoolean("wa", v).apply()

    val ready: Boolean get() = serverUrl.startsWith("https://") && anonKey.isNotEmpty() && deviceKey.isNotEmpty()

    fun log(line: String) {
        val t = java.text.SimpleDateFormat("MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date())
        val old = (sp.getString("log", "") ?: "").split("\n").filter { it.isNotBlank() }
        sp.edit().putString("log", (listOf("$t  $line") + old).take(40).joinToString("\n")).apply()
    }
    fun logText(): String = sp.getString("log", "") ?: ""
}
