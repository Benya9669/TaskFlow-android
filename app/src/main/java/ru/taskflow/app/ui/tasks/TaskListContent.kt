@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package ru.taskflow.app.ui.tasks

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AssistChip
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.TimePicker
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import kotlinx.coroutines.launch
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import ru.taskflow.app.data.TaskUpdate
import ru.taskflow.app.data.local.ProjectEntity
import ru.taskflow.app.data.local.TaskEntity
import ru.taskflow.app.ui.components.EmptyState
import ru.taskflow.app.ui.theme.Danger
import ru.taskflow.app.ui.theme.LowPriority
import ru.taskflow.app.ui.theme.Success
import ru.taskflow.app.ui.theme.TaskFlowSpace
import ru.taskflow.app.ui.theme.Urgent
import ru.taskflow.app.ui.theme.Warning
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset

private typealias CreateTask = (String, String, String?) -> Unit

@Composable
fun TaskListContent(
    tasks: List<TaskEntity>,
    projects: List<ProjectEntity>,
    emptyTitle: String,
    emptyDescription: String,
    onCreate: CreateTask? = null,
    onComplete: (String) -> Unit,
    onDelete: (String) -> Unit,
    onUpdate: (String, TaskUpdate) -> Unit,
    onRefresh: () -> Unit = {},
    syncing: Boolean = false,
    syncError: String? = null,
    onDismissError: () -> Unit = {},
    onReopen: (String) -> Unit = {},
) {
    var editingTask by remember { mutableStateOf<TaskEntity?>(null) }
    var deletingTask by remember { mutableStateOf<TaskEntity?>(null) }
    var projectFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var priorityFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var statusFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var sort by rememberSaveable { mutableStateOf(TaskSort.Updated) }
    var createOpen by rememberSaveable { mutableStateOf(false) }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var optionsOpen by rememberSaveable { mutableStateOf(false) }
    var pendingDeletionIds by remember { mutableStateOf(emptySet<String>()) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val filteredTasks = tasks.filter { task ->
        task.id !in pendingDeletionIds &&
        (projectFilter == null || task.projectId == projectFilter) &&
            (priorityFilter == null || task.priority == priorityFilter) &&
            (statusFilter == null || task.status == statusFilter) &&
            (searchQuery.isBlank() || task.title.contains(searchQuery.trim(), ignoreCase = true) || task.description.contains(searchQuery.trim(), ignoreCase = true))
    }.sortedWith(taskComparator(sort))
    val activeTasks = filteredTasks.filter { it.status != "done" }
    val completedTasks = filteredTasks.filter { it.status == "done" }

    Box(modifier = Modifier.fillMaxSize()) {
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(TaskFlowSpace.md)) {
        FocusSummary(activeTasks.size, completedTasks.size, syncing, onRefresh)
        syncError?.let {
            Surface(color = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer, shape = MaterialTheme.shapes.small) {
                Row(Modifier.fillMaxWidth().padding(TaskFlowSpace.sm), verticalAlignment = Alignment.CenterVertically) {
                    Text(it, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    IconButton(onClick = onDismissError) { Icon(Icons.Outlined.Close, "Скрыть ошибку") }
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(TaskFlowSpace.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            onCreate?.let {
                Button(onClick = { createOpen = true }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Outlined.Add, contentDescription = null)
                    Text("Новая задача", modifier = Modifier.padding(start = TaskFlowSpace.xs))
                }
            }
            FilledTonalIconButton(onClick = { searchOpen = !searchOpen }) {
                Icon(if (searchOpen) Icons.Outlined.Close else Icons.Outlined.Search, if (searchOpen) "Закрыть поиск" else "Открыть поиск")
            }
            FilledTonalIconButton(onClick = { optionsOpen = true }) {
                Icon(Icons.Outlined.Tune, if (projectFilter != null || priorityFilter != null || statusFilter != null || sort != TaskSort.Updated) "Настройки списка, применены фильтры" else "Настройки списка")
            }
        }
        if (searchOpen) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Поиск задач") },
                singleLine = true,
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                trailingIcon = if (searchQuery.isBlank()) null else ({ IconButton(onClick = { searchQuery = "" }) { Icon(Icons.Outlined.Close, "Очистить поиск") } }),
            )
        }

        if (filteredTasks.isEmpty()) EmptyState(emptyTitle, emptyDescription)
        else LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(TaskFlowSpace.sm)) {
            items(activeTasks, key = TaskEntity::id) { task ->
                TaskCard(task, onComplete = {
                    onComplete(task.id)
                    scope.launch {
                        if (snackbarHostState.showSnackbar("Задача завершена", "Вернуть") == SnackbarResult.ActionPerformed) onReopen(task.id)
                    }
                }, onDelete = { deletingTask = task }, onOpen = { editingTask = task })
            }
            if (completedTasks.isNotEmpty()) {
                item { Text("Завершено (${completedTasks.size})", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = TaskFlowSpace.sm)) }
                items(completedTasks, key = TaskEntity::id) { task -> TaskCard(task, onComplete = {}, onDelete = { deletingTask = task }, onOpen = { editingTask = task }) }
            }
        }
    }
        SnackbarHost(snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = TaskFlowSpace.sm))
    }
    editingTask?.let { task -> TaskEditorDialog(task, projects, { editingTask = null }) { update ->
        onUpdate(task.id, update); editingTask = null
    } }
    deletingTask?.let { task ->
        AlertDialog(
            onDismissRequest = { deletingTask = null },
            title = { Text("Удалить задачу?") },
            text = { Text("«${task.title}» будет удалена на всех устройствах после синхронизации.") },
            confirmButton = { Button(onClick = {
                deletingTask = null
                pendingDeletionIds = pendingDeletionIds + task.id
                scope.launch {
                    val result = snackbarHostState.showSnackbar("Задача удалена", "Отменить")
                    if (result != SnackbarResult.ActionPerformed) onDelete(task.id)
                    pendingDeletionIds = pendingDeletionIds - task.id
                }
            }) { Text("Удалить") } },
            dismissButton = { TextButton(onClick = { deletingTask = null }) { Text("Отмена") } },
        )
    }
    if (createOpen && onCreate != null) {
        TaskCreateSheet(onCreate = onCreate, onDismiss = { createOpen = false })
    }
    if (optionsOpen) {
        TaskListOptionsSheet(
            projects = projects,
            sort = sort,
            project = projectFilter,
            priority = priorityFilter,
            status = statusFilter,
            onSort = { sort = it },
            onProject = { projectFilter = it },
            onPriority = { priorityFilter = it },
            onStatus = { statusFilter = it },
            onReset = { sort = TaskSort.Updated; projectFilter = null; priorityFilter = null; statusFilter = null },
            onDismiss = { optionsOpen = false },
        )
    }
}

@Composable
private fun FocusSummary(activeCount: Int, completedCount: Int, syncing: Boolean, onRefresh: () -> Unit) {
    val total = activeCount + completedCount
    val progress = if (total == 0) 0f else completedCount.toFloat() / total
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = androidx.compose.ui.graphics.Color(0xFF272824),
        contentColor = androidx.compose.ui.graphics.Color.White,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(Modifier.padding(TaskFlowSpace.md), verticalArrangement = Arrangement.spacedBy(TaskFlowSpace.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("В фокусе", style = MaterialTheme.typography.titleMedium)
                    Text(activeTaskLabel(activeCount), style = MaterialTheme.typography.bodyMedium, color = androidx.compose.ui.graphics.Color(0xFFBFC0B9))
                }
                Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.titleMedium, color = androidx.compose.ui.graphics.Color(0xFFBFB5FF))
                IconButton(onClick = onRefresh, enabled = !syncing) {
                    if (syncing) CircularProgressIndicator(modifier = Modifier.size(TaskFlowSpace.lg), strokeWidth = TaskFlowSpace.xs, color = androidx.compose.ui.graphics.Color(0xFFBFB5FF))
                    else Icon(Icons.Outlined.Refresh, contentDescription = "Синхронизировать", tint = androidx.compose.ui.graphics.Color.White)
                }
            }
            androidx.compose.material3.LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth(),
                color = androidx.compose.ui.graphics.Color(0xFFA99CFF),
                trackColor = androidx.compose.ui.graphics.Color(0xFF484944),
            )
        }
    }
}

@Composable
private fun TaskCreateSheet(onCreate: CreateTask, onDismiss: () -> Unit) {
    var title by rememberSaveable { mutableStateOf("") }
    var priority by rememberSaveable { mutableStateOf("normal") }
    var scheduledDate by remember { mutableStateOf<LocalDate?>(null) }
    var datePickerOpen by remember { mutableStateOf(false) }
    fun submit() { title.trim().takeIf(String::isNotBlank)?.let { onCreate(it, priority, scheduledDate?.toString()); onDismiss() } }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = TaskFlowSpace.md, vertical = TaskFlowSpace.sm), verticalArrangement = Arrangement.spacedBy(TaskFlowSpace.md)) {
            Text("Новая задача", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(value = title, onValueChange = { title = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Название") }, singleLine = true, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { submit() }))
            Text("Приоритет", style = MaterialTheme.typography.labelLarge)
            PriorityPicker(priority) { priority = it }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TaskFlowSpace.sm)) {
                AssistChip(onClick = { datePickerOpen = true }, label = { Text(scheduledDate?.toString() ?: "Дата") }, leadingIcon = { Icon(Icons.Outlined.CalendarToday, null, Modifier.size(TaskFlowSpace.md)) })
                if (scheduledDate != null) TextButton(onClick = { scheduledDate = null }) { Text("Очистить") }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("Отмена") }
                Button(onClick = ::submit, enabled = title.isNotBlank()) { Text("Добавить") }
            }
        }
    }
    if (datePickerOpen) {
        val state = androidx.compose.material3.rememberDatePickerState(scheduledDate?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli())
        DatePickerDialog(onDismissRequest = { datePickerOpen = false }, confirmButton = { TextButton(onClick = { state.selectedDateMillis?.let { scheduledDate = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }; datePickerOpen = false }) { Text("Готово") } }, dismissButton = { TextButton(onClick = { datePickerOpen = false }) { Text("Отмена") } }) { DatePicker(state) }
    }
}

@Composable
private fun TaskCard(task: TaskEntity, onComplete: () -> Unit, onDelete: () -> Unit, onOpen: () -> Unit) {
    val isDone = task.status == "done"
    var menuOpen by remember { mutableStateOf(false) }
    ElevatedCard(modifier = Modifier.fillMaxWidth().alpha(if (isDone) .68f else 1f).clickable(onClick = onOpen), colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.padding(TaskFlowSpace.md), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(TaskFlowSpace.sm)) {
            if (!isDone) IconButton(onClick = onComplete) { Icon(Icons.Outlined.Check, "Завершить", tint = Success) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(TaskFlowSpace.xs)) {
                Text(task.title, style = MaterialTheme.typography.titleMedium)
                if (task.description.isNotBlank()) Text(task.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(TaskFlowSpace.xs), verticalAlignment = Alignment.CenterVertically) {
                    PriorityBadge(task.priority)
                    task.scheduledDate?.let { MetaBadge("На $it") }
                    task.dueAt?.let {
                        if (!isDone && isOverdue(it)) MetaBadge("Просрочено: ${dueDate(it)}", MaterialTheme.colorScheme.onErrorContainer, MaterialTheme.colorScheme.errorContainer)
                        else MetaBadge("Срок: ${dueDate(it)}")
                    }
                }
            }
            Box {
                IconButton(onClick = { menuOpen = true }) { Icon(Icons.Outlined.MoreVert, "Действия задачи") }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(text = { Text("Открыть") }, leadingIcon = { Icon(Icons.Outlined.Edit, null) }, onClick = { menuOpen = false; onOpen() })
                    DropdownMenuItem(text = { Text("Удалить", color = MaterialTheme.colorScheme.error) }, leadingIcon = { Icon(Icons.Outlined.Delete, null, tint = MaterialTheme.colorScheme.error) }, onClick = { menuOpen = false; onDelete() })
                }
            }
        }
    }
}

@Composable private fun PriorityPicker(selected: String, onSelected: (String) -> Unit) {
    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(TaskFlowSpace.xs)) {
        priorities.forEach { priority -> FilterChip(selected = selected == priority, onClick = { onSelected(priority) }, label = { Text(priorityLabel(priority)) }) }
    }
}

@Composable private fun PriorityBadge(priority: String) {
    val color = priorityColor(priority)
    Surface(color = color.copy(alpha = .14f), shape = MaterialTheme.shapes.extraSmall) { Text(priorityLabel(priority), modifier = Modifier.padding(horizontal = TaskFlowSpace.sm, vertical = TaskFlowSpace.xs), style = MaterialTheme.typography.labelLarge, color = color) }
}
@Composable private fun MetaBadge(label: String, contentColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant, containerColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.surfaceVariant) { Surface(color = containerColor, shape = MaterialTheme.shapes.extraSmall) { Text(label, modifier = Modifier.padding(horizontal = TaskFlowSpace.sm, vertical = TaskFlowSpace.xs), style = MaterialTheme.typography.labelLarge, color = contentColor) } }

@Composable
private fun TaskSortPicker(selected: TaskSort, onSelected: (TaskSort) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(TaskFlowSpace.xs)) {
        TaskSort.entries.forEach { value ->
            FilterChip(selected = selected == value, onClick = { onSelected(value) }, label = { Text(value.label) })
        }
    }
}

@Composable
private fun TaskListOptionsSheet(
    projects: List<ProjectEntity>,
    sort: TaskSort,
    project: String?,
    priority: String?,
    status: String?,
    onSort: (TaskSort) -> Unit,
    onProject: (String?) -> Unit,
    onPriority: (String?) -> Unit,
    onStatus: (String?) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = TaskFlowSpace.md, vertical = TaskFlowSpace.sm), verticalArrangement = Arrangement.spacedBy(TaskFlowSpace.md)) {
            Text("Настройки списка", style = MaterialTheme.typography.titleLarge)
            Text("Сортировка", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TaskSortPicker(sort, onSort)
            TaskFilters(projects, project, priority, status, onProject, onPriority, onStatus)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = onReset) { Text("Сбросить") }
                Button(onClick = onDismiss) { Text("Готово") }
            }
        }
    }
}

@Composable
private fun TaskFilters(projects: List<ProjectEntity>, project: String?, priority: String?, status: String?, onProject: (String?) -> Unit, onPriority: (String?) -> Unit, onStatus: (String?) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(TaskFlowSpace.xs)) {
        Text("Фильтры", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(TaskFlowSpace.sm)) {
            FilterChip(selected = status == null, onClick = { onStatus(null) }, label = { Text("Все") })
            FilterChip(selected = status == "inbox", onClick = { onStatus("inbox") }, label = { Text("Входящие") })
            FilterChip(selected = status == "done", onClick = { onStatus("done") }, label = { Text("Готово") })
            FilterChip(selected = priority != null, onClick = { onPriority(if (priority == null) "high" else null) }, label = { Text(priority?.let(::priorityLabel) ?: "Приоритет") })
            projects.take(3).forEach { item -> FilterChip(selected = project == item.id, onClick = { onProject(if (project == item.id) null else item.id) }, label = { Text(item.name) }) }
        }
    }
}

private val priorities = listOf("low", "normal", "high", "urgent")
private enum class TaskSort(val label: String) { Updated("Недавние"), Due("По сроку"), Priority("По приоритету"), Title("По названию") }
private fun taskComparator(sort: TaskSort): Comparator<TaskEntity> = when (sort) {
    TaskSort.Updated -> compareByDescending(TaskEntity::updatedAt)
    TaskSort.Due -> compareBy<TaskEntity> { it.dueAt == null }.thenBy { it.dueAt }
    TaskSort.Priority -> compareByDescending<TaskEntity> { priorityRank(it.priority) }.thenByDescending { it.updatedAt }
    TaskSort.Title -> compareBy(String.CASE_INSENSITIVE_ORDER, TaskEntity::title)
}
private fun priorityRank(priority: String) = when (priority) { "urgent" -> 4; "high" -> 3; "normal" -> 2; else -> 1 }
private fun priorityLabel(priority: String) = when (priority) { "low" -> "Низкий"; "high" -> "Высокий"; "urgent" -> "Срочный"; else -> "Обычный" }
@Composable private fun priorityColor(priority: String) = when (priority) { "low" -> LowPriority; "high" -> Warning; "urgent" -> Urgent; else -> MaterialTheme.colorScheme.primary }
private fun taskWord(count: Int) = when { count % 10 == 1 && count % 100 != 11 -> "задача"; count % 10 in 2..4 && count % 100 !in 12..14 -> "задачи"; else -> "задач" }
private fun activeTaskLabel(count: Int) = when {
    count % 10 == 1 && count % 100 != 11 -> "$count активная задача"
    count % 10 in 2..4 && count % 100 !in 12..14 -> "$count активные задачи"
    else -> "$count активных задач"
}

@Composable
private fun TaskEditorDialog(
    task: TaskEntity,
    projects: List<ProjectEntity>,
    onDismiss: () -> Unit,
    onSave: (TaskUpdate) -> Unit,
) {
    var title by remember(task.id) { mutableStateOf(task.title) }
    var priority by remember(task.id) { mutableStateOf(task.priority) }
    var description by remember(task.id) { mutableStateOf(task.description) }
    var projectId by remember(task.id) { mutableStateOf(task.projectId) }
    var scheduledDate by remember(task.id) { mutableStateOf(task.scheduledDate?.let(LocalDate::parse)) }
    var dueDate by remember(task.id) { mutableStateOf(task.dueAt?.let(::dueDate)) }
    var dueHour by remember(task.id) { mutableIntStateOf(task.dueAt?.let(::dueHour) ?: 9) }
    var dueMinute by remember(task.id) { mutableIntStateOf(task.dueAt?.let(::dueMinute) ?: 0) }
    var estimatedMinutes by remember(task.id) { mutableStateOf(task.estimatedMinutes?.toString().orEmpty()) }
    var tagsText by remember(task.id) { mutableStateOf(task.tags.joinToString(", ")) }
    var reminderOffsets by remember(task.id) { mutableStateOf(task.reminderOffsets.toSet()) }
    var picker by remember { mutableStateOf<Picker?>(null) }
    var discardPrompt by remember { mutableStateOf(false) }

    val updatedDueAt = dueDate
        ?.atTime(dueHour, dueMinute)
        ?.atZone(ZoneId.systemDefault())
        ?.toInstant()
        ?.toString()
    val dirty = title != task.title ||
        priority != task.priority ||
        description != task.description ||
        projectId != task.projectId ||
        scheduledDate?.toString() != task.scheduledDate ||
        updatedDueAt != task.dueAt ||
        estimatedMinutes.toIntOrNull() != task.estimatedMinutes ||
        tagsText.split(',').map(String::trim).filter(String::isNotBlank) != task.tags ||
        reminderOffsets.sorted() != task.reminderOffsets.sorted()

    fun requestDismiss() {
        if (dirty) discardPrompt = true else onDismiss()
    }

    BackHandler(onBack = ::requestDismiss)
    ModalBottomSheet(
        onDismissRequest = ::requestDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = TaskFlowSpace.md, vertical = TaskFlowSpace.sm),
            verticalArrangement = Arrangement.spacedBy(TaskFlowSpace.sm),
        ) {
            Text("Редактировать задачу", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Название") },
                isError = title.isBlank(),
                supportingText = {
                    if (title.isBlank()) Text("Название обязательно")
                },
            )
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Описание") },
            )
            Text("Приоритет", style = MaterialTheme.typography.labelLarge)
            PriorityPicker(priority) { priority = it }
            Text("Проект", style = MaterialTheme.typography.labelLarge)
            Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                TextButton(onClick = { projectId = null }) {
                    Text(if (projectId == null) "[Без проекта]" else "Без проекта")
                }
                projects.forEach { project ->
                    TextButton(onClick = { projectId = project.id }) {
                        Text(if (projectId == project.id) "[${project.name}]" else project.name)
                    }
                }
            }
            DateField(
                label = "Запланировать",
                value = scheduledDate,
                onPick = { picker = Picker.Scheduled },
                onClear = { scheduledDate = null },
            )
            DateField(
                label = "Срок",
                value = dueDate,
                onPick = { picker = Picker.DueDate },
                onClear = { dueDate = null },
            )
            if (dueDate != null) {
                TextButton(onClick = { picker = Picker.DueTime }) {
                    Text("Время: %02d:%02d".format(dueHour, dueMinute))
                }
            }
            OutlinedTextField(
                value = estimatedMinutes,
                onValueChange = { value -> estimatedMinutes = value.filter(Char::isDigit).take(6) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Оценка, минут") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            OutlinedTextField(
                value = tagsText,
                onValueChange = { tagsText = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Теги через запятую") },
                supportingText = { Text("Например: работа, важно") },
            )
            Text("Напоминания", style = MaterialTheme.typography.labelLarge)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(TaskFlowSpace.xs)) {
                reminderChoices.forEach { (offset, label) ->
                    FilterChip(
                        selected = offset in reminderOffsets,
                        onClick = { reminderOffsets = if (offset in reminderOffsets) reminderOffsets - offset else reminderOffsets + offset },
                        label = { Text(label) },
                        enabled = dueDate != null,
                    )
                }
            }
            if (dueDate == null) Text("Сначала укажите срок", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = ::requestDismiss) { Text("Отмена") }
                Button(
                    onClick = {
                        onSave(TaskUpdate(
                            title = title.trim(),
                            priority = priority,
                            description = description.trim(),
                            projectId = projectId,
                            scheduledDate = scheduledDate?.toString(),
                            dueAt = updatedDueAt,
                            estimatedMinutes = estimatedMinutes.toIntOrNull(),
                            tags = tagsText.split(',').map(String::trim).filter(String::isNotBlank).distinct(),
                            reminderOffsets = reminderOffsets.sorted(),
                        ))
                    },
                    enabled = title.isNotBlank(),
                ) { Text("Сохранить") }
            }
        }
    }

    if (discardPrompt) {
        AlertDialog(
            onDismissRequest = { discardPrompt = false },
            title = { Text("Не сохранять изменения?") },
            text = { Text("Несохранённые изменения будут потеряны.") },
            confirmButton = {
                TextButton(onClick = onDismiss) { Text("Не сохранять") }
            },
            dismissButton = {
                TextButton(onClick = { discardPrompt = false }) {
                    Text("Продолжить редактирование")
                }
            },
        )
    }

    when (picker) {
        Picker.Scheduled,
        Picker.DueDate -> {
            val isDueDate = picker == Picker.DueDate
            val selectedDate = if (isDueDate) dueDate else scheduledDate
            val state = rememberDatePickerState(
                selectedDate?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
            )
            DatePickerDialog(
                onDismissRequest = { picker = null },
                confirmButton = {
                    TextButton(onClick = {
                        state.selectedDateMillis?.let { millis ->
                            val selected = Instant.ofEpochMilli(millis)
                                .atZone(ZoneOffset.UTC)
                                .toLocalDate()
                            if (isDueDate) dueDate = selected else scheduledDate = selected
                        }
                        picker = null
                    }) { Text("Готово") }
                },
                dismissButton = {
                    TextButton(onClick = { picker = null }) { Text("Отмена") }
                },
            ) {
                DatePicker(state)
            }
        }

        Picker.DueTime -> {
            val state = rememberTimePickerState(dueHour, dueMinute)
            AlertDialog(
                onDismissRequest = { picker = null },
                title = { Text("Время срока") },
                text = { TimePicker(state) },
                confirmButton = {
                    TextButton(onClick = {
                        dueHour = state.hour
                        dueMinute = state.minute
                        picker = null
                    }) { Text("Готово") }
                },
                dismissButton = {
                    TextButton(onClick = { picker = null }) { Text("Отмена") }
                },
            )
        }

        null -> Unit
    }
}

@Composable
private fun DateField(
    label: String,
    value: LocalDate?,
    onPick: () -> Unit,
    onClear: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        TextButton(onClick = onPick) {
            Text(value?.let { "$label: $it" } ?: label)
        }
        if (value != null) {
            TextButton(onClick = onClear) { Text("Очистить") }
        }
    }
}

private enum class Picker { Scheduled, DueDate, DueTime }
private val reminderChoices = listOf(0 to "В срок", 15 to "За 15 минут", 60 to "За час", 1440 to "За день")

private fun dueDate(value: String): LocalDate = runCatching {
    OffsetDateTime.parse(value).toLocalDate()
}.getOrElse {
    LocalDate.parse(value.take(10))
}

private fun dueHour(value: String): Int = runCatching {
    OffsetDateTime.parse(value).hour
}.getOrDefault(9)

private fun dueMinute(value: String): Int = runCatching {
    OffsetDateTime.parse(value).minute
}.getOrDefault(0)

private fun isOverdue(value: String): Boolean = runCatching { Instant.parse(value).isBefore(Instant.now()) }
    .recoverCatching { OffsetDateTime.parse(value).toInstant().isBefore(Instant.now()) }
    .getOrDefault(false)
