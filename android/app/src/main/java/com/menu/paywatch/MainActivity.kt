package com.menu.paywatch

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/** Small settings screen built in code (no layout files). */
class MainActivity : Activity() {
    private lateinit var cfg: Config
    private lateinit var root: LinearLayout
    private lateinit var status: TextView
    private lateinit var logView: TextView

    private fun field(label: String, value: String, secret: Boolean = false): EditText {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        box.addView(TextView(this).apply { text = label; textSize = 14f })
        val e = EditText(this).apply {
            setText(value)
            isSingleLine = true
            inputType = if (secret) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            else InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        }
        box.addView(e)
        root.addView(box)
        return e
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cfg = Config(this)
        val pad = (16 * resources.displayMetrics.density).toInt()
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(pad, pad * 2, pad, pad) }
        val scroll = ScrollView(this).apply { addView(root) }
        setContentView(scroll)

        root.addView(TextView(this).apply { text = "PayWatch"; textSize = 24f })
        root.addView(TextView(this).apply {
            text = "Reads incoming Vodafone Cash / InstaPay transfer messages and sends the amount and transaction id to your restaurant server."
            textSize = 13f
        })

        val url = field("Server URL (https://xxxx.supabase.co)", cfg.serverUrl)
        val anon = field("Public (anon) key", cfg.anonKey, true)
        val dkey = field("Device key (from the README step)", cfg.deviceKey, true)
        val senders = field("Extra sender names to accept (optional, comma separated)", cfg.senders)

        val sw = Switch(this).apply { text = "Forward transfer messages"; isChecked = cfg.enabled }
        root.addView(sw)

        val wa = Switch(this).apply { text = "Also read WhatsApp notifications"; isChecked = cfg.waEnabled }
        root.addView(wa)
        val waAccess = Button(this).apply { text = "Allow notification access (WhatsApp)" }

        val save = Button(this).apply { text = "Save and enable" }
        val test = Button(this).apply { text = "Test connection" }
        val battery = Button(this).apply { text = "Allow background running" }
        root.addView(waAccess); root.addView(save); root.addView(test); root.addView(battery)

        status = TextView(this).apply { textSize = 14f; setPadding(0, pad, 0, pad) }
        root.addView(status)
        root.addView(TextView(this).apply { text = "Recent activity"; textSize = 14f })
        logView = TextView(this).apply { textSize = 12f; gravity = Gravity.START }
        root.addView(logView)

        save.setOnClickListener {
            cfg.serverUrl = url.text.toString(); cfg.anonKey = anon.text.toString()
            cfg.deviceKey = dkey.text.toString(); cfg.senders = senders.text.toString()
            cfg.enabled = sw.isChecked; cfg.waEnabled = wa.isChecked
            ensurePermission()
            scheduleHeartbeat()
            Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
            refresh()
        }
        test.setOnClickListener {
            cfg.serverUrl = url.text.toString(); cfg.anonKey = anon.text.toString(); cfg.deviceKey = dkey.text.toString()
            if (!cfg.ready) { Toast.makeText(this, "Fill the URL (https), key and device key", Toast.LENGTH_LONG).show(); return@setOnClickListener }
            thread {
                val msg = try {
                    val (code, text) = Api.rpc(cfg, "device_heartbeat", JSONObject().put("p_key", cfg.deviceKey))
                    if (code in 200..299) "Connected. The server accepted this device." else "Server said $code: $text"
                } catch (e: Exception) { "Could not reach the server: ${e.javaClass.simpleName}" }
                runOnUiThread { Toast.makeText(this, msg, Toast.LENGTH_LONG).show(); refresh() }
            }
        }
        waAccess.setOnClickListener { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
        battery.setOnClickListener {
            if (Build.VERSION.SDK_INT >= 23) {
                val pm = getSystemService(PowerManager::class.java)
                if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                    startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
                } else Toast.makeText(this, "Already allowed", Toast.LENGTH_SHORT).show()
            }
        }
        ensurePermission()
        refresh()
    }

    override fun onResume() { super.onResume(); refresh() }

    private fun ensurePermission() {
        if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.RECEIVE_SMS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECEIVE_SMS), 1)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        refresh()
    }

    private fun scheduleHeartbeat() {
        val req = PeriodicWorkRequestBuilder<HeartbeatWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork("heartbeat", ExistingPeriodicWorkPolicy.UPDATE, req)
    }

    private fun refresh() {
        val perm = Build.VERSION.SDK_INT < 23 || checkSelfPermission(Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
        status.text = when {
            !perm -> "SMS permission is missing. Tap Save and enable, then allow it."
            !cfg.ready -> "Not configured yet."
            !cfg.enabled -> "Configured but switched off."
            else -> "Running. Incoming transfer messages are forwarded."
        }
        logView.text = cfg.logText().ifEmpty { "Nothing yet." }
    }
}
