package ru.taskflow.app.data

import ru.taskflow.app.data.local.ProjectEntity
import ru.taskflow.app.data.local.TaskFlowDatabase
import ru.taskflow.app.data.remote.ProjectUpdateRequest
import ru.taskflow.app.data.remote.ProjectWriteRequest
import ru.taskflow.app.data.remote.TaskFlowApi
import ru.taskflow.app.data.remote.VersionGuardRequest

class ProjectRepository(
    private val api: TaskFlowApi,
    private val database: TaskFlowDatabase,
) {
    val active = database.projectDao().observeActive()
    val archived = database.projectDao().observeArchived()

    suspend fun refresh() {
        database.projectDao().upsertAll(api.projects(includeArchived = true).projects.map { it.toEntity() })
    }

    suspend fun create(name: String, color: String) {
        validate(name, color)
        database.projectDao().upsert(api.createProject(ProjectWriteRequest(name.trim(), color)).project.toEntity())
    }

    suspend fun update(project: ProjectEntity, name: String, color: String) {
        validate(name, color)
        database.projectDao().upsert(api.updateProject(project.id, ProjectUpdateRequest(name.trim(), color, project.version)).project.toEntity())
    }

    suspend fun archive(project: ProjectEntity) {
        database.projectDao().upsert(api.archiveProject(project.id, VersionGuardRequest(project.version)).project.toEntity())
    }

    suspend fun restore(project: ProjectEntity) {
        database.projectDao().upsert(api.restoreProject(project.id, VersionGuardRequest(project.version)).project.toEntity())
    }

    private fun validate(name: String, color: String) {
        require(name.trim().length in 1..100) { "Название проекта должно содержать от 1 до 100 символов" }
        require(COLOR.matches(color)) { "Цвет проекта должен быть в формате #RRGGBB" }
    }

    private companion object {
        val COLOR = Regex("^#[0-9a-fA-F]{6}$")
    }
}

private fun ru.taskflow.app.data.remote.ProjectDto.toEntity() = ProjectEntity(
    id, ownerId, name, color, createdAt, updatedAt, version, deletedAt, archivedAt,
)
