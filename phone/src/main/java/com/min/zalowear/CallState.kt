package com.min.zalowear

import android.Manifest
import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Icon
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.telecom.TelecomManager
import android.util.Log
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.Wearable
import java.io.ByteArrayOutputStream

data class ZaloCall(
    val key: String,
    val name: String,
    val text: String,
    val video: Boolean,
    val ringing: Boolean,
    val answer: PendingIntent?,
    val decline: PendingIntent?,
    val hangup: PendingIntent?,
    val avatarIcon: Icon?,
    val postTime: Long,
)

/** Current Zalo call on the phone, and the bridge to/from the watch. */
object CallState {
    private const val TAG = "ZaloWear"
    private val main = Handler(Looper.getMainLooper())

    @Volatile var listenerConnected = false
    @Volatile var current: ZaloCall? = null
        private set
    private var avatar: ByteArray? = null
    private var ongoingSince = 0L

    fun onCall(ctx: Context, call: ZaloCall) {
        val prev = current
        current = call
        val changed = prev == null || prev.key != call.key || prev.ringing != call.ringing || prev.name != call.name
        if (!changed) return // Zalo reposts the notification a lot; only forward real changes.

        if (prev == null || prev.key != call.key || prev.name != call.name || avatar == null) {
            avatar = iconToJpeg(ctx, call.avatarIcon)
        }
        val map = DataMap().apply {
            putString(Protocol.K_ID, call.key)
            putString(Protocol.K_NAME, call.name)
            putString(Protocol.K_TEXT, call.text)
            putBoolean(Protocol.K_VIDEO, call.video)
            avatar?.let { putByteArray(Protocol.K_AVATAR, it) }
        }
        if (call.ringing) {
            send(ctx, Protocol.RING, map)
        } else {
            ongoingSince = System.currentTimeMillis()
            map.putLong(Protocol.K_SINCE, ongoingSince)
            send(ctx, Protocol.ONGOING, map)
        }
    }

    fun onEnded(ctx: Context) {
        val c = current ?: return
        current = null
        main.removeCallbacksAndMessages(null)
        if (speakerSet) speakerOff(ctx)
        avatar = null
        send(ctx, Protocol.END, DataMap().apply { putString(Protocol.K_ID, c.key) })
    }

    /** Watch asked to accept/decline/hang up. */
    fun handleWatchCommand(ctx: Context, path: String) {
        val c = current
        Log.i(TAG, "watch command $path, call=${c?.name}")
        if (c == null) {
            // Stale UI on the watch (e.g. test call) - just tell it to close.
            send(ctx, Protocol.END, DataMap().apply { putString(Protocol.K_ID, "") })
            return
        }
        when (path) {
            Protocol.ACCEPT, Protocol.ACCEPT_SPEAKER -> {
                fire(ctx, c.answer)
                // If the activity launch was blocked by Android, fall back to Telecom.
                main.postDelayed({
                    if (current?.key == c.key && current?.ringing == true) acceptViaTelecom(ctx)
                }, 2500)
                if (path == Protocol.ACCEPT_SPEAKER) {
                    // Zalo sets its own audio route when the call connects, so apply ours after it, twice.
                    main.postDelayed({ speakerOn(ctx) }, 1500)
                    main.postDelayed({ speakerOn(ctx) }, 3500)
                }
            }
            Protocol.DECLINE -> fire(ctx, c.decline)
            Protocol.HANGUP -> fire(ctx, c.hangup ?: c.decline)
        }
    }

    private fun fire(ctx: Context, pi: PendingIntent?) {
        if (pi == null) { Log.w(TAG, "no PendingIntent"); return }
        try {
            val opts = if (Build.VERSION.SDK_INT >= 34) {
                ActivityOptions.makeBasic().setPendingIntentBackgroundActivityStartMode(
                    ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                ).toBundle()
            } else null
            pi.send(ctx, 0, null, null, null, null, opts)
        } catch (e: Exception) {
            Log.w(TAG, "send PendingIntent failed", e)
        }
    }

    @Suppress("DEPRECATION")
    private fun acceptViaTelecom(ctx: Context) {
        if (ctx.checkSelfPermission(Manifest.permission.ANSWER_PHONE_CALLS) != PackageManager.PERMISSION_GRANTED) return
        runCatching {
            ctx.getSystemService(TelecomManager::class.java).acceptRingingCall()
            Log.i(TAG, "acceptRingingCall fallback")
        }.onFailure { Log.w(TAG, "telecom accept", it) }
    }

    private var speakerSet = false

    /** Undo our loudspeaker request when the call ends, so later calls aren't on speaker. */
    @Suppress("DEPRECATION")
    private fun speakerOff(ctx: Context) {
        speakerSet = false
        val am = ctx.getSystemService(AudioManager::class.java)
        runCatching {
            if (Build.VERSION.SDK_INT >= 31) am.clearCommunicationDevice()
            am.isSpeakerphoneOn = false
        }
    }

    @Suppress("DEPRECATION")
    private fun speakerOn(ctx: Context) {
        if (current == null) return
        speakerSet = true
        val am = ctx.getSystemService(AudioManager::class.java)
        runCatching {
            if (Build.VERSION.SDK_INT >= 31) {
                am.availableCommunicationDevices
                    .firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                    ?.let { am.setCommunicationDevice(it) }
            }
            am.isSpeakerphoneOn = true
        }.onFailure { Log.w(TAG, "speaker", it) }
    }

    fun send(ctx: Context, path: String, map: DataMap) {
        val app = ctx.applicationContext
        val bytes = map.toByteArray()
        Wearable.getNodeClient(app).connectedNodes.addOnSuccessListener { nodes ->
            if (nodes.isEmpty()) Log.w(TAG, "no watch connected")
            nodes.forEach { Wearable.getMessageClient(app).sendMessage(it.id, path, bytes) }
        }.addOnFailureListener { Log.w(TAG, "nodes", it) }
    }

    private fun iconToJpeg(ctx: Context, icon: Icon?): ByteArray? = runCatching {
        val d = icon?.loadDrawable(ctx) ?: return null
        val size = 192
        val bmp = if (d is BitmapDrawable && d.bitmap != null) {
            Bitmap.createScaledBitmap(d.bitmap, size, size, true)
        } else {
            Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also {
                d.setBounds(0, 0, size, size); d.draw(Canvas(it))
            }
        }
        ByteArrayOutputStream().use { out ->
            bmp.compress(Bitmap.CompressFormat.JPEG, 85, out); out.toByteArray()
        }
    }.onFailure { Log.w(TAG, "avatar", it) }.getOrNull()
}
