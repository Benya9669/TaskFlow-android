package ru.taskflow.app.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.MockResponse
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import ru.taskflow.app.data.local.TaskFlowDatabase
import ru.taskflow.app.data.local.KanbanColumnEntity
import ru.taskflow.app.data.remote.TaskFlowApiFactory
import ru.taskflow.app.data.session.SessionTokens
import ru.taskflow.app.data.session.TokenStore

@RunWith(AndroidJUnit4::class)
class ProjectRepositoryTest {
    private lateinit var database: TaskFlowDatabase
    private lateinit var server: MockWebServer
    private lateinit var tokenStore: TokenStore
    private lateinit var repository: ProjectRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = Room.inMemoryDatabaseBuilder(context, TaskFlowDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        server = MockWebServer().apply { start() }
        tokenStore = TokenStore(context).apply {
            clear()
            saveServerUrl(server.url("/api/v1/").toString())
            save(SessionTokens("test-access", "test-refresh"))
        }
        repository = ProjectRepository(
            TaskFlowApiFactory(tokenStore).create(tokenStore.serverUrl()!!),
            database,
        )
        runBlocking {
            database.kanbanColumnDao().upsertAll(listOf(KanbanColumnEntity("column", "owner-1", "Inbox", "#2563EB", "inbox", 0, "2026-08-10T09:00:00Z", "2026-08-10T09:00:00Z", 1, null)))
        }
    }

    @After
    fun tearDown() {
        tokenStore.clear()
        database.close()
        server.shutdown()
    }

    @Test
    fun createUpdateArchiveAndRestoreCoalesceIntoDurableMutation() = runBlocking {
        repository.create("Inbox", "#2563EB")
        val created = repository.active.first().single()
        repository.update(created, "Work", "#7C3AED")
        val updated = repository.active.first().single()
        repository.archive(updated)
        val archived = repository.archived.first().single()
        repository.restore(archived)

        val restored = repository.active.first().single()
        assertEquals("Work", restored.name)
        assertEquals(1, restored.version)
        val mutation = database.mutationDao().nextBatch(10).single()
        assertEquals("project", mutation.entityType)
        assertEquals("create", mutation.operation)
        assert(mutation.bodyJson.orEmpty().contains("\"name\":\"Work\""))
        assert(mutation.bodyJson.orEmpty().contains("\"archived\":false"))
    }

    @Test
    fun syncSendsProjectEntityAndClearsDurableMutation() = runBlocking {
        repository.create("Offline", "#2563EB")
        val local = repository.active.first().single()
        server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody(
            """{"mutations":[{"id":"${database.mutationDao().nextBatch(10).single().id}","status":201,"response":{"project":{"id":"${local.id}","owner_id":"owner-1","name":"Offline","color":"#2563eb","created_at":"2026-08-10T09:00:00Z","updated_at":"2026-08-10T09:00:00Z","version":1,"deleted_at":null,"archived_at":null}}}]}""",
        ))
        server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody(
            """{"snapshot":"snapshot","cursor":"cursor","has_more":false,"next_cursor":null,"tasks":[],"projects":[],"kanban_columns":[]}""",
        ))

        SyncRepository(TaskFlowApiFactory(tokenStore).create(tokenStore.serverUrl()!!), TaskRepository(database), repository).pushAndPull()

        val mutationRequest = server.takeRequest()
        assertEquals("/api/v1/sync/mutations", mutationRequest.path)
        val body = mutationRequest.body.readUtf8()
        assert(body.contains("\"entity\":\"project\""))
        assert(body.contains("\"project_id\":\"${local.id}\""))
        assertEquals(0, database.mutationDao().nextBatch(10).size)
    }
}
