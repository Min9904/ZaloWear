package com.min.zalowear

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Answer / Decline buttons on the ring notification. */
class CallActionReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        when (intent.action) {
            ACCEPT -> CallBus.accept(ctx)
            DECLINE -> CallBus.decline(ctx)
            HANGUP -> CallBus.hangup(ctx)
        }
    }
    companion object {
        const val ACCEPT = "com.min.zalowear.ACCEPT"
        const val DECLINE = "com.min.zalowear.DECLINE"
        const val HANGUP = "com.min.zalowear.HANGUP"
    }
}
