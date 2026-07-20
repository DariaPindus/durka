package com.durka.backend.notes

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import java.time.Instant

data class NewNote(val title: String, val content: String)

data class NoteRow(
    val id: Long,
    val title: String,
    val content: String,
    val createdAt: Instant,
    val updatedAt: Instant,
)

@Repository
class NoteRepository(private val jdbcTemplate: NamedParameterJdbcTemplate) {

    fun insert(note: NewNote): Long {
        val params = MapSqlParameterSource()
            .addValue("title", note.title)
            .addValue("content", note.content)

        return jdbcTemplate.queryForObject(
            "INSERT INTO note (title, content) VALUES (:title, :content) RETURNING id",
            params,
            Long::class.java,
        )!!
    }

    /** updated_at is bumped to now() here, not passed in - it always means "when this was saved". */
    fun update(id: Long, note: NewNote): Boolean {
        val params = MapSqlParameterSource()
            .addValue("id", id)
            .addValue("title", note.title)
            .addValue("content", note.content)

        val rowsUpdated = jdbcTemplate.update(
            "UPDATE note SET title = :title, content = :content, updated_at = now() WHERE id = :id",
            params,
        )
        return rowsUpdated > 0
    }

    /** Most recently edited first - the natural order for a personal scratch-notes list. */
    fun findAll(): List<NoteRow> =
        jdbcTemplate.query(
            "SELECT id, title, content, created_at, updated_at FROM note ORDER BY updated_at DESC",
            MapSqlParameterSource(),
        ) { rs, _ -> mapRow(rs) }

    fun findById(id: Long): NoteRow? =
        jdbcTemplate.query(
            "SELECT id, title, content, created_at, updated_at FROM note WHERE id = :id",
            MapSqlParameterSource("id", id),
        ) { rs, _ -> mapRow(rs) }.firstOrNull()

    private fun mapRow(rs: java.sql.ResultSet): NoteRow = NoteRow(
        id = rs.getLong("id"),
        title = rs.getString("title"),
        content = rs.getString("content"),
        createdAt = rs.getTimestamp("created_at").toInstant(),
        updatedAt = rs.getTimestamp("updated_at").toInstant(),
    )
}
