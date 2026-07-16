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
     * Latest N messages per (chat_type, author) group, regardless of is_seen - read/unread
     * tracking is deferred (is_seen stays in the schema, unused, for later). Per-group rather
     * than an overall top-N so a handful of very active chats can't crowd quieter ones out of
     * the result entirely - ROW_NUMBER() partitioned by the same (chat_type, author) key the
     * controller groups by gives an exact top-N per group directly from the DB, rather than
     * approximating it by over-fetching and truncating in application code.
     */
    fun findRecent(limitPerGroup: Int): List<FeedItemRow> =
        jdbcTemplate.query(
            """
            SELECT id, external_id, chat_type, from_display_name, from_username, occurred_at, text
            FROM (
                SELECT *,
                       ROW_NUMBER() OVER (
                           PARTITION BY chat_type
                           ORDER BY occurred_at DESC
                       ) AS rn
                FROM feed_item
            ) ranked
            WHERE rn <= :limitPerGroup
            ORDER BY occurred_at DESC
            """.trimIndent(),
            MapSqlParameterSource("limitPerGroup", limitPerGroup),
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
