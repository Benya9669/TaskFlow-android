package ru.taskflow.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Workspaces
import androidx.compose.material.icons.outlined.ViewKanban
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.taskflow.app.data.local.TaskFlowDatabase
import ru.taskflow.app.BuildConfig
import ru.taskflow.app.data.session.TokenStore
import ru.taskflow.app.ui.auth.LoginScreen
import ru.taskflow.app.ui.auth.SessionViewModel
import ru.taskflow.app.ui.auth.SessionViewModelFactory
import ru.taskflow.app.ui.tasks.TaskListContent
import ru.taskflow.app.ui.tasks.TaskListViewModel
import ru.taskflow.app.ui.tasks.TaskListViewModelFactory
import ru.taskflow.app.ui.tasks.ConflictDialog
import ru.taskflow.app.ui.tasks.ProjectConflictDialog
import ru.taskflow.app.ui.projects.ProjectListContent
import ru.taskflow.app.ui.kanban.KanbanBoardContent
import ru.taskflow.app.ui.settings.MoreContent
import ru.taskflow.app.ui.theme.TaskFlowSpace

private enum class Destination(val label: String, val icon: ImageVector) {
    Today("Сегодня", Icons.Outlined.CalendarToday),
    Inbox("Входящие", Icons.Outlined.Inbox),
    Board("Доска", Icons.Outlined.ViewKanban),
    Projects("Проекты", Icons.Outlined.Workspaces),
    More("Ещё", Icons.Outlined.MoreHoriz),
}

@Composable
fun TaskFlowApp(sharedText: String? = null, taskIdFromLink: String? = null, verificationServerUrl: String? = null, verificationToken: String? = null) {
    val context = LocalContext.current.applicationContext
    val sessionViewModel: SessionViewModel = viewModel(factory = SessionViewModelFactory(TokenStore(context)))
    val session by sessionViewModel.state.collectAsStateWithLifecycle()
    val profile by sessionViewModel.profile.collectAsStateWithLifecycle()
    LaunchedEffect(verificationServerUrl, verificationToken) {
        if (verificationServerUrl != null || verificationToken != null) sessionViewModel.verifyEmail(verificationServerUrl, verificationToken)
    }
    if (!session.isSignedIn) {
        LoginScreen(session, sessionViewModel::login, sessionViewModel::register, sessionViewModel::dismissVerification)
        return
    }
    val taskListViewModel: TaskListViewModel = viewModel(factory = TaskListViewModelFactory(TaskFlowDatabase.get(context), TokenStore(context), context))
    val tasks by taskListViewModel.taskList.collectAsStateWithLifecycle()
    val projects by taskListViewModel.projects.collectAsStateWithLifecycle()
    val archivedProjects by taskListViewModel.archivedProjects.collectAsStateWithLifecycle()
    val kanbanColumns by taskListViewModel.kanbanColumns.collectAsStateWithLifecycle()
    val conflicts by taskListViewModel.conflicts.collectAsStateWithLifecycle()
    val projectConflicts by taskListViewModel.projectConflicts.collectAsStateWithLifecycle()
    val pendingCount by taskListViewModel.pendingCount.collectAsStateWithLifecycle()
    val syncing by taskListViewModel.syncing.collectAsStateWithLifecycle()
    val syncError by taskListViewModel.syncError.collectAsStateWithLifecycle()
    val projectBusy by taskListViewModel.projectBusy.collectAsStateWithLifecycle()
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val updateTask: (String, ru.taskflow.app.data.TaskUpdate) -> Unit = { id, update ->
        taskListViewModel.updateTask(id, update)
        if (
            update.dueAt != null &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    var destination by rememberSaveable { mutableStateOf(if (taskIdFromLink != null) Destination.Inbox else Destination.Today) }
    var selectedProjectId by rememberSaveable { mutableStateOf<String?>(null) }
    val openProjectTasks: (String) -> Unit = { projectId -> selectedProjectId = projectId; destination = Destination.Inbox }
    val notificationSettings = {
        context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }
    var permissionRevision by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) permissionRevision++ }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val notificationsEnabled = remember(permissionRevision) {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }
    LaunchedEffect(sharedText) {
        sharedText?.trim()?.takeIf(String::isNotBlank)?.let(taskListViewModel::createInboxTask)
    }
    val wideLayout = LocalConfiguration.current.screenWidthDp >= 720
    if (wideLayout) {
        androidx.compose.foundation.layout.Row(Modifier.fillMaxSize()) {
            NavigationRail {
                Destination.entries.forEach { item ->
                    NavigationRailItem(selected = destination == item && selectedProjectId == null, onClick = { destination = item; selectedProjectId = null }, icon = { Icon(item.icon, item.label) }, label = { Text(item.label) })
                }
            }
            TaskFlowContent(destination, PaddingValues(), tasks, projects, archivedProjects, kanbanColumns, selectedProjectId, taskIdFromLink, taskListViewModel::createInboxTask, taskListViewModel::completeTask, taskListViewModel::reopenTask, taskListViewModel::moveTask, taskListViewModel::deleteTask, updateTask, taskListViewModel::refresh, syncing, pendingCount, syncError, taskListViewModel::dismissError, projectBusy, openProjectTasks, taskListViewModel::createProject, taskListViewModel::updateProject, taskListViewModel::archiveProject, taskListViewModel::restoreProject, TokenStore(context).serverUrl(), notificationsEnabled, profile, notificationSettings, sessionViewModel::updateProfile, sessionViewModel::logout) { selectedProjectId = null; destination = Destination.Projects }
        }
    } else {
        Scaffold(bottomBar = {
            NavigationBar {
                Destination.entries.forEach { item ->
                    NavigationBarItem(selected = destination == item && selectedProjectId == null, onClick = { destination = item; selectedProjectId = null }, icon = { Icon(item.icon, item.label) }, label = { Text(item.label) })
                }
            }
        }) { padding -> TaskFlowContent(destination, padding, tasks, projects, archivedProjects, kanbanColumns, selectedProjectId, taskIdFromLink, taskListViewModel::createInboxTask, taskListViewModel::completeTask, taskListViewModel::reopenTask, taskListViewModel::moveTask, taskListViewModel::deleteTask, updateTask, taskListViewModel::refresh, syncing, pendingCount, syncError, taskListViewModel::dismissError, projectBusy, openProjectTasks, taskListViewModel::createProject, taskListViewModel::updateProject, taskListViewModel::archiveProject, taskListViewModel::restoreProject, TokenStore(context).serverUrl(), notificationsEnabled, profile, notificationSettings, sessionViewModel::updateProfile, sessionViewModel::logout) { selectedProjectId = null; destination = Destination.Projects } }
    }
    conflicts.firstOrNull()?.let { conflict ->
        ConflictDialog(conflict, onKeepServer = { taskListViewModel.keepServerVersion(conflict.mutationId) }, onKeepLocal = { taskListViewModel.keepLocalVersion(conflict) })
    }
    if (conflicts.isEmpty()) projectConflicts.firstOrNull()?.let { conflict ->
        ProjectConflictDialog(conflict, onKeepServer = { taskListViewModel.keepServerProjectVersion(conflict.mutationId) }, onKeepLocal = { taskListViewModel.keepLocalProjectVersion(conflict) })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskFlowContent(destination: Destination, padding: PaddingValues, tasks: List<ru.taskflow.app.data.local.TaskEntity>, projects: List<ru.taskflow.app.data.local.ProjectEntity>, archivedProjects: List<ru.taskflow.app.data.local.ProjectEntity>, kanbanColumns: List<ru.taskflow.app.data.local.KanbanColumnEntity>, selectedProjectId: String?, taskIdFromLink: String?, onCreateInboxTask: (String, String, String?) -> Unit, onCompleteTask: (String) -> Unit, onReopenTask: (String) -> Unit, onMoveTask: (String, ru.taskflow.app.data.local.KanbanColumnEntity) -> Unit, onDeleteTask: (String) -> Unit, onUpdateTask: (String, ru.taskflow.app.data.TaskUpdate) -> Unit, onRefresh: () -> Unit, syncing: Boolean, pendingCount: Int, syncError: String?, onDismissError: () -> Unit, projectBusy: Boolean, onOpenProjectTasks: (String) -> Unit, onCreateProject: (String, String) -> Unit, onUpdateProject: (ru.taskflow.app.data.local.ProjectEntity, String, String) -> Unit, onArchiveProject: (ru.taskflow.app.data.local.ProjectEntity) -> Unit, onRestoreProject: (ru.taskflow.app.data.local.ProjectEntity) -> Unit, serverUrl: String?, notificationsEnabled: Boolean, profile: ru.taskflow.app.ui.auth.ProfileUiState, onOpenNotificationSettings: () -> Unit, onSaveProfile: (String, String) -> Unit, onLogout: () -> Unit, onBackToProjects: () -> Unit) {
    val selectedProject = projects.firstOrNull { it.id == selectedProjectId }
    Scaffold(topBar = { TopAppBar(title = { Text(selectedProject?.name ?: destination.label, style = MaterialTheme.typography.titleLarge) }, navigationIcon = { if (selectedProject != null) androidx.compose.material3.IconButton(onClick = onBackToProjects) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Вернуться к проектам") } }, actions = { if (pendingCount > 0) Text("$pendingCount ожидает", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = TaskFlowSpace.md)) }) }) { contentPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(contentPadding).padding(horizontal = TaskFlowSpace.md, vertical = TaskFlowSpace.sm),
            verticalArrangement = Arrangement.Top,
        ) {
            when (destination) {
                Destination.Today -> TaskListContent(tasks.filter { it.scheduledDate == java.time.LocalDate.now().toString() }, projects, "На сегодня задач нет", "Запланируйте задачу, чтобы увидеть её здесь.", onComplete = onCompleteTask, onDelete = onDeleteTask, onUpdate = onUpdateTask, onRefresh = onRefresh, syncing = syncing, syncError = syncError, onDismissError = onDismissError, onReopen = onReopenTask)
                Destination.Inbox -> if (selectedProject != null) {
                    TaskListContent(tasks.filter { it.projectId == selectedProject.id }, projects, "В проекте пока нет задач", "Назначьте проект в редакторе задачи.", onComplete = onCompleteTask, onDelete = onDeleteTask, onUpdate = onUpdateTask, onRefresh = onRefresh, syncing = syncing, syncError = syncError, onDismissError = onDismissError, onReopen = onReopenTask)
                } else {
                    TaskListContent(tasks.filter { it.status == "inbox" }.filter { taskIdFromLink == null || it.id == taskIdFromLink }, projects, "Входящие пусты", if (taskIdFromLink == null) "Новые несортированные задачи появятся здесь." else "Задача из ссылки ещё не синхронизирована.", onCreateInboxTask, onCompleteTask, onDeleteTask, onUpdateTask, onRefresh, syncing, syncError, onDismissError, onReopenTask)
                }
                Destination.Board -> KanbanBoardContent(kanbanColumns, tasks, projects) { task, column -> onMoveTask(task.id, column) }
                Destination.Projects -> ProjectListContent(projects, archivedProjects, tasks, projectBusy, onOpenProjectTasks, onCreateProject, onUpdateProject, onArchiveProject, onRestoreProject)
                Destination.More -> MoreContent(serverUrl, BuildConfig.VERSION_NAME, syncing, syncError, notificationsEnabled, profile, onRefresh, onOpenNotificationSettings, onSaveProfile, onLogout)
            }
        }
    }
}
