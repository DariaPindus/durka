package com.durka.backend.telegram.feed

import com.durka.backend.telegram.TelegramClientHolder
import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.util.concurrent.TimeUnit

data class SenderSummaryDto(
    val chatId: Long,
    val displayName: String?,
    val username: String?,
    val lastMessageAt: String,
)

// Named "outgoing", not "isOutgoing": Kotlin's "isXxx" boolean property compiles to a getter
// named isXxx(), and Jackson's default bean introspection treats that as a JavaBean-style
// boolean getter for a property called "xxx" - it strips the "is" prefix when serializing to
// JSON regardless of what the Kotlin source calls it. Naming it "outgoing" here keeps the
// Kotlin property name and the actual wire field name honestly in sync instead of silently
// diverging (which is exactly what happened before this was caught).
data class ConversationMessageDto(
    val id: String,
    val timestamp: String,
    val text: String,
    val outgoing: Boolean,
)

data class SendReplyRequest(val text: String)

/**
 * Private-conversation view of the feed: recently active 1:1 chats, a single chat's message
 * thread (both directions), and sending a reply into it. Scoped to private chats only - see the
 * project's own notes on why group/channel senders don't get this (replying there would mean
 * opening an unsolicited new private chat with a stranger, a different and riskier action).
 *
 * Token auth on requests under /api/messages is enforced by FeedAccessFilter (see
 * FeedSecurityConfig), not here.
 */
@RestController
@RequestMapping("/api/messages")
class SendersController(
    private val feedItemRepository: FeedItemRepository,
    private val telegramClientHolder: TelegramClientHolder,
    private val chatHistoryBackfiller: ChatHistoryBackfiller,
) {

    private val log = LoggerFactory.getLogger(SendersController::class.java)

    @GetMapping("/senders")
    fun recentSenders(@RequestParam(defaultValue = "10") limit: Int): List<SenderSummaryDto> =
        feedItemRepository.findRecentSenders(limit).map {
            SenderSummaryDto(
                chatId = it.chatId,
                displayName = it.displayName,
                username = it.username,
                lastMessageAt = it.lastMessageAt.toString(),
            )
        }

    @GetMapping("/senders/{chatId}")
    fun conversation(@PathVariable chatId: Long, @RequestParam(defaultValue = "20") limit: Int): List<ConversationMessageDto> {
        try {
            chatHistoryBackfiller.ensureBackfilled(telegramClientHolder.requireClient(), chatId, limit)
        } catch (ex: Exception) {
            // Degrade gracefully - show whatever's already stored rather than fail the whole
            // page over a backfill hiccup (TDLib client not ready yet, a slow/timed-out history
            // fetch, etc.). The live ingestion path is unaffected either way.
            log.warn("History backfill failed for chat {} - showing already-stored messages only", chatId, ex)
        }

        return feedItemRepository.findMessagesForChat(chatId, limit)
            .sortedBy { it.occurredAt } // chronological (oldest first) for a chat-thread reading order
            .map {
                ConversationMessageDto(
                    id = it.externalId,
                    timestamp = it.occurredAt.toString(),
                    text = it.text,
                    outgoing = it.isOutgoing,
                )
            }
    }

    @PostMapping("/senders/{chatId}/reply")
    fun reply(@PathVariable chatId: Long, @RequestBody request: SendReplyRequest): ConversationMessageDto {
        val sent = telegramClientHolder.sendMessage(chatId, request.text).get(30, TimeUnit.SECONDS)
        val occurredAt = Instant.ofEpochSecond(sent.date.toLong())

        // Persisted here immediately rather than waiting for the ingestion pipeline's own
        // UpdateNewMessage handling to (also) store it, so the reply shows up in the same request/
        // response cycle instead of racing an async update - insert() is idempotent via the
        // (chat_id, message_id) unique constraint, so no duplicate row when that update also arrives.
        feedItemRepository.insert(
            NewFeedItem(
                externalId = "tg:$chatId:${sent.id}",
                chatId = chatId,
                messageId = sent.id,
                chatType = ChatCategory.PRIVATE,
                fromDisplayName = null,
                fromUsername = null,
                occurredAt = occurredAt,
                text = request.text,
                isOutgoing = true,
            )
        )

        return ConversationMessageDto(
            id = "tg:$chatId:${sent.id}",
            timestamp = occurredAt.toString(),
            text = request.text,
            outgoing = true,
        )
    }
}
