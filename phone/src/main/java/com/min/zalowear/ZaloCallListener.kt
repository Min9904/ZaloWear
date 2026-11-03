package com.min.zalowear

import android.app.Notification
import android.app.PendingIntent
import android.app.Person
import android.graphics.drawable.Icon
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

/**
 * Watches Zalo's call notification (tag "incall", category "call").
 *
 * From the decompiled Zalo (md2/l1.java):
 *  - Android 12+: Notification.CallStyle with a Person (caller name + avatar bitmap),
 *    answer = activity PendingIntent (EXTRA_DATA_ACCEPT_CALL_FROM_NOTI),
 *    decline/hang up = broadcast "m.voip.api.action.ACTION_NOTI_END_CALL".
 *  - Older: plain notification with actions "Từ chối" (decline) then "Trả lời" (answer),
 *    and a single "Kết thúc" (end) action while in call.
 */
class ZaloCallListener : NotificationListenerService() {

    override fun onListenerConnected() {
        CallState.listenerConnected = true
        runCatching { activeNotifications?.forEach { onNotificationPosted(it) } }
    }

    override fun onListenerDisconnected() {
        CallState.listenerConnected = false
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName != ZALO) return
        val n = sbn.notification
        if (sbn.tag != "incall" && n.category != Notification.CATEGORY_CALL) return

        val call = parse(sbn) ?: return
        CallState.onCall(this, call)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (sbn.packageName != ZALO) return
        if (CallState.current?.key == sbn.key) CallState.onEnded(this)
    }

    private fun parse(sbn: StatusBarNotification): ZaloCall? {
        val n = sbn.notification
        val ex = n.extras

        var answer: PendingIntent? = null
        var decline: PendingIntent? = null
        var hangup: PendingIntent? = null
        var callType = 0
        var person: Person? = null

        if (Build.VERSION.SDK_INT >= 31) {
            callType = ex.getInt(Notification.EXTRA_CALL_TYPE, 0)
            answer = ex.parcel(Notification.EXTRA_ANSWER_INTENT)
            decline = ex.parcel(Notification.EXTRA_DECLINE_INTENT)
            hangup = ex.parcel(Notification.EXTRA_HANG_UP_INTENT)
            person = ex.parcel(Notification.EXTRA_CALL_PERSON)
        }

        // Plain style: match actions by label, fall back to Zalo's order (decline, answer).
        val actions = n.actions.orEmpty()
        if (answer == null && decline == null && hangup == null && actions.isNotEmpty()) {
            for (a in actions) {
                val t = a.title?.toString()?.lowercase().orEmpty()
                when {
                    ANSWER_WORDS.any { t.contains(it) } -> answer = a.actionIntent
                    DECLINE_WORDS.any { t.contains(it) } -> decline = a.actionIntent
                    END_WORDS.any { t.contains(it) } -> hangup = a.actionIntent
                }
            }
            if (answer == null && decline == null && hangup == null) {
                if (actions.size >= 2) { decline = actions[0].actionIntent; answer = actions[1].actionIntent }
                else hangup = actions[0].actionIntent
            }
        }

        val ringing = when (callType) {
            1 -> true                    // CALL_TYPE_INCOMING
            2, 3 -> false                // ONGOING / SCREENING
            else -> answer != null
        }
        // Outgoing calls / calls we never saw ring: ignore.
        if (!ringing && CallState.current?.key != sbn.key) return null

        val name = person?.name?.toString()
            ?: ex.getCharSequence(Notification.EXTRA_TITLE)?.toString()
            ?: "Zalo"
        val text = ex.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val icon: Icon? = person?.icon ?: n.getLargeIcon()

        return ZaloCall(
            key = sbn.key,
            name = name,
            text = text,
            video = text.contains("video", ignoreCase = true),
            ringing = ringing,
            answer = answer,
            decline = decline ?: hangup,
            hangup = hangup ?: decline,
            avatarIcon = icon,
            postTime = sbn.postTime,
        )
    }

    @Suppress("DEPRECATION")
    private inline fun <reified T : android.os.Parcelable> android.os.Bundle.parcel(k: String): T? =
        runCatching { getParcelable<T>(k) }.getOrNull()

    companion object {
        const val TAG = "ZaloWear"
        const val ZALO = "com.zing.zalo"
        val ANSWER_WORDS = listOf("trả lời", "answer", "accept", "nghe")
        val DECLINE_WORDS = listOf("từ chối", "decline", "reject")
        val END_WORDS = listOf("kết thúc", "end", "hang up", "gác máy")
    }
}
