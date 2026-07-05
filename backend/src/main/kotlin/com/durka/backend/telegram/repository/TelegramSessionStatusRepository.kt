package com.durka.backend.telegram.repository

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.time.Instant

data class TelegramSessionRow(
    val phoneNumber: String,
    val telegramUserId: Long?,
    val authorizationState: String,
)

/**
 * Bookkeeping only - phone number, Telegram user id, and last-verified timestamp for observability.
 * The actual session credential lives entirely in TDLib's own local database directory, never here.
 */
@Repository
class TelegramSessionStatusRepository(private val jdbcTemplate: NamedParameterJdbcTemplate) {

    fun findSession(): TelegramSessionRow? {
        val rows = jdbcTemplate.query(
            """
            SELECT phone_number, telegram_user_id, authorization_state
            FROM telegram_session_status
            ORDER BY updated_at DESC
            LIMIT 1
            """.trimIndent(),
            emptyMap<String, Any>(),
        ) { rs, _ ->
            val userId = rs.getLong("telegram_user_id")
            TelegramSessionRow(
                phoneNumber = rs.getString("phone_number"),
                telegramUserId = if (rs.wasNull()) null else userId,
                authorizationState = rs.getString("authorization_state"),
            )
        }
        return rows.firstOrNull()
    }

    fun upsertReady(phoneNumber: String, telegramUserId: Long, verifiedAt: Instant) {
        val params = MapSqlParameterSource()
            .addValue("phoneNumber", phoneNumber)
            .addValue("telegramUserId", telegramUserId)
            .addValue("state", "authorizationStateReady")
            .addValue("verifiedAt", Timestamp.from(verifiedAt))

        jdbcTemplate.update(
            """
            INSERT INTO telegram_session_status (phone_number, telegram_user_id, authorization_state, last_verified_at, updated_at)
            VALUES (:phoneNumber, :telegramUserId, :state, :verifiedAt, now())
            ON CONFLICT (phone_number) DO UPDATE SET
                telegram_user_id = EXCLUDED.telegram_user_id,
                authorization_state = EXCLUDED.authorization_state,
                last_verified_at = EXCLUDED.last_verified_at,
                updated_at = now()
            """.trimIndent(),
            params,
        )
    }
}
