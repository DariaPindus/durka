package com.durka.backend.telegram.feed

import it.tdlight.client.SimpleTelegramClient
import it.tdlight.jni.TdApi
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

/**
 * Backfills older messages via TDLib's GetChatHistory, on demand - the live update stream
 * (FeedItemIngestor) only ever reports things happening from the moment it starts listening, so
 * anything sent before that needs to be fetched explicitly instead.
 */
@Component
class ChatHistoryBackfiller(private val feedItemRepository: FeedItemRepository) {

    companion object {
        private const val MAX_PAGES = 5
        private const val PAGE_SIZE = 100
    }

    /**
     * Fetches history until at least desiredCount messages are stored for this chat, or history
     * runs out - but only the very first time this chat is ever seen (tracked via
     * chat_backfill_status, not a row count: see FeedItemRepository.hasBackfilled for why a count
     * is unreliable here). Always starts from the newest message and pages backward; insert() is
     * idempotent on (chat_id, message_id), so re-fetching messages we already have is safe.
     *
     * Private chats resolve one fixed counterpart, reused for every message. Group/channel chats
     * resolve the chat's own title once (used as the conversation label), but each message's
     * individual sender separately (many different people post in a group) - cached within this
     * one call so repeat senders don't trigger a redundant GetUser/GetChat round trip.
     */
    fun ensureBackfilled(client: SimpleTelegramClient, chatId: Long, desiredCount: Int) {
        if (feedItemRepository.hasBackfilled(chatId)) return

        val chat = client.send(TdApi.GetChat(chatId)).get(10, TimeUnit.SECONDS)
        val chatCategory = ChatCategory.from(chat.type)

        val privateCounterpart = if (chatCategory == ChatCategory.PRIVATE) {
            TelegramMessageMapper.resolveUserName(client, (chat.type as TdApi.ChatTypePrivate).userId)
                .get(10, TimeUnit.SECONDS)
        } else {
            null
        }
        val senderCache = mutableMapOf<String, Pair<String?, String?>>()

        var fromMessageId = 0L // 0 = start from the newest message
        var count = feedItemRepository.countForChat(chatId)

        var page = 0
        while (count < desiredCount && page < MAX_PAGES) {
            val history = client.send(TdApi.GetChatHistory(chatId, fromMessageId, 0, PAGE_SIZE, false))
                .get(15, TimeUnit.SECONDS)
            val messages = history.messages.filterNotNull()
            if (messages.isEmpty()) break // reached the actual start of the conversation

            messages.forEach { message ->
                // Same policy as the live path: our own group/channel posts stay excluded so
                // they don't clutter the "what did people send me" view.
                if (message.isOutgoing && chatCategory != ChatCategory.PRIVATE) return@forEach

                val (displayName, username) = privateCounterpart ?: senderCache.getOrPut(senderCacheKey(message.senderId)) {
                    TelegramMessageMapper.resolveSenderName(client, message.senderId).get(10, TimeUnit.SECONDS)
                }

                feedItemRepository.insert(
                    TelegramMessageMapper.toNewFeedItem(
                        message = message,
                        chatCategory = chatCategory,
                        fromDisplayName = displayName,
                        fromUsername = username,
                        chatTitle = if (chatCategory == ChatCategory.PRIVATE) null else chat.title,
                    )
                )
            }

            fromMessageId = messages.minOf { it.id }
            count = feedItemRepository.countForChat(chatId)
            page++
        }

        feedItemRepository.markBackfilled(chatId)
    }

    private fun senderCacheKey(sender: TdApi.MessageSender): String = when (sender) {
        is TdApi.MessageSenderUser -> "user:${sender.userId}"
        is TdApi.MessageSenderChat -> "chat:${sender.chatId}"
    }
}
