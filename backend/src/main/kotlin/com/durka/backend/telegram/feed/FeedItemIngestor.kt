package com.durka.backend.telegram.feed

import it.tdlight.client.SimpleTelegramClient
import it.tdlight.jni.TdApi
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Normalizes incoming (and, for private chats only, our own outgoing) Telegram messages into
 * feed_item rows, one at a time as they arrive live. Content is plain text only for now (non-text
 * messages are stored as a "(MessageXxx)" placeholder) - rich spans/media parsing is deferred.
 */
@Component
class FeedItemIngestor(private val feedItemRepository: FeedItemRepository) {

    private val log = LoggerFactory.getLogger(FeedItemIngestor::class.java)

    fun handle(client: SimpleTelegramClient, update: TdApi.UpdateNewMessage) {
        val message = update.message

        // chat type (private/group/channel) is a property of the chat, resolved separately from
        // who sent the message - a group's chatId is the group itself, not the individual sender.
        val resolved = client.send(TdApi.GetChat(message.chatId)).thenCompose { chat ->
            val chatCategory = ChatCategory.from(chat.type)
            // For a private chat, always resolve the OTHER person - not message.senderId, which
            // is ourselves for our own outgoing messages. chat.type.userId is who this
            // conversation is with regardless of message direction. For group/channel, resolve
            // the individual sender of THIS message (many different people post in a group,
            // unlike a private chat's fixed counterpart) plus the chat's own title, used as the
            // conversation label in the senders list instead of "whoever posted last".
            val fromFuture = if (chatCategory == ChatCategory.PRIVATE) {
                TelegramMessageMapper.resolveUserName(client, (chat.type as TdApi.ChatTypePrivate).userId)
            } else {
                TelegramMessageMapper.resolveSenderName(client, message.senderId)
            }
            fromFuture.thenApply { from -> Triple(chatCategory, from, chat.title) }
        }

        resolved.whenCompleteAsync { result, error ->
            if (error != null) {
                log.warn("Could not resolve chat/sender info for chat {} message {}", message.chatId, message.id, error)
            }
            val (chatCategory, from, chatTitle) = result ?: Triple(ChatCategory.PRIVATE, null, null)

            // Our own messages are only kept for private conversations (the reply feature's
            // thread view needs both sides) - group/channel posts we sent stay excluded, same
            // as before, so they don't clutter the "what did people send me" view.
            if (message.isOutgoing && chatCategory != ChatCategory.PRIVATE) return@whenCompleteAsync

            val (displayName, username) = from ?: (null to null)
            feedItemRepository.insert(
                TelegramMessageMapper.toNewFeedItem(
                    message = message,
                    chatCategory = chatCategory,
                    fromDisplayName = displayName,
                    fromUsername = username,
                    chatTitle = if (chatCategory == ChatCategory.PRIVATE) null else chatTitle,
                )
            )
        }
    }
}
