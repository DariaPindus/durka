package com.durka.backend.telegram.feed

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.time.Instant

data class NewFeedItem(
    val externalId: String,
    val chatId: Long,
    val messageId: Long,
    val chatType: ChatCategory,
    val fromDisplayName: String?,
    val fromUsername: String?,
    val occurredAt: Instant,
    val text: String,
    val isOutgoing: Boolean = false,
)

data class FeedItemRow(
    val id: Long,
    val externalId: String,
    val chatType: ChatCategory,
    val fromDisplayName: String?,
    val fromUsername: String?,
    val occurredAt: Instant,
    val text: String,
    val isOutgoing: Boolean,
)

data class SenderSummaryRow(
    val chatId: Long,
    val displayName: String?,
    val username: String?,
    val lastMessageAt: Instant,
)

@Repository
class FeedItemRepository(private val jdbcTemplate: NamedParameterJdbcTemplate) {

    /** No-ops on a duplicate (chat_id, message_id) - TDLib may redeliver updates on reconnect. */
    fun insert(item: NewFeedItem) {
        val params = MapSqlParameterSource()
            .addValue("externalId", item.externalId)
            .addValue("chatId", item.chatId)
            .addValue("messageId", item.messageId)
            .addValue("chatType", item.chatType.name)
            .addValue("fromDisplayName", item.fromDisplayName)
            .addValue("fromUsername", item.fromUsername)
            .addValue("occurredAt", Timestamp.from(item.occurredAt))
            .addValue("text", item.text)
            .addValue("isOutgoing", item.isOutgoing)

        jdbcTemplate.update(
            """
            INSERT INTO feed_item (external_id, chat_id, message_id, chat_type, from_display_name, from_username, occurred_at, text, is_outgoing)
            VALUES (:externalId, :chatId, :messageId, :chatType, :fromDisplayName, :fromUsername, :occurredAt, :text, :isOutgoing)
            ON CONFLICT (chat_id, message_id) DO NOTHING
            """.trimIndent(),
            params,
        )
    }

    /**
     * Latest N messages per chat_type group, excluding our own outgoing messages - this is the
     * "what did people send me" view, unaffected by the private-conversation reply feature
     * (see findMessagesForChat, which is the only place outgoing messages are shown).
     */
    fun findRecent(limitPerGroup: Int): List<FeedItemRow> =
        jdbcTemplate.query(
            """
            SELECT id, external_id, chat_type, from_display_name, from_username, occurred_at, text, is_outgoing
            FROM (
                SELECT *,
                       ROW_NUMBER() OVER (
                           PARTITION BY chat_type
                           ORDER BY occurred_at DESC
                       ) AS rn
                FROM feed_item
                WHERE is_outgoing = false
            ) ranked
            WHERE rn <= :limitPerGroup
            ORDER BY occurred_at DESC
            """.trimIndent(),
            MapSqlParameterSource("limitPerGroup", limitPerGroup),
        ) { rs, _ -> mapRow(rs) }

    /**
     * The N most recently-active private conversations, one row per chat_id (its most recent
     * message, whichever direction). Private only, per the reply feature's scope - chat_id here
     * doubles as the id you'd send a reply to, since a private chat_id unambiguously identifies
     * the conversation with that one person.
     */
    fun findRecentSenders(limit: Int): List<SenderSummaryRow> =
        jdbcTemplate.query(
            """
            SELECT chat_id, from_display_name, from_username, occurred_at
            FROM (
                SELECT DISTINCT ON (chat_id) chat_id, from_display_name, from_username, occurred_at
                FROM feed_item
                WHERE chat_type = 'PRIVATE'
                ORDER BY chat_id, occurred_at DESC
            ) latest
            ORDER BY occurred_at DESC
            LIMIT :limit
            """.trimIndent(),
            MapSqlParameterSource("limit", limit),
        ) { rs, _ ->
            SenderSummaryRow(
                chatId = rs.getLong("chat_id"),
                displayName = rs.getString("from_display_name"),
                username = rs.getString("from_username"),
                lastMessageAt = rs.getTimestamp("occurred_at").toInstant(),
            )
        }

    /** Last N messages in one specific chat, both directions - the conversation-thread view. */
    fun findMessagesForChat(chatId: Long, limit: Int): List<FeedItemRow> =
        jdbcTemplate.query(
            """
            SELECT id, external_id, chat_type, from_display_name, from_username, occurred_at, text, is_outgoing
            FROM feed_item
            WHERE chat_id = :chatId
            ORDER BY occurred_at DESC
            LIMIT :limit
            """.trimIndent(),
            MapSqlParameterSource()
                .addValue("chatId", chatId)
                .addValue("limit", limit),
        ) { rs, _ -> mapRow(rs) }

    /** How many messages we already have for a chat - shown in logs, not used to gate backfill. */
    fun countForChat(chatId: Long): Int =
        jdbcTemplate.query(
            "SELECT COUNT(*) AS cnt FROM feed_item WHERE chat_id = :chatId",
            MapSqlParameterSource("chatId", chatId),
        ) { rs, _ -> rs.getInt("cnt") }.first()

    /**
     * Whether a real GetChatHistory backfill has ever completed for this chat - the actual gate
     * for ChatHistoryBackfiller, not a row count (an active chat can easily already have
     * enough *incoming* rows from before outgoing messages were even stored, which would make a
     * count-based check falsely conclude nothing needs backfilling).
     */
    fun hasBackfilled(chatId: Long): Boolean =
        jdbcTemplate.query(
            "SELECT 1 FROM chat_backfill_status WHERE chat_id = :chatId",
            MapSqlParameterSource("chatId", chatId),
        ) { rs, _ -> rs.getInt(1) }.isNotEmpty()

    fun markBackfilled(chatId: Long) {
        jdbcTemplate.update(
            """
            INSERT INTO chat_backfill_status (chat_id) VALUES (:chatId)
            ON CONFLICT (chat_id) DO NOTHING
            """.trimIndent(),
            MapSqlParameterSource("chatId", chatId),
        )
    }

    private fun mapRow(rs: java.sql.ResultSet): FeedItemRow = FeedItemRow(
        id = rs.getLong("id"),
        externalId = rs.getString("external_id"),
        chatType = ChatCategory.valueOf(rs.getString("chat_type")),
        fromDisplayName = rs.getString("from_display_name"),
        fromUsername = rs.getString("from_username"),
        occurredAt = rs.getTimestamp("occurred_at").toInstant(),
        text = rs.getString("text"),
        isOutgoing = rs.getBoolean("is_outgoing"),
    )
}
