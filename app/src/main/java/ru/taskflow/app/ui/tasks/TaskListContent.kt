package ru.taskflow.app.ui.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Refresh
import ru.taskflow.app.data.local.TaskEntity
import ru.taskflow.app.data.local.ProjectEntity
import ru.taskflow.app.ui.components.EmptyState
import ru.taskflow.app.ui.theme.TaskFlowSpace
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset

@Composable
fun TaskListContent(tasks: List<TaskEntity>, projects: List<ProjectEntity>, emptyTitle: String, emptyDescription: String, onCreate: ((String) -> Unit)? = null, onComplete: (String) -> Unit, onDelete: (String) -> Unit, onUpdate: (String, String, String, String, String?, String?, String?) -> Unit, onRefresh: () -> Unit = {}, syncing: Boolean = false) {
    var title by remember { mutableStateOf("") }
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
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${activeTasks.size} активных", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            IconButton(onClick = onRefresh, enabled = !syncing) {
                if (syncing) CircularProgressIndicator() else Icon(Icons.Outlined.Refresh, "Синхронизировать")
            }
        }
        if (onCreate != null) {
            OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), label = { Text("Новая задача") }, singleLine = true)
            Button(onClick = { onCreate(title); title = "" }, enabled = title.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Добавить во входящие") }
        }
        TaskFilters(projects, projectFilter, priorityFilter, statusFilter, { projectFilter = it }, { priorityFilter = it }, { statusFilter = it })
    if (filteredTasks.isEmpty()) {
        EmptyState(emptyTitle, emptyDescription)
    } else {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(TaskFlowSpace.sm)) {
            items(activeTasks, key = TaskEntity::id) { task ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(TaskFlowSpace.md), verticalArrangement = Arrangement.spacedBy(TaskFlowSpace.xs)) {
                        Text(task.title, style = MaterialTheme.typography.titleMedium)
                        androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            IconButton(onClick = { editingTask = task }) { Icon(Icons.Outlined.Edit, "Редактировать") }
                            if (task.status != "done") IconButton(onClick = { onComplete(task.id) }) { Icon(Icons.Outlined.Check, "Завершить") }
                            IconButton(onClick = { onDelete(task.id) }) { Icon(Icons.Outlined.Delete, "Удалить") }
                        }
                        if (task.description.isNotBlank()) Text(task.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(task.priority.replaceFirstChar(Char::uppercase), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            if (completedTasks.isNotEmpty()) {
                item { Text("Завершено (${completedTasks.size})", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                items(completedTasks, key = TaskEntity::id) { task ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(TaskFlowSpace.md), verticalArrangement = Arrangement.spacedBy(TaskFlowSpace.xs)) {
                            Text(task.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                IconButton(onClick = { editingTask = task }) { Icon(Icons.Outlined.Edit, "Редактировать") }
                                IconButton(onClick = { onDelete(task.id) }) { Icon(Icons.Outlined.Delete, "Удалить") }
                            }
                        }
                    }
                }
            }
        }
    }
        editingTask?.let { task -> TaskEditorDialog(task, projects, onDismiss = { editingTask = null }) { taskTitle, priority, description, projectId, scheduledDate, dueAt -> onUpdate(task.id, taskTitle, priority, description, projectId, scheduledDate, dueAt); editingTask = null } }
    }
}

@Composable
private fun TaskFilters(projects: List<ProjectEntity>, projectFilter: String?, priorityFilter: String?, statusFilter: String?, onProject: (String?) -> Unit, onPriority: (String?) -> Unit, onStatus: (String?) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(TaskFlowSpace.xs)) {
        Text("Фильтры", style = MaterialTheme.typography.labelLarge)
        androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(TaskFlowSpace.xs)) {
            TextButton(onClick = { onProject(null) }) { Text(if (projectFilter == null) "[Все проекты]" else "Все проекты") }
            projects.forEach { project -> TextButton(onClick = { onProject(project.id) }) { Text(if (projectFilter == project.id) "[${project.name}]" else project.name) } }
        }
        androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(TaskFlowSpace.xs)) {
            listOf<String?>(null, "low", "normal", "high", "urgent").forEach { value -> TextButton(onClick = { onPriority(value) }) { Text(if (priorityFilter == value) "[${value ?: "Все"}]" else value ?: "Все") } }
            listOf<String?>(null, "inbox", "done").forEach { value -> TextButton(onClick = { onStatus(value) }) { Text(if (statusFilter == value) "[${value ?: "Все"}]" else value ?: "Все") } }
        }
    }
}

@Composable
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
private fun TaskEditorDialog(task: TaskEntity, projects: List<ProjectEntity>, onDismiss: () -> Unit, onSave: (String, String, String, String?, String?, String?) -> Unit) {
    var title by remember(task.id) { mutableStateOf(task.title) }
    var priority by remember(task.id) { mutableStateOf(task.priority) }
    var description by remember(task.id) { mutableStateOf(task.description) }
    var projectId by remember(task.id) { mutableStateOf(task.projectId) }
    var scheduledDate by remember(task.id) { mutableStateOf(task.scheduledDate?.let(LocalDate::parse)) }
    var dueDate by remember(task.id) { mutableStateOf(task.dueAt?.let(::dueDate)) }
    var dueHour by remember(task.id) { mutableIntStateOf(task.dueAt?.let(::dueHour) ?: 9) }
    var dueMinute by remember(task.id) { mutableIntStateOf(task.dueAt?.let(::dueMinute) ?: 0) }
    var picker by remember { mutableStateOf<Picker?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Редактировать задачу") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(TaskFlowSpace.sm)) {
                OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), label = { Text("Название") }, singleLine = true)
                OutlinedTextField(description, { description = it }, Modifier.fillMaxWidth(), label = { Text("Описание") })
                Text("Приоритет", style = MaterialTheme.typography.labelLarge)
                androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(TaskFlowSpace.xs)) {
                    listOf("low", "normal", "high", "urgent").forEach { value ->
                        TextButton(onClick = { priority = value }) { Text(if (priority == value) "[$value]" else value) }
                    }
                }
                Text("Проект", style = MaterialTheme.typography.labelLarge)
                androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(TaskFlowSpace.xs)) {
                    TextButton(onClick = { projectId = null }) { Text(if (projectId == null) "[Без проекта]" else "Без проекта") }
                    projects.forEach { project -> TextButton(onClick = { projectId = project.id }) { Text(if (projectId == project.id) "[${project.name}]" else project.name) } }
                }
                DateField("Запланировать", scheduledDate, onPick = { picker = Picker.Scheduled }, onClear = { scheduledDate = null })
                DateField("Срок", dueDate, onPick = { picker = Picker.DueDate }, onClear = { dueDate = null })
                if (dueDate != null) {
                    TextButton(onClick = { picker = Picker.DueTime }) { Text("Время: %02d:%02d".format(dueHour, dueMinute)) }
                }
            }
        },
        confirmButton = { Button(onClick = { onSave(title, priority, description, projectId, scheduledDate?.toString(), dueDate?.atTime(dueHour, dueMinute)?.atZone(ZoneId.systemDefault())?.toInstant()?.toString()) }, enabled = title.isNotBlank()) { Text("Сохранить") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
    when (picker) {
        Picker.Scheduled, Picker.DueDate -> {
            val isDueDate = picker == Picker.DueDate
            val initial = if (isDueDate) dueDate else scheduledDate
            val state = rememberDatePickerState(initial?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli())
            DatePickerDialog(onDismissRequest = { picker = null }, confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { millis ->
                        val date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                        if (isDueDate) dueDate = date else scheduledDate = date
                    }
                    picker = null
                }) { Text("Готово") }
            }, dismissButton = { TextButton(onClick = { picker = null }) { Text("Отмена") } }) { DatePicker(state) }
        }
        Picker.DueTime -> {
            val state = rememberTimePickerState(dueHour, dueMinute)
            AlertDialog(onDismissRequest = { picker = null }, title = { Text("Время срока") }, text = { TimePicker(state) }, confirmButton = {
                TextButton(onClick = { dueHour = state.hour; dueMinute = state.minute; picker = null }) { Text("Готово") }
            }, dismissButton = { TextButton(onClick = { picker = null }) { Text("Отмена") } })
        }
        null -> Unit
    }
}

@Composable
private fun DateField(label: String, value: LocalDate?, onPick: () -> Unit, onClear: () -> Unit) {
    androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        TextButton(onClick = onPick) { Text(if (value == null) label else "$label: $value") }
        if (value != null) TextButton(onClick = onClear) { Text("Очистить") }
    }
}

private enum class Picker { Scheduled, DueDate, DueTime }
private fun dueDate(value: String) = runCatching { OffsetDateTime.parse(value).toLocalDate() }.getOrElse { LocalDate.parse(value.take(10)) }
private fun dueHour(value: String) = runCatching { OffsetDateTime.parse(value).hour }.getOrDefault(9)
private fun dueMinute(value: String) = runCatching { OffsetDateTime.parse(value).minute }.getOrDefault(0)
