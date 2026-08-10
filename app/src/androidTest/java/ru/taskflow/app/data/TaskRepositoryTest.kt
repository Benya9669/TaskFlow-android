package ru.taskflow.app.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import ru.taskflow.app.data.local.KanbanColumnEntity
import ru.taskflow.app.data.local.TaskFlowDatabase

@RunWith(AndroidJUnit4::class)
class TaskRepositoryTest {
    private lateinit var database: TaskFlowDatabase
    private lateinit var repository: TaskRepository

    @Before fun setUp() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), TaskFlowDatabase::class.java).allowMainThreadQueries().build()
        repository = TaskRepository(database)
        database.kanbanColumnDao().upsertAll(listOf(column("inbox", "inbox"), column("done", "done")))
    }

    @After fun tearDown() = database.close()

    @Test fun offlineCrudAddsExpectedMutations() = runBlocking {
        val task = repository.createInboxTask("Offline task")
        repository.updateLocalTask(task.id, "Edited task", "high", "", null, null, null)
        repository.completeLocalTask(task.id)
        repository.deleteLocalTask(task.id)

        val mutations = repository.pendingMutations(10)
        assertEquals(listOf("create", "update", "update", "delete"), mutations.map { it.operation })
        assertEquals("Edited task", database.taskDao().find(task.id)?.title)
        assertNotNull(database.taskDao().find(task.id)?.deletedAt)
    }

    @Test fun keepingServerVersionRemovesConflict() = runBlocking {
        val task = repository.createInboxTask("Conflict task")
        repository.updateLocalTask(task.id, "Local value", "urgent", "", null, null, null)
        val mutation = repository.pendingMutations(10).last()
        repository.saveConflict(mutation, task.copy(title = "Server value", priority = "low").toDto())

        val conflict = repository.conflicts.first().single()
        repository.keepServerVersion(conflict.mutationId)

        assertEquals(emptyList<Any>(), repository.conflicts.first())
        assertEquals(1, repository.pendingMutations(10).size)
    }

    @Test fun extendedTaskFieldsAreStoredInOfflineMutation() = runBlocking {
        val task = repository.createInboxTask("Расширенная задача")
        repository.updateLocalTask(task.id, TaskUpdate("Расширенная задача", "high", "Описание", null, null, "2026-08-11T09:00:00Z", 90, listOf("работа", "важно"), listOf(15, 60)))

        val stored = database.taskDao().find(task.id)!!
        assertEquals(90, stored.estimatedMinutes)
        assertEquals(listOf("работа", "важно"), stored.tags)
        val body = repository.pendingMutations(10).last().bodyJson.orEmpty()
        assert(body.contains("\"estimated_minutes\":90"))
        assert(body.contains("\"reminder_offsets\":[15,60]"))
    }

    @Test fun kanbanMoveStoresManualOrderAndVersionedMoveMutation() = runBlocking {
        val first = repository.createInboxTask("Первая")
        repository.createInboxTask("Вторая")
        val third = repository.createInboxTask("Третья")
        repository.updateLocalTask(third.id, "Третья изменена", "high", "", null, null, null)

        repository.moveLocalTask(third.id, column("inbox", "inbox"), first.id)

        assertEquals(listOf(third.id, first.id), database.taskDao().activeInColumn("inbox").take(2).map { it.id })
        val move = repository.pendingMutations(20).last()
        assertEquals("move", move.operation)
        assert(move.bodyJson.orEmpty().contains("\"before_task_id\":\"${first.id}\""))
        assert(move.bodyJson.orEmpty().contains("\"expected_version\":2"))
    }

    @Test fun kanbanQuickAddUsesColumnStatusAndProject() = runBlocking {
        val task = repository.createKanbanTask(column("done", "done"), "Готовая задача", "project")

        assertEquals("done", task.status)
        assertEquals("done", task.columnId)
        assertEquals("project", task.projectId)
    }

    private fun column(id: String, status: String) = KanbanColumnEntity(id, "owner", id, "#000000", status, 0, "2026-01-01T00:00:00Z", "2026-01-01T00:00:00Z", 1, null)
}

private fun ru.taskflow.app.data.local.TaskEntity.toDto() = ru.taskflow.app.data.remote.TaskDto(id, ownerId, projectId, columnId, title, description, status, priority, scheduledDate, dueAt, estimatedMinutes, kanbanPosition, recurrence, reminderOffsets, tags, createdAt, updatedAt, version, deletedAt)
