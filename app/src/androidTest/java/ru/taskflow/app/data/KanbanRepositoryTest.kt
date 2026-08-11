package ru.taskflow.app.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import ru.taskflow.app.data.local.KanbanColumnEntity
import ru.taskflow.app.data.local.TaskFlowDatabase
import ru.taskflow.app.data.remote.TaskFlowApiFactory
import ru.taskflow.app.data.session.SessionTokens
import ru.taskflow.app.data.session.TokenStore

@RunWith(AndroidJUnit4::class)
class KanbanRepositoryTest {
    private lateinit var database: TaskFlowDatabase
    private lateinit var server: MockWebServer
    private lateinit var tokenStore: TokenStore
    private lateinit var repository: KanbanRepository

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = Room.inMemoryDatabaseBuilder(context, TaskFlowDatabase::class.java).allowMainThreadQueries().build()
        server = MockWebServer().apply { start() }
        tokenStore = TokenStore(context).apply {
            clear()
            saveServerUrl(server.url("/api/v1/").toString())
            save(SessionTokens("test-access", "test-refresh"))
        }
        repository = KanbanRepository(TaskFlowApiFactory(tokenStore).create(tokenStore.serverUrl()!!), database)
    }

    @After fun tearDown() {
        tokenStore.clear()
        database.close()
        server.shutdown()
    }

    @Test fun createUpdateReorderAndDeleteUseVersionedKanbanApi() = runBlocking {
        server.enqueue(json(columnResponse("one", "Новая", "#2563eb", "todo", 0, 1)))
        repository.create("Новая", "#2563EB", "todo")
        val created = checkNotNull(database.kanbanColumnDao().find("one"))
        assertEquals("/api/v1/kanban/columns", server.takeRequest().path)

        server.enqueue(json(columnResponse("one", "Работа", "#7c3aed", "in_progress", 0, 2)))
        repository.update(created, "Работа", "#7C3AED", "in_progress")
        val updateRequest = server.takeRequest()
        assertEquals("/api/v1/kanban/columns/one", updateRequest.path)
        assert(updateRequest.body.readUtf8().contains("\"expected_version\":1"))

        val second = column("two", "Готово", "done", 1, 1)
        database.kanbanColumnDao().upsert(second)
        server.enqueue(json("""{"columns":[${columnJson("two", "Готово", "#16a34a", "done", 0, 2)},${columnJson("one", "Работа", "#7c3aed", "in_progress", 1, 3)}]}"""))
        repository.reorder(listOf(second, checkNotNull(database.kanbanColumnDao().find("one"))))
        val reorderRequest = server.takeRequest()
        assertEquals("/api/v1/kanban/columns/reorder", reorderRequest.path)
        assert(reorderRequest.body.readUtf8().contains("\"column_ids\":[\"two\",\"one\"]"))

        val current = checkNotNull(database.kanbanColumnDao().find("one"))
        server.enqueue(json("""{"deleted":true,"id":"one","moved_to_column_id":"two"}"""))
        repository.delete(current, checkNotNull(database.kanbanColumnDao().find("two")))
        val deleteRequest = server.takeRequest()
        assertEquals("DELETE", deleteRequest.method)
        assert(deleteRequest.body.readUtf8().contains("\"expected_version\":3"))
    }

    private fun json(body: String) = MockResponse().setHeader("Content-Type", "application/json").setBody(body)
    private fun columnResponse(id: String, name: String, color: String, status: String, position: Int, version: Int) = """{"column":${columnJson(id, name, color, status, position, version)}}"""
    private fun columnJson(id: String, name: String, color: String, status: String, position: Int, version: Int) = """{"id":"$id","owner_id":"owner","name":"$name","color":"$color","semantic_status":"$status","position":$position,"created_at":"2026-08-10T09:00:00Z","updated_at":"2026-08-10T09:00:00Z","version":$version,"deleted_at":null}"""
    private fun column(id: String, name: String, status: String, position: Int, version: Int) = KanbanColumnEntity(id, "owner", name, "#16a34a", status, position, "2026-08-10T09:00:00Z", "2026-08-10T09:00:00Z", version, null)
}
