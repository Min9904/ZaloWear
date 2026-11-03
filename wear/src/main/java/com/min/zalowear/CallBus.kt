package com.min.zalowear

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.android.gms.wearable.Wearable

sealed class WatchCall {
    abstract val id: String
    abstract val name: String
    abstract val avatar: ByteArray?

    data class Ringing(override val id: String, override val name: String, val text: String,
                       val video: Boolean, override val avatar: ByteArray?) : WatchCall()
    data class Connecting(override val id: String, override val name: String, override val avatar: ByteArray?) : WatchCall()
    data class Ongoing(override val id: String, override val name: String, val since: Long,
                       override val avatar: ByteArray?) : WatchCall()
}

/** In-process call state shared by the message service, the ringer and the UI. */
object CallBus {
    private val main = Handler(Looper.getMainLooper())
    private val listeners = mutableSetOf<(WatchCall?) -> Unit>()

    @Volatile var call: WatchCall? = null
        private set
    @Volatile var phoneNode: String? = null

    fun set(c: WatchCall?) = main.post {
        call = c
        listeners.toList().forEach { it(c) }
    }

    fun observe(l: (WatchCall?) -> Unit) { listeners += l; l(call) }
    fun remove(l: (WatchCall?) -> Unit) { listeners -= l }

    fun accept(ctx: Context, speaker: Boolean = false) {
        val c = call as? WatchCall.Ringing ?: return
        Ringer.stop(ctx)
        if (c.id == "local") {
            val o = WatchCall.Ongoing(c.id, c.name, System.currentTimeMillis(), c.avatar)
            set(o); Ringer.ongoing(ctx, o); return
        }
        set(WatchCall.Connecting(c.id, c.name, c.avatar))
        command(ctx, if (speaker) Protocol.ACCEPT_SPEAKER else Protocol.ACCEPT)
    }

    fun decline(ctx: Context) {
        if (call !is WatchCall.Ringing) return
        Ringer.stop(ctx)
        command(ctx, Protocol.DECLINE)
        set(null)
    }

    fun hangup(ctx: Context) {
        Ringer.stop(ctx)
        command(ctx, Protocol.HANGUP)
        set(null)
    }

    /** Send accept/decline/hangup to the phone. Local test calls are handled here. */
    private fun command(ctx: Context, path: String) {
        if (call?.id == "local") return
        val app = ctx.applicationContext
        val mc = Wearable.getMessageClient(app)
        val node = phoneNode
        if (node != null) {
            mc.sendMessage(node, path, ByteArray(0))
        } else {
            Wearable.getNodeClient(app).connectedNodes.addOnSuccessListener { nodes ->
                nodes.forEach { mc.sendMessage(it.id, path, ByteArray(0)) }
            }
        }
        Log.i("ZaloWear", "sent $path")
    }
}
