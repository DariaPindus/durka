package com.durka.backend.telegram.feed

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository

/**
 * Every request to a token-protected endpoint, valid or not - the raw material for a future
 * "notify me if a new IP/device uses this token" feature (not built yet, this session only
 * captures the data it would need: distinct IPs seen, first-seen time, valid vs invalid attempts).
 */
@Repository
class FeedAccessLogRepository(private val jdbcTemplate: NamedParameterJdbcTemplate) {

    fun record(ipAddress: String, userAgent: String?, path: String, tokenValid: Boolean) {
        val params = MapSqlParameterSource()
            .addValue("ipAddress", ipAddress)
            .addValue("userAgent", userAgent)
            .addValue("path", path)
            .addValue("tokenValid", tokenValid)

        jdbcTemplate.update(
            """
            INSERT INTO feed_access_log (ip_address, user_agent, path, token_valid)
            VALUES (:ipAddress, :userAgent, :path, :tokenValid)
            """.trimIndent(),
            params,
        )
    }
}
