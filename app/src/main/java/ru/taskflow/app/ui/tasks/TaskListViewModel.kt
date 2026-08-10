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
import ru.taskflow.app.data.ProjectRepository
import ru.taskflow.app.data.KanbanRepository
import ru.taskflow.app.data.TaskUpdate
import ru.taskflow.app.data.local.TaskEntity
import ru.taskflow.app.data.local.TaskConflictEntity
import ru.taskflow.app.data.remote.TaskFlowApiFactory
import ru.taskflow.app.data.session.TokenStore
import ru.taskflow.app.data.sync.TaskSyncScheduler
import ru.taskflow.app.data.reminders.TaskReminderWorker

class TaskListViewModel(private val tasks: TaskRepository, private val projectsRepository: ProjectRepository, private val kanbanRepository: KanbanRepository, private val sync: SyncRepository, private val appContext: Context) : ViewModel() {
    val taskList = tasks.tasks.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val projects = tasks.projects.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val archivedProjects = tasks.archivedProjects.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val kanbanColumns = tasks.columns.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val conflicts = tasks.conflicts.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val projectConflicts = projectsRepository.conflicts.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val pendingCount = tasks.pendingCount.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
    private val _syncing = MutableStateFlow(false)
    val syncing = _syncing
    private val _syncError = MutableStateFlow<String?>(null)
    val syncError = _syncError
    private val _projectBusy = MutableStateFlow(false)
    val projectBusy = _projectBusy
    private val _kanbanBusy = MutableStateFlow(false)
    val kanbanBusy = _kanbanBusy

    init { refresh() }

    fun refresh() {
        if (_syncing.value) return
        viewModelScope.launch {
            _syncing.value = true
            runCatching { sync.pushAndPull() }
                .onSuccess { _syncError.value = null }
                .onFailure { _syncError.value = it.message ?: "Не удалось синхронизировать данные" }
            _syncing.value = false
        }
    }

    fun dismissError() { _syncError.value = null }

    fun createProject(name: String, color: String) = projectAction { projectsRepository.create(name, color) }
    fun updateProject(project: ru.taskflow.app.data.local.ProjectEntity, name: String, color: String) = projectAction { projectsRepository.update(project, name, color) }
    fun archiveProject(project: ru.taskflow.app.data.local.ProjectEntity) = projectAction { projectsRepository.archive(project) }
    fun restoreProject(project: ru.taskflow.app.data.local.ProjectEntity) = projectAction { projectsRepository.restore(project) }

    private fun projectAction(action: suspend () -> Unit) {
        if (_projectBusy.value) return
        viewModelScope.launch {
            _projectBusy.value = true
            runCatching { action() }
                .onSuccess { _syncError.value = null; TaskSyncScheduler.enqueue(appContext) }
                .onFailure { _syncError.value = it.message ?: "Не удалось изменить проект" }
            _projectBusy.value = false
        }
    }

    fun createInboxTask(title: String, priority: String = "normal", scheduledDate: String? = null) {
        if (title.isBlank()) return
        viewModelScope.launch {
            runCatching { tasks.createInboxTask(title, priority, scheduledDate) }.onSuccess { TaskSyncScheduler.enqueue(appContext) }
            refresh()
        }
    }

    fun completeTask(taskId: String) {
        viewModelScope.launch { runCatching { tasks.completeLocalTask(taskId) }.onSuccess { TaskSyncScheduler.enqueue(appContext) }; refresh() }
    }

    fun reopenTask(taskId: String) {
        viewModelScope.launch { runCatching { tasks.reopenLocalTask(taskId) }.onSuccess { TaskSyncScheduler.enqueue(appContext) }; refresh() }
    }

    fun createKanbanTask(column: ru.taskflow.app.data.local.KanbanColumnEntity, title: String, projectId: String?) {
        if (title.isBlank()) return
        viewModelScope.launch { runCatching { tasks.createKanbanTask(column, title, projectId) }.onSuccess { TaskSyncScheduler.enqueue(appContext) }; refresh() }
    }

    fun moveTask(taskId: String, column: ru.taskflow.app.data.local.KanbanColumnEntity, beforeTaskId: String? = null) {
        viewModelScope.launch { runCatching { tasks.moveLocalTask(taskId, column, beforeTaskId) }.onSuccess { TaskSyncScheduler.enqueue(appContext) }; refresh() }
    }

    fun createKanbanColumn(name: String, color: String, status: String) = kanbanAction { kanbanRepository.create(name, color, status) }
    fun updateKanbanColumn(column: ru.taskflow.app.data.local.KanbanColumnEntity, name: String, color: String, status: String) = kanbanAction { kanbanRepository.update(column, name, color, status) }
    fun reorderKanbanColumns(columns: List<ru.taskflow.app.data.local.KanbanColumnEntity>) = kanbanAction { kanbanRepository.reorder(columns) }
    fun deleteKanbanColumn(column: ru.taskflow.app.data.local.KanbanColumnEntity, destination: ru.taskflow.app.data.local.KanbanColumnEntity) = kanbanAction { kanbanRepository.delete(column, destination) }

    private fun kanbanAction(action: suspend () -> Unit) {
        if (_kanbanBusy.value) return
        viewModelScope.launch {
            _kanbanBusy.value = true
            runCatching { action(); sync.pull() }
                .onSuccess { _syncError.value = null }
                .onFailure { _syncError.value = it.message ?: "Не удалось изменить Kanban" }
            _kanbanBusy.value = false
        }
    }

    fun deleteTask(taskId: String) {
        viewModelScope.launch { runCatching { tasks.deleteLocalTask(taskId) }.onSuccess { TaskSyncScheduler.enqueue(appContext) }; refresh() }
    }

    fun updateTask(taskId: String, update: TaskUpdate) {
        viewModelScope.launch { runCatching { tasks.updateLocalTask(taskId, update) }.onSuccess { TaskSyncScheduler.enqueue(appContext); TaskReminderWorker.schedule(appContext, taskId, update.title, update.dueAt) }; refresh() }
    }

    fun keepServerVersion(mutationId: String) {
        viewModelScope.launch { tasks.keepServerVersion(mutationId) }
    }

    fun keepLocalVersion(conflict: TaskConflictEntity) {
        viewModelScope.launch { runCatching { tasks.keepLocalVersion(conflict) }.onSuccess { TaskSyncScheduler.enqueue(appContext) } }
    }

    fun keepServerProjectVersion(mutationId: String) {
        viewModelScope.launch { projectsRepository.keepServerVersion(mutationId) }
    }

    fun keepLocalProjectVersion(conflict: ru.taskflow.app.data.local.ProjectConflictEntity) {
        viewModelScope.launch { runCatching { projectsRepository.keepLocalVersion(conflict) }.onSuccess { TaskSyncScheduler.enqueue(appContext) } }
    }

    fun today(tasks: List<TaskEntity>, date: String): List<TaskEntity> = tasks.filter { it.scheduledDate == date }
    fun inbox(tasks: List<TaskEntity>): List<TaskEntity> = tasks.filter { it.status == "inbox" }
}

class TaskListViewModelFactory(private val database: ru.taskflow.app.data.local.TaskFlowDatabase, private val tokenStore: TokenStore, private val appContext: Context) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val tasks = TaskRepository(database)
        val serverUrl = checkNotNull(tokenStore.serverUrl()) { "Сервер не настроен" }
        val api = TaskFlowApiFactory(tokenStore).create(serverUrl)
        val projects = ProjectRepository(api, database)
        val kanban = KanbanRepository(api, database)
        return TaskListViewModel(tasks, projects, kanban, SyncRepository(api, tasks, projects), appContext) as T
    }
}
