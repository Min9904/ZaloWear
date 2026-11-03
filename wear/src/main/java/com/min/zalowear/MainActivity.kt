package com.min.zalowear

import android.Manifest
import android.app.Activity
import android.app.NotificationManager
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.io.ByteArrayOutputStream

/** Watch setup screen: permissions + local test call. */
class MainActivity : Activity() {
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val w = resources.displayMetrics.widthPixels
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
            setPadding((w * 0.12).toInt(), (w * 0.15).toInt(), (w * 0.12).toInt(), (w * 0.2).toInt())
        }
        col.addView(TextView(this).apply { text = "Zalo Calls"; textSize = 16f; setTextColor(Color.WHITE); gravity = Gravity.CENTER })
        status = TextView(this).apply { textSize = 11f; setTextColor(Color.LTGRAY); gravity = Gravity.CENTER }
        col.addView(status)
        fun btn(label: String, f: () -> Unit) = col.addView(Button(this).apply { text = label; textSize = 11f; setOnClickListener { f() } })
        btn("Allow notifications") { if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1) }
        btn("Allow full screen") {
            if (Build.VERSION.SDK_INT >= 34) runCatching {
                startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:$packageName")))
            }
        }
        btn("Test call") { testCall() }
        btn("Test in 5 s (lock the watch)") { delayedTest() }
        if (intent.getBooleanExtra("delayedTest", false)) delayedTest()
        setContentView(ScrollView(this).apply { addView(col) })
    }

    override fun onResume() {
        super.onResume()
        val nm = getSystemService(NotificationManager::class.java)
        val notif = nm.areNotificationsEnabled()
        val fsi = Build.VERSION.SDK_INT < 34 || nm.canUseFullScreenIntent()
        status.text = "${if (notif) "✅" else "❌"} notifications\n${if (fsi) "✅" else "❌"} full screen\nWaiting for Zalo calls…"
    }

    private fun delayedTest() {
        val app = applicationContext
        android.os.Handler(mainLooper).postDelayed({ testCall(app) }, 5000)
        moveTaskToBack(true)
    }

    private fun testCall(ctx: android.content.Context = this) {
        val bmp = Bitmap.createBitmap(192, 192, Bitmap.Config.ARGB_8888)
        Canvas(bmp).apply {
            drawColor(Color.rgb(0, 104, 255))
            drawText("R", 96f, 130f, Paint().apply { color = Color.WHITE; textSize = 110f; textAlign = Paint.Align.CENTER; isAntiAlias = true })
        }
        val jpg = ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) }.toByteArray()
        val c = WatchCall.Ringing("local", "Rôm 😊", "Đang gọi đến", false, jpg)
        CallBus.set(c)
        Ringer.start(ctx, c)
    }
}
