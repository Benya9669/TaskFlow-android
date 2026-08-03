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

    private fun column(id: String, status: String) = KanbanColumnEntity(id, "owner", id, "#000000", status, 0, "2026-01-01T00:00:00Z", "2026-01-01T00:00:00Z", 1, null)
}

private fun ru.taskflow.app.data.local.TaskEntity.toDto() = ru.taskflow.app.data.remote.TaskDto(id, ownerId, projectId, columnId, title, description, status, priority, scheduledDate, dueAt, estimatedMinutes, kanbanPosition, recurrence, reminderOffsets, tags, createdAt, updatedAt, version, deletedAt)
