package com.durka.backend.telegram.feed

import it.tdlight.client.SimpleTelegramClient
import it.tdlight.jni.TdApi
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.concurrent.CompletableFuture

/**
 * Normalizes incoming Telegram messages into feed_item rows. Content is plain text only for
 * now (non-text messages are stored as a "(MessageXxx)" placeholder) - rich spans/media parsing
 * is deferred to a later pass; this is the minimum needed to serve a working messages feed.
 */
@Component
class FeedItemIngestor(private val feedItemRepository: FeedItemRepository) {

    private val log = LoggerFactory.getLogger(FeedItemIngestor::class.java)

    fun handle(client: SimpleTelegramClient, update: TdApi.UpdateNewMessage) {
        val message = update.message

        // Don't surface our own sent messages as feed items.
        if (message.isOutgoing) return

        // chat type (private/group/channel) is a property of the chat, resolved separately from
        // who sent the message - a group's chatId is the group itself, not the individual sender.
        val chatCategoryFuture = client.send(TdApi.GetChat(message.chatId)).thenApply { chat -> ChatCategory.from(chat.type) }
        val senderFuture = resolveSenderName(client, message.senderId)

        chatCategoryFuture.thenCombine(senderFuture) { chatCategory, from -> chatCategory to from }
            .whenCompleteAsync { result, error ->
                if (error != null) {
                    log.warn("Could not resolve chat/sender info for chat {} message {}", message.chatId, message.id, error)
                }
                val (chatCategory, from) = result ?: (ChatCategory.PRIVATE to null)
                val (displayName, username) = from ?: (null to null)

                feedItemRepository.insert(
                    NewFeedItem(
                        externalId = "tg:${message.chatId}:${message.id}",
                        chatId = message.chatId,
                        messageId = message.id,
                        chatType = chatCategory,
                        fromDisplayName = displayName,
                        fromUsername = username,
                        occurredAt = Instant.ofEpochSecond(message.date.toLong()),
                        text = extractText(message.content),
                    )
                )
            }
    }

    private fun extractText(content: TdApi.MessageContent): String = when (content) {
        is TdApi.MessageText -> content.text.text
        else -> "(${content.javaClass.simpleName})"
    }

    private fun resolveSenderName(
        client: SimpleTelegramClient,
        senderId: TdApi.MessageSender,
    ): CompletableFuture<Pair<String?, String?>> = when (senderId) {
        is TdApi.MessageSenderUser ->
            client.send(TdApi.GetUser(senderId.userId)).thenApply { user ->
                val displayName = listOfNotNull(user.firstName.ifBlank { null }, user.lastName.ifBlank { null })
                    .joinToString(" ")
                    .ifBlank { null }
                val username = user.usernames?.activeUsernames?.firstOrNull()
                displayName to username
            }
        is TdApi.MessageSenderChat ->
            client.send(TdApi.GetChat(senderId.chatId)).thenApply { chat -> chat.title to null }
    }
}
