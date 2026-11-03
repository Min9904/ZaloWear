package com.min.zalowear

import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService

/** Receives call events from the phone app. */
class WatchMessageService : WearableListenerService() {
    override fun onMessageReceived(event: MessageEvent) {
        CallBus.phoneNode = event.sourceNodeId
        val m = runCatching { DataMap.fromByteArray(event.data) }.getOrElse { DataMap() }
        val id = m.getString(Protocol.K_ID) ?: ""
        val name = m.getString(Protocol.K_NAME) ?: "Zalo"
        val avatar = m.getByteArray(Protocol.K_AVATAR)
        when (event.path) {
            Protocol.RING -> {
                val c = WatchCall.Ringing(id, name, m.getString(Protocol.K_TEXT) ?: "", m.getBoolean(Protocol.K_VIDEO), avatar)
                CallBus.set(c)
                Ringer.start(this, c)
            }
            Protocol.ONGOING -> {
                Ringer.stop(this)
                // Show the in-call screen only if the call was answered from the watch.
                val cur = CallBus.call
                if (cur is WatchCall.Connecting || cur is WatchCall.Ongoing) {
                    val o = WatchCall.Ongoing(id, name, m.getLong(Protocol.K_SINCE, System.currentTimeMillis()), avatar)
                    CallBus.set(o)
                    Ringer.ongoing(this, o)
                } else CallBus.set(null)
            }
            Protocol.END -> {
                Ringer.stop(this)
                val cur = CallBus.call
                if (cur == null || id.isEmpty() || cur.id == id) CallBus.set(null)
            }
        }
    }
}
