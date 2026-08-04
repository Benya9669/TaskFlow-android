package ru.taskflow.app.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import ru.taskflow.app.data.local.TaskFlowDatabase
import ru.taskflow.app.data.remote.TaskFlowApiFactory
import ru.taskflow.app.data.session.SessionRepository
import ru.taskflow.app.data.session.TokenStore

@RunWith(AndroidJUnit4::class)
class TaskFlowV020E2eTest {
    private lateinit var database: TaskFlowDatabase

    @Before fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), TaskFlowDatabase::class.java).allowMainThreadQueries().build()
    }

    @After fun tearDown() = database.close()

    @Test fun syncsWithExternalV020ServerWhenConfigured() = runBlocking {
        val arguments = InstrumentationRegistry.getArguments()
        val baseUrl = arguments.getString("taskflowE2eUrl")
        val email = arguments.getString("taskflowE2eEmail")
        val password = arguments.getString("taskflowE2ePassword")
        assumeTrue("External TaskFlow server credentials were not configured", !baseUrl.isNullOrBlank() && !email.isNullOrBlank() && !password.isNullOrBlank())
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val tokenStore = TokenStore(context).also { it.clear() }
        SessionRepository(tokenStore).login(baseUrl!!, email!!, password!!)
        val repository = TaskRepository(database)
        val sync = SyncRepository(TaskFlowApiFactory(tokenStore).create(tokenStore.serverUrl()!!), repository)
        sync.pull()
        val created = repository.createInboxTask("Android v0.2.0 E2E")
        sync.pushAndPull()
        assertEquals(emptyList<Any>(), repository.pendingMutations(10))
        assertEquals(created.id, database.taskDao().find(created.id)?.id)
        tokenStore.clear()
    }
}
