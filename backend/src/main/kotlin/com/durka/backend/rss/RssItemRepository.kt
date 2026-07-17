package com.durka.backend.rss

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.time.Instant

data class NewRssItem(
    val feedUrl: String,
    val feedTitle: String?,
    val externalId: String,
    val title: String?,
    val link: String?,
    val publishedAt: Instant,
    val description: String?,
)

data class RssItemRow(
    val id: Long,
    val feedUrl: String,
    val feedTitle: String?,
    val title: String?,
    val link: String?,
    val publishedAt: Instant,
    val description: String?,
)

data class RssFeedSummary(
    val feedUrl: String,
    val feedTitle: String?,
    val itemCount: Int,
    val lastPublishedAt: Instant,
)

private val ROW_MAPPER = RowMapper { rs, _ ->
    RssItemRow(
        id = rs.getLong("id"),
        feedUrl = rs.getString("feed_url"),
        feedTitle = rs.getString("feed_title"),
        title = rs.getString("title"),
        link = rs.getString("link"),
        publishedAt = rs.getTimestamp("published_at").toInstant(),
        description = rs.getString("description"),
    )
}

@Repository
class RssItemRepository(private val jdbcTemplate: NamedParameterJdbcTemplate) {

    /**
     * Upserts on a duplicate (feed_url, external_id): title/link/description are refreshed on
     * every re-poll (a feed can edit a published entry), but external_id/published_at - the
     * identity and original publish time - never change on conflict.
     */
    fun insert(item: NewRssItem) {
        val params = MapSqlParameterSource()
            .addValue("feedUrl", item.feedUrl)
            .addValue("feedTitle", item.feedTitle)
            .addValue("externalId", item.externalId)
            .addValue("title", item.title)
            .addValue("link", item.link)
            .addValue("publishedAt", Timestamp.from(item.publishedAt))
            .addValue("description", item.description)

        jdbcTemplate.update(
            """
            INSERT INTO rss_item (feed_url, feed_title, external_id, title, link, published_at, description)
            VALUES (:feedUrl, :feedTitle, :externalId, :title, :link, :publishedAt, :description)
            ON CONFLICT (feed_url, external_id) DO UPDATE SET
                feed_title = EXCLUDED.feed_title,
                title = EXCLUDED.title,
                link = EXCLUDED.link,
                description = EXCLUDED.description
            """.trimIndent(),
            params,
        )
    }

    /** One row per distinct feed, for the top-level "list of feeds" page. */
    fun findFeeds(): List<RssFeedSummary> =
        jdbcTemplate.query(
            """
            SELECT feed_url, feed_title, COUNT(*) AS item_count, MAX(published_at) AS last_published_at
            FROM rss_item
            GROUP BY feed_url, feed_title
            ORDER BY MAX(published_at) DESC
            """.trimIndent(),
            MapSqlParameterSource(),
        ) { rs, _ ->
            RssFeedSummary(
                feedUrl = rs.getString("feed_url"),
                feedTitle = rs.getString("feed_title"),
                itemCount = rs.getInt("item_count"),
                lastPublishedAt = rs.getTimestamp("last_published_at").toInstant(),
            )
        }

    /** Last N entries for one specific feed - the "topics in this feed" page. */
    fun findByFeed(feedUrl: String, limit: Int): List<RssItemRow> =
        jdbcTemplate.query(
            """
            SELECT id, feed_url, feed_title, title, link, published_at, description
            FROM rss_item
            WHERE feed_url = :feedUrl
            ORDER BY published_at DESC
            LIMIT :limit
            """.trimIndent(),
            MapSqlParameterSource().addValue("feedUrl", feedUrl).addValue("limit", limit),
            ROW_MAPPER,
        )

    /** Single entry with its full description - the "topic content" page. */
    fun findById(id: Long): RssItemRow? =
        jdbcTemplate.query(
            """
            SELECT id, feed_url, feed_title, title, link, published_at, description
            FROM rss_item
            WHERE id = :id
            """.trimIndent(),
            MapSqlParameterSource("id", id),
            ROW_MAPPER,
        ).firstOrNull()
}
