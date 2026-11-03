package com.min.zalowear

import android.app.Activity
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Outline
import android.os.Bundle
import android.os.SystemClock
import android.view.GestureDetector
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.widget.Chronometer
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Full-screen incoming call UI (black background, round avatar, caller name).
 * Accept: tap green / swipe right. Accept on phone speaker: tap the speaker button.
 * Decline: tap red / swipe left / shake the wrist.
 */
class CallActivity : Activity() {
    private lateinit var avatar: ImageView
    private lateinit var name: TextView
    private lateinit var timer: Chronometer
    private lateinit var gap: View
    private lateinit var gap2: View
    private lateinit var speaker: ImageButton
    private lateinit var accept: ImageButton
    private lateinit var decline: ImageButton
    private lateinit var gestures: GestureDetector
    private var lastAvatar: ByteArray? = null

    private val listener: (WatchCall?) -> Unit = { render(it) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        buildUi()
        gestures = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent) = true
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
                val dx = e2.x - (e1?.x ?: e2.x)
                if (abs(dx) < dp(40) || abs(vx) < abs(vy)) return false
                if (dx > 0) doAccept() else doDecline()
                return true
            }
        })
    }

    override fun onStart() { super.onStart(); CallBus.observe(listener) }

    override fun onStop() {
        super.onStop()
        CallBus.remove(listener)
        // Left with Home / Back (not just screen off): silence the ringing, keep the call alive.
        val c = CallBus.call
        if (c is WatchCall.Ringing && getSystemService(android.os.PowerManager::class.java).isInteractive) {
            Ringer.silence(this, c)
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (CallBus.call is WatchCall.Ringing && gestures.onTouchEvent(ev)) {
            if (ev.action == MotionEvent.ACTION_UP || ev.action == MotionEvent.ACTION_MOVE) return true
        }
        return super.dispatchTouchEvent(ev)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (CallBus.call is WatchCall.Ringing) when (keyCode) {
            KeyEvent.KEYCODE_NAVIGATE_NEXT -> { doAccept(); return true }      // wrist flick out
            KeyEvent.KEYCODE_NAVIGATE_PREVIOUS -> { doDecline(); return true } // wrist flick in
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun doAccept() = CallBus.accept(this)
    private fun doAcceptSpeaker() = CallBus.accept(this, speaker = true)
    private fun doDecline() = CallBus.decline(this)
    private fun doHangup() = CallBus.hangup(this)

    // Shake = decline: 3 strong jolts (>13 m/s² above gravity) within 1.2 s, so lifting the wrist doesn't count.
    private val shake = object : SensorEventListener {
        private val peaks = ArrayDeque<Long>()
        private var lastPeak = 0L
        override fun onSensorChanged(e: SensorEvent) {
            val (x, y, z) = e.values
            val g = sqrt(x * x + y * y + z * z) - SensorManager.GRAVITY_EARTH
            val now = SystemClock.elapsedRealtime()
            if (abs(g) > 13f && now - lastPeak > 120) {
                lastPeak = now
                peaks.addLast(now)
                while (peaks.isNotEmpty() && now - peaks.first() > 1200) peaks.removeFirst()
                if (peaks.size >= 3) { peaks.clear(); doDecline() }
            }
        }
        override fun onAccuracyChanged(s: Sensor?, a: Int) {}
    }

    override fun onResume() {
        super.onResume()
        val sm = getSystemService(SensorManager::class.java)
        sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let { sm.registerListener(shake, it, SensorManager.SENSOR_DELAY_GAME) }
    }

    override fun onPause() {
        super.onPause()
        getSystemService(SensorManager::class.java).unregisterListener(shake)
    }

    private fun render(c: WatchCall?) {
        if (c == null) { finish(); return }
        name.text = c.name
        if (!c.avatar.contentEquals(lastAvatar)) {
            lastAvatar = c.avatar
            val bmp = c.avatar?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
            if (bmp != null) avatar.setImageBitmap(bmp) else avatar.setImageResource(R.drawable.ic_launcher)
        }
        val ringing = c is WatchCall.Ringing
        if (ringing) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // Ringing: [answer] [decline]. In call: just a centered [hang up].
        val v = if (ringing) View.VISIBLE else View.GONE
        accept.visibility = v; gap.visibility = v; speaker.visibility = v; gap2.visibility = v
        decline.setOnClickListener { if (ringing) doDecline() else doHangup() }
        if (c is WatchCall.Ongoing) {
            timer.visibility = View.VISIBLE
            timer.base = SystemClock.elapsedRealtime() - (System.currentTimeMillis() - c.since).coerceAtLeast(0)
            timer.start()
        } else {
            timer.visibility = View.GONE; timer.stop()
        }
    }

    private fun dp(v: Int) = v * resources.displayMetrics.density

    private fun buildUi() {
        val w = resources.displayMetrics.widthPixels
        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            clipChildren = false; clipToPadding = false
        }
        val avSize = (w * 0.36).toInt()
        avatar = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, o: Outline) = o.setOval(0, 0, view.width, view.height)
            }
            clipToOutline = true
            setBackgroundColor(Color.DKGRAY)
        }
        col.addView(avatar, LinearLayout.LayoutParams(avSize, avSize))
        name = TextView(this).apply {
            setTextColor(Color.WHITE); textSize = 18f; gravity = Gravity.CENTER
            maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding((w * 0.12).toInt(), dp(8).toInt(), (w * 0.12).toInt(), 0)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        col.addView(name, LinearLayout.LayoutParams(-1, -2))
        timer = Chronometer(this).apply { setTextColor(Color.parseColor("#2ECC71")); textSize = 13f; gravity = Gravity.CENTER; visibility = View.GONE }
        col.addView(timer, LinearLayout.LayoutParams(-1, -2))

        val btnSize = (w * 0.20).toInt()
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
        }
        fun roundBtn(icon: Int, bg: Int, desc: String) = ImageButton(this).apply {
            setImageResource(icon); setBackgroundResource(bg); contentDescription = desc
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            val p = (btnSize * 0.26).toInt(); setPadding(p, p, p, p)
        }
        accept = roundBtn(R.drawable.ic_call, R.drawable.bg_accept, "Trả lời").apply { setOnClickListener { doAccept() } }
        decline = roundBtn(R.drawable.ic_call_end, R.drawable.bg_decline, "Từ chối")
        speaker = roundBtn(R.drawable.ic_speaker, R.drawable.bg_accept, "Trả lời bằng loa ngoài").apply { setOnClickListener { doAcceptSpeaker() } }
        gap = View(this); gap2 = View(this)
        row.addView(accept, LinearLayout.LayoutParams(btnSize, btnSize))
        row.addView(gap, LinearLayout.LayoutParams((w * 0.05).toInt(), 1))
        row.addView(speaker, LinearLayout.LayoutParams(btnSize, btnSize))
        row.addView(gap2, LinearLayout.LayoutParams((w * 0.05).toInt(), 1))
        row.addView(decline, LinearLayout.LayoutParams(btnSize, btnSize))
        col.addView(row, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14).toInt() })

        root.addView(col, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)
    }
}
