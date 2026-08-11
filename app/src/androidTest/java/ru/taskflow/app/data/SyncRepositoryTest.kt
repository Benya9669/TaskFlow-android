package ru.taskflow.app.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import ru.taskflow.app.data.local.TaskFlowDatabase
import ru.taskflow.app.data.local.KanbanColumnEntity
import ru.taskflow.app.data.remote.KanbanColumnDto
import ru.taskflow.app.data.remote.MutationBatch
import ru.taskflow.app.data.remote.MutationBatchResponse
import ru.taskflow.app.data.remote.ProjectDto
import ru.taskflow.app.data.remote.RefreshRequest
import ru.taskflow.app.data.remote.RefreshResponse
import ru.taskflow.app.data.remote.SyncResponse
import ru.taskflow.app.data.remote.TaskDto
import ru.taskflow.app.data.remote.TaskFlowApi
import ru.taskflow.app.data.remote.TaskFlowApiFactory
import ru.taskflow.app.data.remote.MutationDto
import ru.taskflow.app.data.remote.MutationResultDto
import ru.taskflow.app.data.session.SessionTokens
import ru.taskflow.app.data.session.TokenStore

@RunWith(AndroidJUnit4::class)
class SyncRepositoryTest {
    private lateinit var database: TaskFlowDatabase
    private lateinit var repository: TaskRepository

    @Before fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), TaskFlowDatabase::class.java).allowMainThreadQueries().build()
        repository = TaskRepository(database)
    }

    @After fun tearDown() = database.close()

    @Test fun pullFollowsPaginationAndStoresFinalCursor() = runBlocking {
        val api = object : ProjectAwareTestApi() {
            var calls = 0
            override suspend fun sync(since: String, cursor: String?, snapshot: String?, limit: Int): SyncResponse {
                calls++
                return if (calls == 1) page("snapshot-1", "cursor-1", true, "next-1", listOf(task("one"))) else page("snapshot-1", "cursor-2", false, null, listOf(task("two")))
            }
            override suspend fun login(request: ru.taskflow.app.data.remote.LoginRequest) = throw UnsupportedOperationException()
            override suspend fun register(request: ru.taskflow.app.data.remote.RegisterRequest) = throw UnsupportedOperationException()
            override suspend fun verifyEmail(request: ru.taskflow.app.data.remote.VerifyEmailRequest) = throw UnsupportedOperationException()
            override suspend fun refresh(request: RefreshRequest) = throw UnsupportedOperationException()
            override suspend fun sendMutations(request: MutationBatch) = MutationBatchResponse(emptyList())
        }
        SyncRepository(api, repository).pull()
        assertEquals(2, api.calls)
        assertEquals("cursor-2", repository.syncCursor())
        assertEquals(listOf("one", "two"), database.taskDao().observeActive().first().map { it.id }.sorted())
    }

    @Test fun refreshesTokenAfterUnauthorizedSyncRequest() = runBlocking {
        val server = MockWebServer()
        var syncAttempts = 0
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when {
                request.path?.startsWith("/api/v1/sync") == true -> {
                    syncAttempts++
                    if (syncAttempts == 1) MockResponse().setResponseCode(401) else MockResponse().setBody(syncJson())
                }
                request.path == "/api/v1/auth/refresh" -> MockResponse().setBody("{\"token\":\"new-access\",\"refresh_token\":\"new-refresh\",\"session_id\":\"session\"}")
                else -> MockResponse().setResponseCode(404)
            }
        }
        server.start()
        try {
            val store = TokenStore(ApplicationProvider.getApplicationContext())
            store.clear()
            store.saveServerUrl(server.url("/api/v1/").toString())
            store.save(SessionTokens("old-access", "old-refresh"))
            SyncRepository(TaskFlowApiFactory(store).create(store.serverUrl()!!), repository).pull()
            assertEquals(2, syncAttempts)
            assertEquals("new-access", store.read()?.accessToken)
        } finally { server.shutdown() }
    }

    @Test fun retryingSameMutationIdRemovesItAfterSuccessfulReplay() = runBlocking {
        database.kanbanColumnDao().upsertAll(listOf(KanbanColumnEntity("column", "owner", "Inbox", "#000000", "inbox", 0, "2026-01-01T00:00:00Z", "2026-01-01T00:00:00Z", 1, null)))
        val task = repository.createInboxTask("Retry task")
        val mutation = repository.pendingMutations(10).single()
        val api = object : ProjectAwareTestApi() {
            var sends = 0
            override suspend fun sendMutations(request: MutationBatch): MutationBatchResponse {
                sends++
                assertEquals(mutation.id, request.mutations.single().id)
                if (sends == 1) throw java.io.IOException("offline")
                return MutationBatchResponse(listOf(MutationResultDto(mutation.id, 200, emptyMap())))
            }
            override suspend fun sync(since: String, cursor: String?, snapshot: String?, limit: Int) = page("snapshot", "cursor", false, null, emptyList())
            override suspend fun login(request: ru.taskflow.app.data.remote.LoginRequest) = throw UnsupportedOperationException()
            override suspend fun register(request: ru.taskflow.app.data.remote.RegisterRequest) = throw UnsupportedOperationException()
            override suspend fun verifyEmail(request: ru.taskflow.app.data.remote.VerifyEmailRequest) = throw UnsupportedOperationException()
            override suspend fun refresh(request: RefreshRequest) = throw UnsupportedOperationException()
        }
        runCatching { SyncRepository(api, repository).pushAndPull() }
        SyncRepository(api, repository).pushAndPull()
        assertEquals(2, api.sends)
        assertEquals(emptyList<Any>(), repository.pendingMutations(10))
        assertEquals(task.id, database.taskDao().find(task.id)?.id)
    }

    @Test fun mutationJsonKeepsIntegralApiFieldsAsIntegers() = runBlocking {
        val column = KanbanColumnEntity("column", "owner", "Inbox", "#000000", "inbox", 0, "2026-01-01T00:00:00Z", "2026-01-01T00:00:00Z", 1, null)
        database.kanbanColumnDao().upsertAll(listOf(column))
        val task = task("integer-fields")
        repository.applySyncPage(listOf(task), emptyList(), listOf(KanbanColumnDto("column", "owner", "Inbox", "#000000", "inbox", 0, "2026-01-01T00:00:00Z", "2026-01-01T00:00:00Z", 1, null)), "cursor")
        repository.updateLocalTask(task.id, TaskUpdate(task.title, task.priority, task.description, null, null, "2026-01-02T09:00:00Z", 30, emptyList(), listOf(5, 15)))
        val api = object : ProjectAwareTestApi() {
            override suspend fun sendMutations(request: MutationBatch): MutationBatchResponse {
                val mutation = request.mutations.single()
                assertEquals(1, mutation.body?.get("expected_version"))
                assertEquals(30, mutation.body?.get("estimated_minutes"))
                assertEquals(listOf(5, 15), mutation.body?.get("reminder_offsets"))
                return MutationBatchResponse(listOf(MutationResultDto(mutation.id, 200, emptyMap())))
            }
            override suspend fun sync(since: String, cursor: String?, snapshot: String?, limit: Int) = page("snapshot", "cursor-2", false, null, emptyList())
            override suspend fun login(request: ru.taskflow.app.data.remote.LoginRequest) = throw UnsupportedOperationException()
            override suspend fun register(request: ru.taskflow.app.data.remote.RegisterRequest) = throw UnsupportedOperationException()
            override suspend fun verifyEmail(request: ru.taskflow.app.data.remote.VerifyEmailRequest) = throw UnsupportedOperationException()
            override suspend fun refresh(request: RefreshRequest) = throw UnsupportedOperationException()
        }
        SyncRepository(api, repository).pushAndPull()
        assertEquals(emptyList<Any>(), repository.pendingMutations(10))
    }


    private fun task(id: String) = TaskDto(id, "owner", null, "column", id, "", "inbox", "normal", null, null, null, 0, null, emptyList(), emptyList(), "2026-01-01T00:00:00Z", "2026-01-01T00:00:00Z", 1, null)
    private fun page(snapshot: String, cursor: String, hasMore: Boolean, next: String?, tasks: List<TaskDto>) = SyncResponse(snapshot, cursor, hasMore, next, tasks, emptyList<ProjectDto>(), emptyList<KanbanColumnDto>())
    private fun syncJson() = "{\"snapshot\":\"snapshot\",\"cursor\":\"cursor\",\"has_more\":false,\"next_cursor\":null,\"tasks\":[],\"projects\":[],\"kanban_columns\":[]}"
}

private abstract class ProjectAwareTestApi : TaskFlowApi {
    override suspend fun projects(includeArchived: Boolean) = throw UnsupportedOperationException()
    override suspend fun createProject(request: ru.taskflow.app.data.remote.ProjectWriteRequest) = throw UnsupportedOperationException()
    override suspend fun updateProject(projectId: String, request: ru.taskflow.app.data.remote.ProjectUpdateRequest) = throw UnsupportedOperationException()
    override suspend fun archiveProject(projectId: String, request: ru.taskflow.app.data.remote.VersionGuardRequest) = throw UnsupportedOperationException()
    override suspend fun restoreProject(projectId: String, request: ru.taskflow.app.data.remote.VersionGuardRequest) = throw UnsupportedOperationException()
    override suspend fun kanbanColumns() = throw UnsupportedOperationException()
    override suspend fun createKanbanColumn(request: ru.taskflow.app.data.remote.KanbanColumnWriteRequest) = throw UnsupportedOperationException()
    override suspend fun updateKanbanColumn(columnId: String, request: ru.taskflow.app.data.remote.KanbanColumnUpdateRequest) = throw UnsupportedOperationException()
    override suspend fun reorderKanbanColumns(request: ru.taskflow.app.data.remote.KanbanColumnOrderRequest) = throw UnsupportedOperationException()
    override suspend fun deleteKanbanColumn(columnId: String, request: ru.taskflow.app.data.remote.KanbanColumnDeleteRequest) = throw UnsupportedOperationException()
    override suspend fun me() = throw UnsupportedOperationException()
    override suspend fun updateAccount(request: ru.taskflow.app.data.remote.AccountUpdateRequest) = throw UnsupportedOperationException()
}
