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
)

data class FeedItemRow(
    val id: Long,
    val externalId: String,
    val chatType: ChatCategory,
    val fromDisplayName: String?,
    val fromUsername: String?,
    val occurredAt: Instant,
    val text: String,
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

        jdbcTemplate.update(
            """
            INSERT INTO feed_item (external_id, chat_id, message_id, chat_type, from_display_name, from_username, occurred_at, text)
            VALUES (:externalId, :chatId, :messageId, :chatType, :fromDisplayName, :fromUsername, :occurredAt, :text)
            ON CONFLICT (chat_id, message_id) DO NOTHING
            """.trimIndent(),
            params,
        )
    }

    /**
     * Latest N messages regardless of is_seen - read/unread tracking is deferred, this just
     * serves a simple "last N" feed for now (is_seen stays in the schema, unused, for later).
     */
    fun findRecent(limit: Int): List<FeedItemRow> =
        jdbcTemplate.query(
            """
            SELECT id, external_id, chat_type, from_display_name, from_username, occurred_at, text
            FROM feed_item
            ORDER BY occurred_at DESC
            LIMIT :limit
            """.trimIndent(),
            MapSqlParameterSource("limit", limit),
        ) { rs, _ ->
            FeedItemRow(
                id = rs.getLong("id"),
                externalId = rs.getString("external_id"),
                chatType = ChatCategory.valueOf(rs.getString("chat_type")),
                fromDisplayName = rs.getString("from_display_name"),
                fromUsername = rs.getString("from_username"),
                occurredAt = rs.getTimestamp("occurred_at").toInstant(),
                text = rs.getString("text"),
            )
        }
}
