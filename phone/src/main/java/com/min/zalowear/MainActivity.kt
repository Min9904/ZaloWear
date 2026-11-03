package com.min.zalowear

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.Wearable
import java.io.ByteArrayOutputStream

/** Setup screen: grant permissions, check the watch connection, send a test call. */
class MainActivity : Activity() {
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (16 * resources.displayMetrics.density).toInt()
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(pad, pad * 2, pad, pad) }
        fun btn(label: String, onClick: () -> Unit) =
            col.addView(Button(this).apply { text = label; setOnClickListener { onClick() } })

        col.addView(TextView(this).apply { text = "Zalo → Watch call bridge"; textSize = 22f })
        status = TextView(this).apply { textSize = 15f; setPadding(0, pad, 0, pad) }
        col.addView(status)

        btn("1. Grant notification access") {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        btn("2. Allow display over other apps (needed to answer)") {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }
        btn("3. Allow answering calls (fallback)") {
            requestPermissions(arrayOf(Manifest.permission.ANSWER_PHONE_CALLS), 1)
        }
        btn("4. Disable battery optimisation") {
            startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
        }
        btn("Send test call to watch") { sendTest() }
        btn("Stop test call") {
            CallState.send(this, Protocol.END, DataMap().apply { putString(Protocol.K_ID, "test") })
        }
        btn("Refresh status") { refresh() }

        setContentView(ScrollView(this).apply { addView(col) })
    }

    override fun onResume() { super.onResume(); refresh() }

    private fun refresh() {
        val listenerOn = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
            ?.contains(ComponentName(this, ZaloCallListener::class.java).flattenToString()) == true
        val overlay = Settings.canDrawOverlays(this)
        val answer = checkSelfPermission(Manifest.permission.ANSWER_PHONE_CALLS) == PackageManager.PERMISSION_GRANTED
        fun mark(b: Boolean) = if (b) "✅" else "❌"
        val base = "${mark(listenerOn)} Notification access\n${mark(overlay)} Display over apps\n${mark(answer)} Answer calls permission\n"
        status.text = base + "… looking for watch"
        Wearable.getNodeClient(this).connectedNodes.addOnCompleteListener { t ->
            val nodes = t.result.orEmpty()
            status.text = base + if (nodes.isEmpty()) "❌ No watch connected" else "✅ Watch: " + nodes.joinToString { it.displayName }
        }
    }

    private fun sendTest() {
        // Draw a simple placeholder avatar.
        val bmp = Bitmap.createBitmap(192, 192, Bitmap.Config.ARGB_8888)
        Canvas(bmp).apply {
            drawColor(Color.rgb(0, 104, 255))
            drawText("T", 96f, 130f, Paint().apply { color = Color.WHITE; textSize = 110f; textAlign = Paint.Align.CENTER; isAntiAlias = true })
        }
        val jpg = ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) }.toByteArray()
        CallState.send(this, Protocol.RING, DataMap().apply {
            putString(Protocol.K_ID, "test")
            putString(Protocol.K_NAME, "Test caller")
            putString(Protocol.K_TEXT, "Đang gọi đến")
            putByteArray(Protocol.K_AVATAR, jpg)
        })
    }
}
