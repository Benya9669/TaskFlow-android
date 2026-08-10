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
    val archivedProjects = database.projectDao().observeArchived()
    val columns = database.kanbanColumnDao().observeActive()
    val conflicts = database.taskConflictDao().observeAll()
    val pendingCount = database.mutationDao().observeCount()

    suspend fun syncCursor(): String = database.syncStateDao().cursor(SYNC_CURSOR_KEY) ?: FIRST_SYNC_CURSOR

    suspend fun applySyncPage(tasks: List<TaskDto>, projects: List<ProjectDto>, columns: List<KanbanColumnDto>, cursor: String) {
        database.withTransaction {
            database.taskDao().upsertAll(tasks.map(TaskDto::toEntity))
            database.projectDao().upsertAll(projects.map(ProjectDto::toEntity))
            database.kanbanColumnDao().upsertAll(columns.map(KanbanColumnDto::toEntity))
            database.syncStateDao().put(SyncStateEntity(SYNC_CURSOR_KEY, cursor))
        }
    }

    suspend fun createLocalTask(ownerId: String, columnId: String, title: String, priority: String = "normal", scheduledDate: String? = null, status: String = "inbox", projectId: String? = null): TaskEntity {
        val now = Instant.now().toString()
        val task = TaskEntity(
            id = UUID.randomUUID().toString(), ownerId = ownerId, projectId = projectId, columnId = columnId,
            title = title.trim(), description = "", status = status, priority = priority, scheduledDate = scheduledDate,
            dueAt = null, estimatedMinutes = null, kanbanPosition = Int.MAX_VALUE, recurrence = null,
            reminderOffsets = emptyList(), tags = emptyList(), createdAt = now, updatedAt = now, version = 1, deletedAt = null,
        )
        require(priority in PRIORITIES) { "Некорректный приоритет" }
        val body = mapOf("title" to task.title, "project_id" to task.projectId, "column_id" to task.columnId, "status" to task.status, "priority" to task.priority, "scheduled_date" to task.scheduledDate)
        database.withTransaction {
            database.taskDao().upsert(task)
            database.mutationDao().insert(PendingMutationEntity(UUID.randomUUID().toString(), "create", task.id, moshi.adapter(Map::class.java).toJson(body), System.currentTimeMillis()))
        }
        return task
    }

    suspend fun createInboxTask(title: String, priority: String = "normal", scheduledDate: String? = null): TaskEntity {
        val column = checkNotNull(database.kanbanColumnDao().inbox()) { "Сначала дождитесь синхронизации колонок" }
        return createLocalTask(column.ownerId, column.id, title, priority, scheduledDate)
    }

    suspend fun createKanbanTask(column: KanbanColumnEntity, title: String, projectId: String? = null): TaskEntity =
        createLocalTask(column.ownerId, column.id, title, status = column.semanticStatus, projectId = projectId)

    suspend fun completeLocalTask(taskId: String) {
        val current = checkNotNull(database.taskDao().find(taskId)) { "Задача не найдена" }
        if (current.deletedAt != null || current.status == "done") return
        val doneColumn = checkNotNull(database.kanbanColumnDao().byStatus("done")) { "Сначала дождитесь синхронизации колонок" }
        val updated = current.copy(columnId = doneColumn.id, status = "done", updatedAt = Instant.now().toString())
        enqueueUpdate(updated, mapOf("column_id" to updated.columnId, "status" to "done", "expected_version" to current.version))
    }

    suspend fun reopenLocalTask(taskId: String) {
        val current = checkNotNull(database.taskDao().find(taskId)) { "Задача не найдена" }
        if (current.deletedAt != null || current.status != "done") return
        val inboxColumn = checkNotNull(database.kanbanColumnDao().inbox()) { "Сначала дождитесь синхронизации колонок" }
        val updated = current.copy(columnId = inboxColumn.id, status = "inbox", updatedAt = Instant.now().toString())
        enqueueUpdate(updated, mapOf("column_id" to updated.columnId, "status" to "inbox", "expected_version" to current.version))
    }

    suspend fun moveLocalTask(taskId: String, column: KanbanColumnEntity, beforeTaskId: String? = null) {
        val current = checkNotNull(database.taskDao().find(taskId)) { "Задача не найдена" }
        if (current.deletedAt != null || beforeTaskId == taskId) return
        val target = database.taskDao().activeInColumn(column.id).filter { it.id != taskId }.toMutableList()
        val insertAt = if (beforeTaskId == null) target.size else target.indexOfFirst { it.id == beforeTaskId }.also {
            require(it >= 0) { "Опорная задача отсутствует в целевой колонке" }
        }
        val currentOrder = database.taskDao().activeInColumn(column.id).map(TaskEntity::id)
        val desiredOrder = target.map(TaskEntity::id).toMutableList().apply { add(insertAt, taskId) }
        if (current.columnId == column.id && currentOrder == desiredOrder) return
        val now = Instant.now().toString()
        val moved = current.copy(columnId = column.id, status = column.semanticStatus, updatedAt = now)
        target.add(insertAt, moved)
        val ordered = target.mapIndexed { index, task ->
            task.copy(
                columnId = column.id,
                status = if (task.id == taskId) column.semanticStatus else task.status,
                kanbanPosition = (index + 1) * 1024,
                updatedAt = if (task.id == taskId) now else task.updatedAt,
            )
        }
        database.withTransaction {
            val expectedVersion = nextExpectedVersion(current)
            database.taskDao().upsertAll(ordered)
            database.mutationDao().insert(
                PendingMutationEntity(
                    UUID.randomUUID().toString(),
                    "move",
                    taskId,
                    mapAdapter.toJson(mapOf("column_id" to column.id, "before_task_id" to beforeTaskId, "expected_version" to expectedVersion)),
                    System.currentTimeMillis(),
                ),
            )
        }
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

    suspend fun updateLocalTask(taskId: String, update: TaskUpdate) {
        val current = checkNotNull(database.taskDao().find(taskId)) { "Задача не найдена" }
        require(update.title.isNotBlank()) { "Введите название задачи" }
        require(update.priority in PRIORITIES) { "Некорректный приоритет" }
        require(update.estimatedMinutes == null || update.estimatedMinutes in 1..10_080) { "Оценка должна быть от 1 до 10080 минут" }
        require(update.tags.size <= 20 && update.tags.all { it.length in 1..40 }) { "Не больше 20 тегов длиной до 40 символов" }
        require(update.reminderOffsets.isEmpty() || update.dueAt != null) { "Для напоминаний укажите срок" }
        if (current.deletedAt != null) return
        val updated = current.copy(title = update.title.trim(), priority = update.priority, description = update.description.trim(), projectId = update.projectId, scheduledDate = update.scheduledDate, dueAt = update.dueAt, estimatedMinutes = update.estimatedMinutes, tags = update.tags.distinct(), reminderOffsets = update.reminderOffsets.distinct().sorted(), updatedAt = Instant.now().toString())
        enqueueUpdate(updated, mapOf("title" to updated.title, "priority" to updated.priority, "description" to updated.description, "project_id" to updated.projectId, "scheduled_date" to updated.scheduledDate, "due_at" to updated.dueAt, "estimated_minutes" to updated.estimatedMinutes, "tags" to updated.tags, "reminder_offsets" to updated.reminderOffsets, "expected_version" to current.version))
    }

    suspend fun updateLocalTask(taskId: String, title: String, priority: String, description: String, projectId: String?, scheduledDate: String?, dueAt: String?) =
        updateLocalTask(taskId, TaskUpdate(title, priority, description, projectId, scheduledDate, dueAt, null, emptyList(), emptyList()))

    suspend fun pendingMutations(limit: Int) = database.mutationDao().nextBatch(limit)
    suspend fun removeMutations(ids: List<String>) = database.mutationDao().delete(ids)

    suspend fun saveConflict(mutation: PendingMutationEntity, serverTask: TaskDto) {
        val body = checkNotNull(mutation.bodyJson)
        database.withTransaction {
            database.taskDao().upsert(serverTask.toEntity())
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
            val operation = if (body.containsKey("before_task_id")) "move" else "update"
            database.mutationDao().insert(PendingMutationEntity(UUID.randomUUID().toString(), operation, conflict.taskId, mapAdapter.toJson(body), System.currentTimeMillis()))
            database.taskConflictDao().delete(conflict.mutationId)
        }
    }

    private suspend fun enqueueUpdate(task: TaskEntity, body: Map<String, Any?>) {
        database.withTransaction {
            val adjustedBody = body.toMutableMap().apply {
                if (containsKey("expected_version")) this["expected_version"] = nextExpectedVersion(task)
            }
            database.taskDao().upsert(task)
            database.mutationDao().insert(PendingMutationEntity(UUID.randomUUID().toString(), "update", task.id, mapAdapter.toJson(adjustedBody), System.currentTimeMillis()))
        }
    }

    private suspend fun nextExpectedVersion(task: TaskEntity): Int = task.version + database.mutationDao()
        .forEntity("task", task.id)
        .count { it.operation == "update" || it.operation == "move" }

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
