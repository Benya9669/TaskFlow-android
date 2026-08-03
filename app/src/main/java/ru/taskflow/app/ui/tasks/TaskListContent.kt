package ru.taskflow.app.ui.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import ru.taskflow.app.data.local.TaskEntity
import ru.taskflow.app.data.local.ProjectEntity
import ru.taskflow.app.ui.components.EmptyState
import ru.taskflow.app.ui.theme.TaskFlowSpace

@Composable
fun TaskListContent(tasks: List<TaskEntity>, projects: List<ProjectEntity>, emptyTitle: String, emptyDescription: String, onCreate: ((String) -> Unit)? = null, onComplete: (String) -> Unit, onDelete: (String) -> Unit, onUpdate: (String, String, String, String, String?, String?, String?) -> Unit) {
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
    Column(verticalArrangement = Arrangement.spacedBy(TaskFlowSpace.sm)) {
        if (onCreate != null) {
            OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), label = { Text("Новая задача") }, singleLine = true)
            Button(onClick = { onCreate(title); title = "" }, enabled = title.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Добавить во входящие") }
        }
        TaskFilters(projects, projectFilter, priorityFilter, statusFilter, { projectFilter = it }, { priorityFilter = it }, { statusFilter = it })
    if (filteredTasks.isEmpty()) {
        EmptyState(emptyTitle, emptyDescription)
    } else {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(TaskFlowSpace.sm)) {
            items(filteredTasks, key = TaskEntity::id) { task ->
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
private fun TaskEditorDialog(task: TaskEntity, projects: List<ProjectEntity>, onDismiss: () -> Unit, onSave: (String, String, String, String?, String?, String?) -> Unit) {
    var title by remember(task.id) { mutableStateOf(task.title) }
    var priority by remember(task.id) { mutableStateOf(task.priority) }
    var description by remember(task.id) { mutableStateOf(task.description) }
    var projectId by remember(task.id) { mutableStateOf(task.projectId) }
    var scheduledDate by remember(task.id) { mutableStateOf(task.scheduledDate.orEmpty()) }
    var dueAt by remember(task.id) { mutableStateOf(task.dueAt.orEmpty()) }
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
                OutlinedTextField(scheduledDate, { scheduledDate = it }, Modifier.fillMaxWidth(), label = { Text("Дата, ГГГГ-ММ-ДД") }, singleLine = true)
                OutlinedTextField(dueAt, { dueAt = it }, Modifier.fillMaxWidth(), label = { Text("Срок ISO-8601") }, singleLine = true)
            }
        },
        confirmButton = { Button(onClick = { onSave(title, priority, description, projectId, scheduledDate.ifBlank { null }, dueAt.ifBlank { null }) }, enabled = title.isNotBlank()) { Text("Сохранить") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}
