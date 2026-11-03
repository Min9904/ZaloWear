package com.min.zalowear

/** Message paths shared by the phone and watch apps (keep both copies identical). */
object Protocol {
    // phone -> watch
    const val RING = "/zalo/ring"        // DataMap: id, name, text, video, avatar(jpeg bytes)
    const val ONGOING = "/zalo/ongoing"  // DataMap: id, name, since(ms), avatar
    const val END = "/zalo/end"          // DataMap: id
    // watch -> phone
    const val ACCEPT = "/zalo/accept"
    const val ACCEPT_SPEAKER = "/zalo/accept_speaker" // answer + phone loudspeaker
    const val DECLINE = "/zalo/decline"
    const val HANGUP = "/zalo/hangup"

    const val K_ID = "id"
    const val K_NAME = "name"
    const val K_TEXT = "text"
    const val K_VIDEO = "video"
    const val K_AVATAR = "avatar"
    const val K_SINCE = "since"
}
