package ru.taskflow.app.ui.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import android.content.Context
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.taskflow.app.data.SyncRepository
import ru.taskflow.app.data.TaskRepository
import ru.taskflow.app.data.local.TaskEntity
import ru.taskflow.app.data.local.TaskConflictEntity
import ru.taskflow.app.data.remote.TaskFlowApiFactory
import ru.taskflow.app.data.session.TokenStore
import ru.taskflow.app.data.sync.TaskSyncScheduler
import ru.taskflow.app.data.reminders.TaskReminderWorker

class TaskListViewModel(private val tasks: TaskRepository, private val sync: SyncRepository, private val appContext: Context) : ViewModel() {
    val taskList = tasks.tasks.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val projects = tasks.projects.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val conflicts = tasks.conflicts.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val _syncing = MutableStateFlow(false)
    val syncing = _syncing

    init { refresh() }

    fun refresh() {
        if (_syncing.value) return
        viewModelScope.launch {
            _syncing.value = true
            runCatching { sync.pushAndPull() }
            _syncing.value = false
        }
    }

    fun createInboxTask(title: String) {
        if (title.isBlank()) return
        viewModelScope.launch {
            runCatching { tasks.createInboxTask(title) }.onSuccess { TaskSyncScheduler.enqueue(appContext) }
            refresh()
        }
    }

    fun completeTask(taskId: String) {
        viewModelScope.launch { runCatching { tasks.completeLocalTask(taskId) }.onSuccess { TaskSyncScheduler.enqueue(appContext) }; refresh() }
    }

    fun deleteTask(taskId: String) {
        viewModelScope.launch { runCatching { tasks.deleteLocalTask(taskId) }.onSuccess { TaskSyncScheduler.enqueue(appContext) }; refresh() }
    }

    fun updateTask(taskId: String, title: String, priority: String, description: String, projectId: String?, scheduledDate: String?, dueAt: String?) {
        viewModelScope.launch { runCatching { tasks.updateLocalTask(taskId, title, priority, description, projectId, scheduledDate, dueAt) }.onSuccess { TaskSyncScheduler.enqueue(appContext); TaskReminderWorker.schedule(appContext, taskId, title, dueAt) }; refresh() }
    }

    fun keepServerVersion(mutationId: String) {
        viewModelScope.launch { tasks.keepServerVersion(mutationId) }
    }

    fun keepLocalVersion(conflict: TaskConflictEntity) {
        viewModelScope.launch { runCatching { tasks.keepLocalVersion(conflict) }.onSuccess { TaskSyncScheduler.enqueue(appContext) } }
    }

    fun today(tasks: List<TaskEntity>, date: String): List<TaskEntity> = tasks.filter { it.scheduledDate == date }
    fun inbox(tasks: List<TaskEntity>): List<TaskEntity> = tasks.filter { it.status == "inbox" }
}

class TaskListViewModelFactory(private val database: ru.taskflow.app.data.local.TaskFlowDatabase, private val tokenStore: TokenStore, private val appContext: Context) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val tasks = TaskRepository(database)
        val serverUrl = checkNotNull(tokenStore.serverUrl()) { "Сервер не настроен" }
        return TaskListViewModel(tasks, SyncRepository(TaskFlowApiFactory(tokenStore).create(serverUrl), tasks), appContext) as T
    }
}
