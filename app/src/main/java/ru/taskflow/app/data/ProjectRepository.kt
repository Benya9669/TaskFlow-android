package ru.taskflow.app.data

import androidx.room.withTransaction
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import ru.taskflow.app.data.local.PendingMutationEntity
import ru.taskflow.app.data.local.ProjectConflictEntity
import ru.taskflow.app.data.local.ProjectEntity
import ru.taskflow.app.data.local.TaskFlowDatabase
import ru.taskflow.app.data.remote.ProjectDto
import ru.taskflow.app.data.remote.TaskFlowApi
import java.time.Instant
import java.util.UUID

class ProjectRepository(
    private val api: TaskFlowApi,
    private val database: TaskFlowDatabase,
) {
    val active = database.projectDao().observeActive()
    val archived = database.projectDao().observeArchived()
    val conflicts = database.projectConflictDao().observeAll()

    suspend fun refresh() {
        database.projectDao().upsertAll(api.projects(includeArchived = true).projects.map(ProjectDto::toEntity))
    }

    suspend fun create(name: String, color: String) {
        validate(name, color)
        val ownerId = database.projectDao().anyOwnerId() ?: database.kanbanColumnDao().anyOwnerId()
        ?: error("Сначала дождитесь первичной синхронизации")
        val now = Instant.now().toString()
        val project = ProjectEntity(UUID.randomUUID().toString(), ownerId, name.trim(), color, now, now, 1, null, null)
        saveDesired(project, isNew = true)
    }

    suspend fun update(project: ProjectEntity, name: String, color: String) {
        validate(name, color)
        saveDesired(project.copy(name = name.trim(), color = color, updatedAt = Instant.now().toString()))
    }

    suspend fun archive(project: ProjectEntity) {
        saveDesired(project.copy(archivedAt = Instant.now().toString(), updatedAt = Instant.now().toString()))
    }

    suspend fun restore(project: ProjectEntity) {
        saveDesired(project.copy(archivedAt = null, updatedAt = Instant.now().toString()))
    }

    suspend fun saveConflict(mutation: PendingMutationEntity, server: ProjectDto) {
        database.withTransaction {
            database.projectDao().upsert(server.toEntity())
            database.projectConflictDao().insert(ProjectConflictEntity(mutation.id, mutation.taskId, mutation.bodyJson.orEmpty(), server.name, server.color, server.version, System.currentTimeMillis()))
            database.mutationDao().delete(listOf(mutation.id))
        }
    }

    suspend fun keepServerVersion(mutationId: String) = database.projectConflictDao().delete(mutationId)

    suspend fun keepLocalVersion(conflict: ProjectConflictEntity) {
        val current = checkNotNull(database.projectDao().find(conflict.projectId)) { "Серверный проект не найден" }
        val desired = mapAdapter.fromJson(conflict.localBodyJson).orEmpty().toMutableMap()
        desired["expected_version"] = current.version
        val local = current.copy(
            name = desired["name"] as? String ?: current.name,
            color = desired["color"] as? String ?: current.color,
            archivedAt = if (desired["archived"] as? Boolean == true) Instant.now().toString() else null,
            updatedAt = Instant.now().toString(),
        )
        database.withTransaction {
            database.projectDao().upsert(local)
            database.mutationDao().insert(PendingMutationEntity(UUID.randomUUID().toString(), "update", conflict.projectId, mapAdapter.toJson(desired), System.currentTimeMillis(), PROJECT))
            database.projectConflictDao().delete(conflict.mutationId)
        }
    }

    private suspend fun saveDesired(project: ProjectEntity, isNew: Boolean = false) {
        val pending = database.mutationDao().forEntity(PROJECT, project.id)
        val createPending = pending.firstOrNull { it.operation == "create" }
        val operation = if (isNew || createPending != null) "create" else "update"
        val body = mutableMapOf<String, Any?>("name" to project.name, "color" to project.color, "archived" to (project.archivedAt != null))
        if (operation == "update") body["expected_version"] = project.version
        database.withTransaction {
            database.projectDao().upsert(project)
            database.mutationDao().deleteForEntity(PROJECT, project.id)
            database.mutationDao().insert(PendingMutationEntity(createPending?.id ?: UUID.randomUUID().toString(), operation, project.id, mapAdapter.toJson(body), createPending?.createdAt ?: System.currentTimeMillis(), PROJECT))
        }
    }

    private fun validate(name: String, color: String) {
        require(name.trim().length in 1..100) { "Название проекта должно содержать от 1 до 100 символов" }
        require(COLOR.matches(color)) { "Цвет проекта должен быть в формате #RRGGBB" }
    }

    private companion object {
        const val PROJECT = "project"
        val COLOR = Regex("^#[0-9a-fA-F]{6}$")
        val moshi = Moshi.Builder().build()
        val mapAdapter = moshi.adapter<Map<String, Any?>>(Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java))
    }
}

private fun ProjectDto.toEntity() = ProjectEntity(id, ownerId, name, color, createdAt, updatedAt, version, deletedAt, archivedAt)
