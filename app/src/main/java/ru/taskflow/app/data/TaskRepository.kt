package ru.taskflow.app.data

import androidx.room.withTransaction
import com.squareup.moshi.Moshi
import ru.taskflow.app.data.local.PendingMutationEntity
import ru.taskflow.app.data.local.TaskEntity
import ru.taskflow.app.data.local.TaskFlowDatabase
import ru.taskflow.app.data.local.KanbanColumnEntity
import ru.taskflow.app.data.local.ProjectEntity
import ru.taskflow.app.data.local.SyncStateEntity
import ru.taskflow.app.data.local.TaskConflictEntity
import ru.taskflow.app.data.remote.KanbanColumnDto
import ru.taskflow.app.data.remote.ProjectDto
import ru.taskflow.app.data.remote.TaskDto
import java.time.Instant
import java.util.UUID

class TaskRepository(private val database: TaskFlowDatabase) {
    val tasks = database.taskDao().observeActive()
    val projects = database.projectDao().observeActive()
    val conflicts = database.taskConflictDao().observeAll()

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

    suspend fun completeLocalTask(taskId: String) {
        val current = checkNotNull(database.taskDao().find(taskId)) { "Задача не найдена" }
        if (current.deletedAt != null || current.status == "done") return
        val doneColumn = checkNotNull(database.kanbanColumnDao().byStatus("done")) { "Сначала дождитесь синхронизации колонок" }
        val updated = current.copy(columnId = doneColumn.id, status = "done", updatedAt = Instant.now().toString())
        enqueueUpdate(updated, mapOf("column_id" to updated.columnId, "status" to "done", "expected_version" to current.version))
    }

    suspend fun deleteLocalTask(taskId: String) {
        val current = checkNotNull(database.taskDao().find(taskId)) { "Задача не найдена" }
        if (current.deletedAt != null) return
        val deleted = current.copy(deletedAt = Instant.now().toString(), updatedAt = Instant.now().toString())
        database.withTransaction {
            database.taskDao().upsert(deleted)
            database.mutationDao().insert(PendingMutationEntity(UUID.randomUUID().toString(), "delete", taskId, null, System.currentTimeMillis()))
        }
    }

    suspend fun updateLocalTask(taskId: String, title: String, priority: String, description: String, projectId: String?, scheduledDate: String?, dueAt: String?) {
        val current = checkNotNull(database.taskDao().find(taskId)) { "Задача не найдена" }
        require(title.isNotBlank()) { "Введите название задачи" }
        require(priority in PRIORITIES) { "Некорректный приоритет" }
        if (current.deletedAt != null) return
        val updated = current.copy(title = title.trim(), priority = priority, description = description.trim(), projectId = projectId, scheduledDate = scheduledDate, dueAt = dueAt, updatedAt = Instant.now().toString())
        enqueueUpdate(updated, mapOf("title" to updated.title, "priority" to updated.priority, "description" to updated.description, "project_id" to updated.projectId, "scheduled_date" to updated.scheduledDate, "due_at" to updated.dueAt, "expected_version" to current.version))
    }

    suspend fun pendingMutations(limit: Int) = database.mutationDao().nextBatch(limit)
    suspend fun removeMutations(ids: List<String>) = database.mutationDao().delete(ids)

    suspend fun saveConflict(mutation: PendingMutationEntity, serverTask: TaskDto) {
        val body = checkNotNull(mutation.bodyJson)
        database.withTransaction {
            database.taskConflictDao().insert(TaskConflictEntity(mutation.id, mutation.taskId, body, serverTask.title, serverTask.priority, System.currentTimeMillis()))
            database.mutationDao().delete(listOf(mutation.id))
        }
    }

    suspend fun keepServerVersion(mutationId: String) = database.taskConflictDao().delete(mutationId)

    suspend fun keepLocalVersion(conflict: TaskConflictEntity) {
        val current = checkNotNull(database.taskDao().find(conflict.taskId)) { "Серверная задача не найдена" }
        val body = mapAdapter.fromJson(conflict.localBodyJson).orEmpty().toMutableMap()
        body["expected_version"] = current.version
        database.withTransaction {
            database.mutationDao().insert(PendingMutationEntity(UUID.randomUUID().toString(), "update", conflict.taskId, mapAdapter.toJson(body), System.currentTimeMillis()))
            database.taskConflictDao().delete(conflict.mutationId)
        }
    }

    private suspend fun enqueueUpdate(task: TaskEntity, body: Map<String, Any?>) {
        database.withTransaction {
            database.taskDao().upsert(task)
            database.mutationDao().insert(PendingMutationEntity(UUID.randomUUID().toString(), "update", task.id, moshi.adapter(Map::class.java).toJson(body), System.currentTimeMillis()))
        }
    }

    private companion object {
        const val SYNC_CURSOR_KEY = "main"
        const val FIRST_SYNC_CURSOR = "1970-01-01T00:00:00.000Z"
        val PRIORITIES = setOf("low", "normal", "high", "urgent")
        val moshi = Moshi.Builder().build()
        val mapAdapter = moshi.adapter<Map<String, Any?>>(com.squareup.moshi.Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java))
    }
}

private fun TaskDto.toEntity() = TaskEntity(id, ownerId, projectId, columnId, title, description, status, priority, scheduledDate, dueAt, estimatedMinutes, kanbanPosition, recurrence, reminderOffsets, tags, createdAt, updatedAt, version, deletedAt)
private fun ProjectDto.toEntity() = ProjectEntity(id, ownerId, name, color, createdAt, updatedAt, version, deletedAt, archivedAt)
private fun KanbanColumnDto.toEntity() = KanbanColumnEntity(id, ownerId, name, color, semanticStatus, position, createdAt, updatedAt, version, deletedAt)
