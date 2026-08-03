package ru.taskflow.app.data.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import ru.taskflow.app.data.SyncRepository
import ru.taskflow.app.data.TaskRepository
import ru.taskflow.app.data.local.TaskFlowDatabase
import ru.taskflow.app.data.remote.TaskFlowApiFactory
import ru.taskflow.app.data.session.TokenStore
import java.io.IOException
import java.util.concurrent.TimeUnit

class TaskSyncWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val tokenStore = TokenStore(applicationContext)
        val serverUrl = tokenStore.serverUrl() ?: return@withContext Result.failure()
        try {
            val tasks = TaskRepository(TaskFlowDatabase.get(applicationContext))
            SyncRepository(TaskFlowApiFactory(tokenStore).create(serverUrl), tasks).pushAndPull()
            Result.success()
        } catch (_: IOException) {
            Result.retry()
        } catch (error: HttpException) {
            if (error.code() >= 500) Result.retry() else Result.failure()
        }
    }
}

object TaskSyncScheduler {
    private const val UNIQUE_WORK_NAME = "taskflow.sync"

    fun enqueue(context: Context) {
        val request = OneTimeWorkRequestBuilder<TaskSyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.KEEP, request)
    }
}
