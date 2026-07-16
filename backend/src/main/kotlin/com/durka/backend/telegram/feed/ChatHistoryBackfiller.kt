package com.durka.backend.telegram.feed

import it.tdlight.client.SimpleTelegramClient
import it.tdlight.jni.TdApi
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

/**
 * Backfills older messages for a private chat via TDLib's GetChatHistory, on demand - the live
 * update stream (FeedItemIngestor) only ever reports things happening from the moment it starts
 * listening, so anything sent before that needs to be fetched explicitly instead.
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
     * chat_backfill_status, not a row count: an active chat can already have plenty of *incoming*
     * rows from before outgoing messages were stored at all, which would make a count-based check
     * falsely conclude there's nothing to backfill). Always starts from the newest message and
     * pages backward, so it also fills in outgoing messages interleaved with whatever incoming
     * ones were already stored - insert() is idempotent on (chat_id, message_id), so re-fetching
     * messages we already have is safe, just a bit of wasted API calls on that first pass.
     */
    fun ensureBackfilled(client: SimpleTelegramClient, chatId: Long, desiredCount: Int) {
        if (feedItemRepository.hasBackfilled(chatId)) return

        val chat = client.send(TdApi.GetChat(chatId)).get(10, TimeUnit.SECONDS)
        val chatCategory = ChatCategory.from(chat.type)
        if (chatCategory != ChatCategory.PRIVATE) return

        // Resolved once per call, not once per message - unlike the live path, we already know
        // every message in this backfill belongs to this one chat.
        val (displayName, username) = TelegramMessageMapper
            .resolveUserName(client, (chat.type as TdApi.ChatTypePrivate).userId)
            .get(10, TimeUnit.SECONDS)

        var fromMessageId = 0L // 0 = start from the newest message
        var count = feedItemRepository.countForChat(chatId)

        var page = 0
        while (count < desiredCount && page < MAX_PAGES) {
            val history = client.send(TdApi.GetChatHistory(chatId, fromMessageId, 0, PAGE_SIZE, false))
                .get(15, TimeUnit.SECONDS)
            val messages = history.messages.filterNotNull()
            if (messages.isEmpty()) break // reached the actual start of the conversation

            messages.forEach { message ->
                feedItemRepository.insert(TelegramMessageMapper.toNewFeedItem(message, chatCategory, displayName, username))
            }

            fromMessageId = messages.minOf { it.id }
            count = feedItemRepository.countForChat(chatId)
            page++
        }

        feedItemRepository.markBackfilled(chatId)
    }
}
