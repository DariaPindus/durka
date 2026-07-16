package com.durka.backend.telegram.feed

import it.tdlight.client.SimpleTelegramClient
import it.tdlight.jni.TdApi
import java.time.Instant
import java.util.concurrent.CompletableFuture

/**
 * Shared between the live ingestion path (FeedItemIngestor, one message at a time, resolves
 * chat/sender fresh per message since it doesn't know context ahead of time) and the history
 * backfill path (ChatHistoryBackfiller, resolves the chat's counterpart once and reuses it for
 * every historical message in that chat) - both need to turn a TdApi.Message into a NewFeedItem
 * the same way.
 */
object TelegramMessageMapper {

    fun extractText(content: TdApi.MessageContent): String = when (content) {
        is TdApi.MessageText -> content.text.text
        else -> "(${content.javaClass.simpleName})"
    }

    fun toNewFeedItem(
        message: TdApi.Message,
        chatCategory: ChatCategory,
        fromDisplayName: String?,
        fromUsername: String?,
        chatTitle: String? = null,
    ): NewFeedItem = NewFeedItem(
        externalId = "tg:${message.chatId}:${message.id}",
        chatId = message.chatId,
        messageId = message.id,
        chatType = chatCategory,
        fromDisplayName = fromDisplayName,
        fromUsername = fromUsername,
        occurredAt = Instant.ofEpochSecond(message.date.toLong()),
        text = extractText(message.content),
        isOutgoing = message.isOutgoing,
        chatTitle = chatTitle,
    )

    fun resolveUserName(client: SimpleTelegramClient, userId: Long): CompletableFuture<Pair<String?, String?>> =
        client.send(TdApi.GetUser(userId)).thenApply { user ->
            val displayName = listOfNotNull(user.firstName.ifBlank { null }, user.lastName.ifBlank { null })
                .joinToString(" ")
                .ifBlank { null }
            val username = user.usernames?.activeUsernames?.firstOrNull()
            displayName to username
        }

    fun resolveSenderName(
        client: SimpleTelegramClient,
        senderId: TdApi.MessageSender,
    ): CompletableFuture<Pair<String?, String?>> = when (senderId) {
        is TdApi.MessageSenderUser -> resolveUserName(client, senderId.userId)
        is TdApi.MessageSenderChat ->
            client.send(TdApi.GetChat(senderId.chatId)).thenApply { chat -> chat.title to null }
    }
}
