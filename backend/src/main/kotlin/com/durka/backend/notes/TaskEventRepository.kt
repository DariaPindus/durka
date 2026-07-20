package com.durka.backend.notes

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.Date
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate

enum class TaskEventType { TASK, EVENT }

data class NewTaskEvent(
    val type: TaskEventType,
    val occursAt: Instant,
    val description: String,
    val durationMinutes: Int?,
)

data class TaskEventRow(
    val id: Long,
    val type: TaskEventType,
    val occursAt: Instant,
    val description: String,
    val durationMinutes: Int?,
)

data class TaskDateSummary(val date: LocalDate, val count: Int)

@Repository
class TaskEventRepository(private val jdbcTemplate: NamedParameterJdbcTemplate) {

    fun insert(item: NewTaskEvent): Long {
        val params = MapSqlParameterSource()
            .addValue("type", item.type.name)
            .addValue("occursAt", Timestamp.from(item.occursAt))
            .addValue("description", item.description)
            .addValue("durationMinutes", item.durationMinutes)

        return jdbcTemplate.queryForObject(
            """
            INSERT INTO task_event (type, occurs_at, description, duration_minutes)
            VALUES (:type, :occursAt, :description, :durationMinutes)
            RETURNING id
            """.trimIndent(),
            params,
            Long::class.java,
        )!!
    }

    /**
     * One row per calendar date that has at least one task/event, for the top-level "list of
     * dates" page - same shape as RssItemRepository.findFeeds(). Dates are the timestamp's own
     * calendar date with no timezone conversion, consistent with the rest of this app (Postgres
     * session runs UTC, nothing here does explicit AT TIME ZONE handling).
     */
    fun findDates(limit: Int): List<TaskDateSummary> =
        jdbcTemplate.query(
            """
            SELECT occurs_at::date AS day, COUNT(*) AS cnt
            FROM task_event
            GROUP BY day
            ORDER BY day
            LIMIT :limit
            """.trimIndent(),
            MapSqlParameterSource("limit", limit),
        ) { rs, _ -> TaskDateSummary(date = rs.getDate("day").toLocalDate(), count = rs.getInt("cnt")) }

    /** All tasks/events on one specific calendar date, time-ordered - the "day view" page. */
    fun findByDate(date: LocalDate): List<TaskEventRow> =
        jdbcTemplate.query(
            """
            SELECT id, type, occurs_at, description, duration_minutes
            FROM task_event
            WHERE occurs_at::date = :date
            ORDER BY occurs_at
            """.trimIndent(),
            MapSqlParameterSource("date", Date.valueOf(date)),
        ) { rs, _ -> mapRow(rs) }

    fun findById(id: Long): TaskEventRow? =
        jdbcTemplate.query(
            "SELECT id, type, occurs_at, description, duration_minutes FROM task_event WHERE id = :id",
            MapSqlParameterSource("id", id),
        ) { rs, _ -> mapRow(rs) }.firstOrNull()

    private fun mapRow(rs: java.sql.ResultSet): TaskEventRow {
        val duration = rs.getInt("duration_minutes").let { if (rs.wasNull()) null else it }
        return TaskEventRow(
            id = rs.getLong("id"),
            type = TaskEventType.valueOf(rs.getString("type")),
            occursAt = rs.getTimestamp("occurs_at").toInstant(),
            description = rs.getString("description"),
            durationMinutes = duration,
        )
    }
}
