package ru.taskflow.app.data

import ru.taskflow.app.data.local.KanbanColumnEntity
import ru.taskflow.app.data.local.TaskFlowDatabase
import ru.taskflow.app.data.remote.KanbanColumnDeleteRequest
import ru.taskflow.app.data.remote.KanbanColumnDto
import ru.taskflow.app.data.remote.KanbanColumnOrderRequest
import ru.taskflow.app.data.remote.KanbanColumnUpdateRequest
import ru.taskflow.app.data.remote.KanbanColumnWriteRequest
import ru.taskflow.app.data.remote.TaskFlowApi

class KanbanRepository(
    private val api: TaskFlowApi,
    private val database: TaskFlowDatabase,
) {
    suspend fun create(name: String, color: String, semanticStatus: String) {
        validate(name, color, semanticStatus)
        database.kanbanColumnDao().upsert(api.createKanbanColumn(KanbanColumnWriteRequest(name.trim(), color.lowercase(), semanticStatus)).column.toEntity())
    }

    suspend fun update(column: KanbanColumnEntity, name: String, color: String, semanticStatus: String) {
        validate(name, color, semanticStatus)
        database.kanbanColumnDao().upsert(
            api.updateKanbanColumn(column.id, KanbanColumnUpdateRequest(name.trim(), color.lowercase(), semanticStatus, column.version)).column.toEntity(),
        )
    }

    suspend fun reorder(columns: List<KanbanColumnEntity>) {
        require(columns.isNotEmpty()) { "Должна остаться хотя бы одна колонка" }
        database.kanbanColumnDao().upsertAll(api.reorderKanbanColumns(KanbanColumnOrderRequest(columns.map(KanbanColumnEntity::id))).columns.map(KanbanColumnDto::toEntity))
    }

    suspend fun delete(column: KanbanColumnEntity, destination: KanbanColumnEntity) {
        require(column.id != destination.id) { "Выберите другую колонку для переноса задач" }
        api.deleteKanbanColumn(column.id, KanbanColumnDeleteRequest(destination.id, column.version))
    }

    private fun validate(name: String, color: String, semanticStatus: String) {
        require(name.trim().length in 1..80) { "Название колонки должно содержать от 1 до 80 символов" }
        require(COLOR.matches(color)) { "Цвет колонки должен быть в формате #RRGGBB" }
        require(semanticStatus in STATUSES) { "Неизвестный статус колонки" }
    }

    private companion object {
        val COLOR = Regex("^#[0-9a-fA-F]{6}$")
        val STATUSES = setOf("inbox", "todo", "in_progress", "done")
    }
}

private fun KanbanColumnDto.toEntity() = KanbanColumnEntity(id, ownerId, name, color, semanticStatus, position, createdAt, updatedAt, version, deletedAt)
