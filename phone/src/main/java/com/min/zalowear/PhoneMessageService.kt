package com.min.zalowear

import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService

/** Receives Accept / Decline / Hang up from the watch. */
class PhoneMessageService : WearableListenerService() {
    override fun onMessageReceived(event: MessageEvent) {
        CallState.handleWatchCommand(this, event.path)
    }
}
