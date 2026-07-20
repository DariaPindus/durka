package com.durka.backend.notes

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.time.LocalDate

data class NoteDto(
    val id: Long,
    val title: String,
    val content: String,
    val createdAt: String,
    val updatedAt: String,
)

data class CreateNoteRequest(val title: String, val content: String)
data class UpdateNoteRequest(val title: String, val content: String)

data class TaskEventDto(
    val id: Long,
    val type: TaskEventType,
    val occursAt: String,
    val description: String,
    val durationMinutes: Int?,
)

data class CreateTaskEventRequest(
    val type: TaskEventType,
    val occursAt: String,
    val description: String,
    val durationMinutes: Int? = null,
)

data class TaskDateSummaryDto(val date: String, val count: Int)

/** Token auth on requests under /api is enforced by FeedAccessFilter (see FeedSecurityConfig). */
@RestController
@RequestMapping("/api/notes")
class NotesController(
    private val noteRepository: NoteRepository,
    private val taskEventRepository: TaskEventRepository,
) {

    @GetMapping("/notes")
    fun notes(): List<NoteDto> = noteRepository.findAll().map { it.toDto() }

    @PostMapping("/notes")
    fun createNote(@RequestBody request: CreateNoteRequest): NoteDto {
        val id = noteRepository.insert(NewNote(request.title, request.content))
        return noteRepository.findById(id)!!.toDto()
    }

    @PutMapping("/notes/{id}")
    fun updateNote(@PathVariable id: Long, @RequestBody request: UpdateNoteRequest): NoteDto {
        val updated = noteRepository.update(id, NewNote(request.title, request.content))
        if (!updated) throw ResponseStatusException(HttpStatus.NOT_FOUND, "Note not found")
        return noteRepository.findById(id)!!.toDto()
    }

    @GetMapping("/tasks/dates")
    fun taskDates(): List<TaskDateSummaryDto> =
        taskEventRepository.findDates().map { TaskDateSummaryDto(date = it.date.toString(), count = it.count) }

    @GetMapping("/tasks")
    fun tasksForDate(@RequestParam date: String): List<TaskEventDto> =
        taskEventRepository.findByDate(LocalDate.parse(date)).map { it.toDto() }

    @PostMapping("/tasks")
    fun createTaskEvent(@RequestBody request: CreateTaskEventRequest): TaskEventDto {
        val id = taskEventRepository.insert(
            NewTaskEvent(
                type = request.type,
                occursAt = Instant.parse(request.occursAt),
                description = request.description,
                durationMinutes = request.durationMinutes,
            ),
        )
        return taskEventRepository.findById(id)!!.toDto()
    }

    private fun NoteRow.toDto() = NoteDto(id, title, content, createdAt.toString(), updatedAt.toString())

    private fun TaskEventRow.toDto() = TaskEventDto(id, type, occursAt.toString(), description, durationMinutes)
}
