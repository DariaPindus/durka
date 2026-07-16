package com.durka.backend.telegram.feed

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

data class MessageDto(
    val id: String,
    val timestamp: String,
    val text: String,
)

data class AuthorGroupDto(
    val displayName: String?,
    val username: String?,
    val messages: List<MessageDto>,
)

data class ChatTypeGroupDto(
    val chatType: String,
    val authors: List<AuthorGroupDto>,
)

/**
 * Token auth on requests under /api/messages is enforced by FeedAccessFilter (see
 * FeedSecurityConfig), not here - keeps this controller free of auth concerns for any
 * future endpoint added under the same path.
 */
@RestController
@RequestMapping("/api/messages")
class MessagesController(private val feedItemRepository: FeedItemRepository) {

    @GetMapping("/recent")
    fun recent(@RequestParam(defaultValue = "20") limit: Int): List<ChatTypeGroupDto> {
        // limit is per author group (see FeedItemRepository.findRecent), not an overall cap.
        val items = feedItemRepository.findRecent(limit)

        return items
            .groupBy { it.chatType }
            .toSortedMap(compareBy { chatTypeOrder(it) })
            .map { (chatType, itemsForType) ->
                ChatTypeGroupDto(
                    chatType = chatType.name.lowercase(),
                    authors = itemsForType
                        .groupBy { it.fromDisplayName to it.fromUsername }
                        .map { (author, itemsForAuthor) ->
                            AuthorGroupDto(
                                displayName = author.first,
                                username = author.second,
                                messages = itemsForAuthor
                                    .sortedByDescending { it.occurredAt }
                                    .map { MessageDto(id = it.externalId, timestamp = it.occurredAt.toString(), text = it.text) },
                            )
                        }
                        // authors with the most recent message first
                        .sortedByDescending { it.messages.first().timestamp },
                )
            }
    }

    private fun chatTypeOrder(chatType: ChatCategory): Int = when (chatType) {
        ChatCategory.PRIVATE -> 0
        ChatCategory.GROUP -> 1
        ChatCategory.CHANNEL -> 2
    }
}
