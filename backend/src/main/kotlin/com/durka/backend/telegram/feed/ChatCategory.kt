package com.durka.backend.telegram.feed

import it.tdlight.jni.TdApi

enum class ChatCategory {
    PRIVATE,
    GROUP,
    CHANNEL;

    companion object {
        fun from(type: TdApi.ChatType): ChatCategory = when (type) {
            is TdApi.ChatTypePrivate -> PRIVATE
            is TdApi.ChatTypeSecret -> PRIVATE
            is TdApi.ChatTypeBasicGroup -> GROUP
            is TdApi.ChatTypeSupergroup -> if (type.isChannel) CHANNEL else GROUP
        }
    }
}
