@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package ru.taskflow.app.ui.tasks

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import ru.taskflow.app.data.local.ProjectEntity
import ru.taskflow.app.data.local.TaskEntity
import ru.taskflow.app.ui.components.EmptyState
import ru.taskflow.app.ui.theme.TaskFlowSpace
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
    onUpdate: (String, String, String, String, String?, String?, String?) -> Unit,
    onRefresh: () -> Unit = {},
    syncing: Boolean = false,
) {
    var editingTask by remember { mutableStateOf<TaskEntity?>(null) }
    var projectFilter by remember { mutableStateOf<String?>(null) }
    var priorityFilter by remember { mutableStateOf<String?>(null) }
    var statusFilter by remember { mutableStateOf<String?>(null) }

    val filteredTasks = tasks.filter { task ->
        (projectFilter == null || task.projectId == projectFilter) &&
            (priorityFilter == null || task.priority == priorityFilter) &&
            (statusFilter == null || task.status == statusFilter)
    }
    val activeTasks = filteredTasks.filter { it.status != "done" }
    val completedTasks = filteredTasks.filter { it.status == "done" }

    Column(verticalArrangement = Arrangement.spacedBy(TaskFlowSpace.sm)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "${activeTasks.size} активных",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            IconButton(onClick = onRefresh, enabled = !syncing) {
                if (syncing) {
                    CircularProgressIndicator()
                } else {
                    Icon(Icons.Outlined.Refresh, contentDescription = "Синхронизировать")
                }
            }
        }

        onCreate?.let { createTask ->
            QuickAdd(onCreate = createTask)
        }
        TaskFilters(
            projects = projects,
            project = projectFilter,
            priority = priorityFilter,
            status = statusFilter,
            onProject = { projectFilter = it },
            onPriority = { priorityFilter = it },
            onStatus = { statusFilter = it },
        )

        if (filteredTasks.isEmpty()) {
            EmptyState(emptyTitle, emptyDescription)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(TaskFlowSpace.sm)) {
                items(activeTasks, key = TaskEntity::id) { task ->
                    TaskCard(task, onComplete, onDelete) { editingTask = task }
                }
                if (completedTasks.isNotEmpty()) {
                    item {
                        Text(
                            text = "Завершено (${completedTasks.size})",
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                    items(completedTasks, key = TaskEntity::id) { task ->
                        TaskCard(task, onComplete, onDelete) { editingTask = task }
                    }
                }
            }
        }
    }

    editingTask?.let { task ->
        TaskEditorDialog(
            task = task,
            projects = projects,
            onDismiss = { editingTask = null },
            onSave = { title, priority, description, projectId, scheduledDate, dueAt ->
                onUpdate(task.id, title, priority, description, projectId, scheduledDate, dueAt)
                editingTask = null
            },
        )
    }
}

@Composable
private fun QuickAdd(onCreate: CreateTask) {
    var title by remember { mutableStateOf("") }
    var priority by remember { mutableStateOf("normal") }
    var scheduledDate by remember { mutableStateOf<LocalDate?>(null) }
    var datePickerOpen by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    fun submit() {
        title.trim().takeIf(String::isNotBlank)?.let { taskTitle ->
            onCreate(taskTitle, priority, scheduledDate?.toString())
            title = ""
            priority = "normal"
            scheduledDate = null
        }
    }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    OutlinedTextField(
        value = title,
        onValueChange = { title = it },
        modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
        label = { Text("Новая задача") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { submit() }),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(TaskFlowSpace.xs)) {
        priorities.forEach { value ->
            TextButton(onClick = { priority = value }) {
                Text(if (priority == value) "[$value]" else value)
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(TaskFlowSpace.xs)) {
        TextButton(onClick = { datePickerOpen = true }) {
            Text(scheduledDate?.let { "Дата: $it" } ?: "Запланировать")
        }
        if (scheduledDate != null) {
            TextButton(onClick = { scheduledDate = null }) { Text("Очистить") }
        }
    }
    Button(
        onClick = ::submit,
        enabled = title.isNotBlank(),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Добавить во входящие")
    }

    if (datePickerOpen) {
        val state = rememberDatePickerState(
            scheduledDate?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { datePickerOpen = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        scheduledDate = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                    }
                    datePickerOpen = false
                }) { Text("Готово") }
            },
            dismissButton = {
                TextButton(onClick = { datePickerOpen = false }) { Text("Отмена") }
            },
        ) {
            DatePicker(state)
        }
    }
}

@Composable
private fun TaskCard(
    task: TaskEntity,
    onComplete: (String) -> Unit,
    onDelete: (String) -> Unit,
    onEdit: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(TaskFlowSpace.md),
            verticalArrangement = Arrangement.spacedBy(TaskFlowSpace.xs),
        ) {
            Text(task.title, style = MaterialTheme.typography.titleMedium)
            if (task.description.isNotBlank()) {
                Text(task.description, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                text = task.priority,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            task.scheduledDate?.let {
                Text("Запланирована: $it", style = MaterialTheme.typography.labelMedium)
            }
            task.dueAt?.let {
                Text("Срок: ${dueDate(it)}", style = MaterialTheme.typography.labelMedium)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Outlined.Edit, contentDescription = "Редактировать")
                }
                if (task.status != "done") {
                    IconButton(onClick = { onComplete(task.id) }) {
                        Icon(Icons.Outlined.Check, contentDescription = "Завершить")
                    }
                }
                IconButton(onClick = { onDelete(task.id) }) {
                    Icon(Icons.Outlined.Delete, contentDescription = "Удалить")
                }
            }
        }
    }
}

@Composable
private fun TaskFilters(
    projects: List<ProjectEntity>,
    project: String?,
    priority: String?,
    status: String?,
    onProject: (String?) -> Unit,
    onPriority: (String?) -> Unit,
    onStatus: (String?) -> Unit,
) {
    Column {
        Text("Фильтры", style = MaterialTheme.typography.labelLarge)
        Row {
            TextButton(onClick = { onProject(null) }) {
                Text(if (project == null) "[Все]" else "Все")
            }
            projects.forEach { item ->
                TextButton(onClick = { onProject(item.id) }) {
                    Text(if (project == item.id) "[${item.name}]" else item.name)
                }
            }
        }
        Row {
            TextButton(onClick = { onStatus(null) }) {
                Text(if (status == null) "[Все статусы]" else "Статусы")
            }
            TextButton(onClick = { onStatus("inbox") }) { Text("Входящие") }
            TextButton(onClick = { onStatus("done") }) { Text("Готово") }
        }
        Row {
            TextButton(onClick = { onPriority(null) }) {
                Text(if (priority == null) "[Все приоритеты]" else "Приоритеты")
            }
            priorities.forEach { value ->
                TextButton(onClick = { onPriority(value) }) { Text(value) }
            }
        }
    }
}

@Composable
private fun TaskEditorDialog(
    task: TaskEntity,
    projects: List<ProjectEntity>,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String?, String?, String?) -> Unit,
) {
    var title by remember(task.id) { mutableStateOf(task.title) }
    var priority by remember(task.id) { mutableStateOf(task.priority) }
    var description by remember(task.id) { mutableStateOf(task.description) }
    var projectId by remember(task.id) { mutableStateOf(task.projectId) }
    var scheduledDate by remember(task.id) { mutableStateOf(task.scheduledDate?.let(LocalDate::parse)) }
    var dueDate by remember(task.id) { mutableStateOf(task.dueAt?.let(::dueDate)) }
    var dueHour by remember(task.id) { mutableIntStateOf(task.dueAt?.let(::dueHour) ?: 9) }
    var dueMinute by remember(task.id) { mutableIntStateOf(task.dueAt?.let(::dueMinute) ?: 0) }
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
        updatedDueAt != task.dueAt

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
            Row {
                priorities.forEach { value ->
                    TextButton(onClick = { priority = value }) {
                        Text(if (priority == value) "[$value]" else value)
                    }
                }
            }
            Text("Проект", style = MaterialTheme.typography.labelLarge)
            Row {
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = ::requestDismiss) { Text("Отмена") }
                Button(
                    onClick = {
                        onSave(
                            title.trim(),
                            priority,
                            description.trim(),
                            projectId,
                            scheduledDate?.toString(),
                            updatedDueAt,
                        )
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

private val priorities = listOf("low", "normal", "high", "urgent")

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
