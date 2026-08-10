package ru.taskflow.app.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import ru.taskflow.app.data.local.TaskFlowDatabase
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
    }

    @After
    fun tearDown() {
        tokenStore.clear()
        database.close()
        server.shutdown()
    }

    @Test
    fun createUpdateArchiveAndRestorePersistServerVersions() = runBlocking {
        server.enqueue(projectResponse("Inbox", "#2563EB", 1, null))
        server.enqueue(projectResponse("Work", "#7C3AED", 2, null))
        server.enqueue(projectResponse("Work", "#7C3AED", 3, "2026-08-10T10:00:00Z"))
        server.enqueue(projectResponse("Work", "#7C3AED", 4, null))

        repository.create("Inbox", "#2563EB")
        val created = repository.active.first().single()
        repository.update(created, "Work", "#7C3AED")
        val updated = repository.active.first().single()
        repository.archive(updated)
        val archived = repository.archived.first().single()
        repository.restore(archived)

        val restored = repository.active.first().single()
        assertEquals("Work", restored.name)
        assertEquals(4, restored.version)
        assertEquals("POST", server.takeRequest().method)
        assertEquals("PATCH", server.takeRequest().method)
        assertEquals("POST", server.takeRequest().method)
        val restoreRequest = server.takeRequest()
        assertEquals("DELETE", restoreRequest.method)
        assertTrue(restoreRequest.body.readUtf8().contains("\"expected_version\":3"))
    }

    private fun projectResponse(name: String, color: String, version: Int, archivedAt: String?) =
        MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json").setBody(
            """{"project":{"id":"project-1","owner_id":"owner-1","name":"$name","color":"$color","created_at":"2026-08-10T09:00:00Z","updated_at":"2026-08-10T10:00:00Z","version":$version,"deleted_at":null,"archived_at":${archivedAt?.let { "\"$it\"" } ?: "null"}}}""",
        )
}
