package com.durka.backend.telegram.feed

import com.durka.backend.telegram.TelegramClientHolder
import it.tdlight.jni.TdApi
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.util.concurrent.TimeUnit

data class SenderSummaryDto(
    val chatId: Long,
    val chatType: String,
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
    val fromDisplayName: String?,
)

data class ConversationDto(
    val chatId: Long,
    val chatType: String,
    val title: String?,
    // Private and group chats can be sent into unambiguously (chat_id says exactly where);
    // channels can't - posting there needs elevated permissions and isn't really a "reply" to
    // begin with, same reasoning as excluding channel senders from the reply feature entirely.
    // Computed backend-side so the frontend doesn't need to duplicate this rule.
    val canReply: Boolean,
    val messages: List<ConversationMessageDto>,
)

data class SendReplyRequest(val text: String)

/**
 * Conversation view of the feed: recently active chats across all types, a single chat's message
 * thread, and (for private/group chats only) sending a reply into it.
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
                chatType = it.chatType.name.lowercase(),
                displayName = it.displayName,
                username = it.username,
                lastMessageAt = it.lastMessageAt.toString(),
            )
        }

    @GetMapping("/senders/{chatId}")
    fun conversation(@PathVariable chatId: Long, @RequestParam(defaultValue = "25") limit: Int): ConversationDto {
        val client = telegramClientHolder.requireClient()

        try {
            chatHistoryBackfiller.ensureBackfilled(client, chatId, limit)
        } catch (ex: Exception) {
            // Degrade gracefully - show whatever's already stored rather than fail the whole
            // page over a backfill hiccup (TDLib client not ready yet, a slow/timed-out history
            // fetch, etc.). The live ingestion path is unaffected either way.
            log.warn("History backfill failed for chat {} - showing already-stored messages only", chatId, ex)
        }

        val chat = client.send(TdApi.GetChat(chatId)).get(10, TimeUnit.SECONDS)
        val chatCategory = ChatCategory.from(chat.type)

        val messages = feedItemRepository.findMessagesForChat(chatId, limit)
            .sortedBy { it.occurredAt } // chronological (oldest first) for a chat-thread reading order
            .map {
                ConversationMessageDto(
                    id = it.externalId,
                    timestamp = it.occurredAt.toString(),
                    text = it.text,
                    outgoing = it.isOutgoing,
                    fromDisplayName = it.fromDisplayName,
                )
            }

        return ConversationDto(
            chatId = chatId,
            chatType = chatCategory.name.lowercase(),
            title = chat.title,
            canReply = chatCategory != ChatCategory.CHANNEL,
            messages = messages,
        )
    }

    @PostMapping("/senders/{chatId}/reply")
    fun reply(@PathVariable chatId: Long, @RequestBody request: SendReplyRequest): ConversationMessageDto {
        val client = telegramClientHolder.requireClient()

        // Defense in depth, not just a hidden UI element: reject channel sends server-side too,
        // in case anything ever posts here directly without going through the frontend's form.
        val chat = client.send(TdApi.GetChat(chatId)).get(10, TimeUnit.SECONDS)
        if (ChatCategory.from(chat.type) == ChatCategory.CHANNEL) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot reply to a channel")
        }

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
                chatType = ChatCategory.from(chat.type),
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
            fromDisplayName = null,
        )
    }
}
