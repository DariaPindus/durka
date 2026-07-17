package com.durka.backend.rss

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
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
)

data class RssItemRow(
    val feedUrl: String,
    val feedTitle: String?,
    val title: String?,
    val link: String?,
    val publishedAt: Instant,
)

@Repository
class RssItemRepository(private val jdbcTemplate: NamedParameterJdbcTemplate) {

    /** No-ops on a duplicate (feed_url, external_id) - re-polling the same feed re-sees old entries. */
    fun insert(item: NewRssItem) {
        val params = MapSqlParameterSource()
            .addValue("feedUrl", item.feedUrl)
            .addValue("feedTitle", item.feedTitle)
            .addValue("externalId", item.externalId)
            .addValue("title", item.title)
            .addValue("link", item.link)
            .addValue("publishedAt", Timestamp.from(item.publishedAt))

        jdbcTemplate.update(
            """
            INSERT INTO rss_item (feed_url, feed_title, external_id, title, link, published_at)
            VALUES (:feedUrl, :feedTitle, :externalId, :title, :link, :publishedAt)
            ON CONFLICT (feed_url, external_id) DO NOTHING
            """.trimIndent(),
            params,
        )
    }

    /**
     * Latest N entries per feed source, not an overall top-N - one very active feed shouldn't be
     * able to crowd quieter ones out of the result entirely (same reasoning as the Telegram
     * side's findRecent). ROW_NUMBER() partitioned by feed_url gives an exact top-N per source
     * directly from the DB.
     */
    fun findRecent(limitPerFeed: Int): List<RssItemRow> =
        jdbcTemplate.query(
            """
            SELECT feed_url, feed_title, title, link, published_at
            FROM (
                SELECT *,
                       ROW_NUMBER() OVER (
                           PARTITION BY feed_url
                           ORDER BY published_at DESC
                       ) AS rn
                FROM rss_item
            ) ranked
            WHERE rn <= :limitPerFeed
            ORDER BY feed_url, published_at DESC
            """.trimIndent(),
            MapSqlParameterSource("limitPerFeed", limitPerFeed),
        ) { rs, _ ->
            RssItemRow(
                feedUrl = rs.getString("feed_url"),
                feedTitle = rs.getString("feed_title"),
                title = rs.getString("title"),
                link = rs.getString("link"),
                publishedAt = rs.getTimestamp("published_at").toInstant(),
            )
        }
}
