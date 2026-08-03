package ru.taskflow.app.ui

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.taskflow.app.data.local.TaskFlowDatabase
import ru.taskflow.app.data.session.TokenStore
import ru.taskflow.app.ui.auth.LoginScreen
import ru.taskflow.app.ui.auth.SessionViewModel
import ru.taskflow.app.ui.auth.SessionViewModelFactory
import ru.taskflow.app.ui.tasks.TaskListContent
import ru.taskflow.app.ui.tasks.TaskListViewModel
import ru.taskflow.app.ui.tasks.TaskListViewModelFactory
import ru.taskflow.app.ui.tasks.ConflictDialog
import ru.taskflow.app.ui.projects.ProjectListContent
import ru.taskflow.app.ui.components.EmptyState
import ru.taskflow.app.ui.theme.TaskFlowSpace

private enum class Destination(val label: String, val icon: ImageVector) {
    Today("Сегодня", Icons.Outlined.CalendarToday),
    Inbox("Входящие", Icons.Outlined.Inbox),
    Projects("Проекты", Icons.Outlined.Workspaces),
    More("Ещё", Icons.Outlined.MoreHoriz),
}

@Composable
fun TaskFlowApp(sharedText: String? = null, taskIdFromLink: String? = null, verificationServerUrl: String? = null, verificationToken: String? = null) {
    val context = LocalContext.current.applicationContext
    val sessionViewModel: SessionViewModel = viewModel(factory = SessionViewModelFactory(TokenStore(context)))
    val session by sessionViewModel.state.collectAsStateWithLifecycle()
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
    val conflicts by taskListViewModel.conflicts.collectAsStateWithLifecycle()
    var destination by remember { mutableStateOf(if (taskIdFromLink != null) Destination.Inbox else Destination.Today) }
    LaunchedEffect(sharedText) {
        sharedText?.trim()?.takeIf(String::isNotBlank)?.let(taskListViewModel::createInboxTask)
    }
    val wideLayout = LocalConfiguration.current.screenWidthDp >= 720
    if (wideLayout) {
        androidx.compose.foundation.layout.Row(Modifier.fillMaxSize()) {
            NavigationRail {
                Destination.entries.forEach { item ->
                    NavigationRailItem(selected = destination == item, onClick = { destination = item }, icon = { Icon(item.icon, item.label) }, label = { Text(item.label) })
                }
            }
            TaskFlowContent(destination, PaddingValues(), tasks, projects, taskIdFromLink, taskListViewModel::createInboxTask, taskListViewModel::completeTask, taskListViewModel::deleteTask, taskListViewModel::updateTask)
        }
    } else {
        Scaffold(bottomBar = {
            NavigationBar {
                Destination.entries.forEach { item ->
                    NavigationBarItem(selected = destination == item, onClick = { destination = item }, icon = { Icon(item.icon, item.label) }, label = { Text(item.label) })
                }
            }
        }) { padding -> TaskFlowContent(destination, padding, tasks, projects, taskIdFromLink, taskListViewModel::createInboxTask, taskListViewModel::completeTask, taskListViewModel::deleteTask, taskListViewModel::updateTask) }
    }
    conflicts.firstOrNull()?.let { conflict ->
        ConflictDialog(conflict, onKeepServer = { taskListViewModel.keepServerVersion(conflict.mutationId) }, onKeepLocal = { taskListViewModel.keepLocalVersion(conflict) })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskFlowContent(destination: Destination, padding: PaddingValues, tasks: List<ru.taskflow.app.data.local.TaskEntity>, projects: List<ru.taskflow.app.data.local.ProjectEntity>, taskIdFromLink: String?, onCreateInboxTask: (String) -> Unit, onCompleteTask: (String) -> Unit, onDeleteTask: (String) -> Unit, onUpdateTask: (String, String, String, String, String?, String?, String?) -> Unit) {
    Scaffold(topBar = { TopAppBar(title = { Text(destination.label, style = MaterialTheme.typography.titleLarge) }) }) { contentPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(contentPadding).padding(horizontal = TaskFlowSpace.md),
            verticalArrangement = Arrangement.Center,
        ) {
            when (destination) {
                Destination.Today -> TaskListContent(tasks.filter { it.scheduledDate == java.time.LocalDate.now().toString() }, projects, "На сегодня задач нет", "Запланируйте задачу, чтобы увидеть её здесь.", onComplete = onCompleteTask, onDelete = onDeleteTask, onUpdate = onUpdateTask)
                Destination.Inbox -> TaskListContent(tasks.filter { it.status == "inbox" }.filter { taskIdFromLink == null || it.id == taskIdFromLink }, projects, "Входящие пусты", if (taskIdFromLink == null) "Новые несортированные задачи появятся здесь." else "Задача из ссылки ещё не синхронизирована.", onCreateInboxTask, onCompleteTask, onDeleteTask, onUpdateTask)
                Destination.Projects -> ProjectListContent(projects, tasks)
                Destination.More -> EmptyState("Ещё", "Настройки и дополнительные инструменты появятся позже.")
            }
        }
    }
}
