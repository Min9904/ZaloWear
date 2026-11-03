package com.min.zalowear

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log

/**
 * Keeps the watch vibrating for as long as the call rings, independent of the screen:
 *  - a full-screen-intent notification with FLAG_INSISTENT (the system repeats its vibration), and
 *  - a repeating Vibrator waveform with ringtone usage.
 * Both stop when the phone reports the call was answered / declined / ended.
 */
object Ringer {
    private const val CHANNEL = "zalo_call_v1"
    private const val NOTIF_ID = 1001
    private const val MAX_RING_MS = 120_000L
    private val PATTERN = longArrayOf(0, 800, 400, 800, 1000)
    private val main = Handler(Looper.getMainLooper())
    private var wake: PowerManager.WakeLock? = null
    private val timeout = Runnable { stopVibration() }

    fun start(ctx: Context, c: WatchCall.Ringing) {
        val app = ctx.applicationContext
        val nm = app.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Zalo calls", NotificationManager.IMPORTANCE_HIGH).apply {
            enableVibration(true)
            vibrationPattern = PATTERN
            setSound(null, null)
            setBypassDnd(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        })
        val open = PendingIntent.getActivity(app, 0,
            Intent(app, CallActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = Notification.Builder(app, CHANNEL)
            .setSmallIcon(R.drawable.ic_call)
            .setContentTitle(c.name)
            .setContentText("Zalo: ${c.text.ifBlank { "Đang gọi đến" }}")
            .setCategory(Notification.CATEGORY_CALL)
            .setOngoing(true)
            .setContentIntent(open)
            .setFullScreenIntent(open, true)
            // Answer / Decline straight from the notification if the full screen didn't show.
            .addAction(Notification.Action.Builder(null, "Trả lời", action(app, CallActionReceiver.ACCEPT)).build())
            .addAction(Notification.Action.Builder(null, "Từ chối", action(app, CallActionReceiver.DECLINE)).build())
            .build()
        n.flags = n.flags or Notification.FLAG_INSISTENT
        runCatching { nm.notify(NOTIF_ID, n) }.onFailure { Log.w("ZaloWear", "notify", it) }

        // Wake the screen and try to open the call UI directly too (works when allowed).
        @Suppress("DEPRECATION")
        wake = app.getSystemService(PowerManager::class.java).newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP, "zalowear:ring"
        ).apply { acquire(MAX_RING_MS) }
        runCatching { app.startActivity(Intent(app, CallActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }

        vibrate(app)
        main.removeCallbacks(timeout)
        main.postDelayed(timeout, MAX_RING_MS)
    }

    private const val CHANNEL_QUIET = "zalo_call_quiet_v1"

    /** Home/Back while ringing: stop buzzing but keep a silent notification to reopen and answer. */
    fun silence(ctx: Context, c: WatchCall.Ringing) {
        stop(ctx)
        quiet(ctx, c.name, "Đang gọi đến (đã tắt rung)",
            "Trả lời" to CallActionReceiver.ACCEPT, "Từ chối" to CallActionReceiver.DECLINE)
    }

    /** During a call answered from the watch: silent notification to get back to the hang-up screen. */
    fun ongoing(ctx: Context, c: WatchCall) {
        stop(ctx)
        quiet(ctx, c.name, "Đang trong cuộc gọi", "Kết thúc" to CallActionReceiver.HANGUP)
    }

    private fun quiet(ctx: Context, title: String, text: String, vararg actions: Pair<String, String>) {
        val app = ctx.applicationContext
        val nm = app.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL_QUIET, "Zalo calls (silent)", NotificationManager.IMPORTANCE_LOW).apply {
            enableVibration(false); setSound(null, null)
        })
        val open = PendingIntent.getActivity(app, 1,
            Intent(app, CallActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val b = Notification.Builder(app, CHANNEL_QUIET)
            .setSmallIcon(R.drawable.ic_call)
            .setContentTitle(title)
            .setContentText(text)
            .setCategory(Notification.CATEGORY_CALL)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
        actions.forEach { (label, a) -> b.addAction(Notification.Action.Builder(null, label, action(app, a)).build()) }
        runCatching { nm.notify(NOTIF_ID, b.build()) }
    }

    private fun action(ctx: Context, a: String) = PendingIntent.getBroadcast(ctx, a.hashCode(),
        Intent(ctx, CallActionReceiver::class.java).setAction(a), PendingIntent.FLAG_IMMUTABLE)

    fun stop(ctx: Context) {
        stopVibration()
        ctx.applicationContext.getSystemService(NotificationManager::class.java).cancel(NOTIF_ID)
        wake?.let { if (it.isHeld) it.release() }
        wake = null
        main.removeCallbacks(timeout)
    }

    private var vib: Vibrator? = null

    // Alarm usage: keeps buzzing in Do Not Disturb / mute / bedtime (ringtone usage gets silenced there).
    private fun vibrate(ctx: Context) {
        val v = if (Build.VERSION.SDK_INT >= 31) ctx.getSystemService(VibratorManager::class.java).defaultVibrator
                else @Suppress("DEPRECATION") ctx.getSystemService(Vibrator::class.java)
        vib = v
        val effect = VibrationEffect.createWaveform(PATTERN, 0) // repeat until cancelled
        if (Build.VERSION.SDK_INT >= 33) {
            v.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(effect, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build())
        }
    }

    private fun stopVibration() { vib?.cancel(); vib = null }
}
