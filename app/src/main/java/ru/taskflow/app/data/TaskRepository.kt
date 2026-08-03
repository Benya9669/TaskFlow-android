package ru.taskflow.app.data

import androidx.room.withTransaction
import com.squareup.moshi.Moshi
import ru.taskflow.app.data.local.PendingMutationEntity
import ru.taskflow.app.data.local.TaskEntity
import ru.taskflow.app.data.local.TaskFlowDatabase
import ru.taskflow.app.data.local.KanbanColumnEntity
import ru.taskflow.app.data.local.ProjectEntity
import ru.taskflow.app.data.local.SyncStateEntity
import ru.taskflow.app.data.remote.KanbanColumnDto
import ru.taskflow.app.data.remote.ProjectDto
import ru.taskflow.app.data.remote.TaskDto
import java.time.Instant
import java.util.UUID

class TaskRepository(private val database: TaskFlowDatabase) {
    val tasks = database.taskDao().observeActive()

    suspend fun syncCursor(): String = database.syncStateDao().cursor(SYNC_CURSOR_KEY) ?: FIRST_SYNC_CURSOR

    suspend fun applySyncPage(tasks: List<TaskDto>, projects: List<ProjectDto>, columns: List<KanbanColumnDto>, cursor: String) {
        database.withTransaction {
            database.taskDao().upsertAll(tasks.map(TaskDto::toEntity))
            database.projectDao().upsertAll(projects.map(ProjectDto::toEntity))
            database.kanbanColumnDao().upsertAll(columns.map(KanbanColumnDto::toEntity))
            database.syncStateDao().put(SyncStateEntity(SYNC_CURSOR_KEY, cursor))
        }
    }

    suspend fun createLocalTask(ownerId: String, columnId: String, title: String): TaskEntity {
        val now = Instant.now().toString()
        val task = TaskEntity(
            id = UUID.randomUUID().toString(), ownerId = ownerId, projectId = null, columnId = columnId,
            title = title.trim(), description = "", status = "inbox", priority = "normal", scheduledDate = null,
            dueAt = null, estimatedMinutes = null, kanbanPosition = Int.MAX_VALUE, recurrence = null,
            reminderOffsets = emptyList(), tags = emptyList(), createdAt = now, updatedAt = now, version = 1, deletedAt = null,
        )
        val body = mapOf("title" to task.title, "column_id" to task.columnId, "status" to task.status, "priority" to task.priority)
        database.withTransaction {
            database.taskDao().upsert(task)
            database.mutationDao().insert(PendingMutationEntity(UUID.randomUUID().toString(), "create", task.id, moshi.adapter(Map::class.java).toJson(body), System.currentTimeMillis()))
        }
        return task
    }

    suspend fun createInboxTask(title: String): TaskEntity {
        val column = checkNotNull(database.kanbanColumnDao().inbox()) { "Сначала дождитесь синхронизации колонок" }
        return createLocalTask(column.ownerId, column.id, title)
    }

    suspend fun pendingMutations(limit: Int) = database.mutationDao().nextBatch(limit)
    suspend fun removeMutations(ids: List<String>) = database.mutationDao().delete(ids)

    private companion object {
        const val SYNC_CURSOR_KEY = "main"
        const val FIRST_SYNC_CURSOR = "1970-01-01T00:00:00.000Z"
        val moshi = Moshi.Builder().build()
    }
}

private fun TaskDto.toEntity() = TaskEntity(id, ownerId, projectId, columnId, title, description, status, priority, scheduledDate, dueAt, estimatedMinutes, kanbanPosition, recurrence, reminderOffsets, tags, createdAt, updatedAt, version, deletedAt)
private fun ProjectDto.toEntity() = ProjectEntity(id, ownerId, name, color, createdAt, updatedAt, version, deletedAt, archivedAt)
private fun KanbanColumnDto.toEntity() = KanbanColumnEntity(id, ownerId, name, color, semanticStatus, position, createdAt, updatedAt, version, deletedAt)
