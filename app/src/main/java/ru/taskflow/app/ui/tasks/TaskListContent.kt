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
import ru.taskflow.app.ui.components.EmptyState
import ru.taskflow.app.ui.theme.TaskFlowSpace

@Composable
fun TaskListContent(tasks: List<TaskEntity>, emptyTitle: String, emptyDescription: String, onCreate: ((String) -> Unit)? = null, onComplete: (String) -> Unit, onDelete: (String) -> Unit, onUpdate: (String, String, String) -> Unit) {
    var title by remember { mutableStateOf("") }
    var editingTask by remember { mutableStateOf<TaskEntity?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(TaskFlowSpace.sm)) {
        if (onCreate != null) {
            OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), label = { Text("Новая задача") }, singleLine = true)
            Button(onClick = { onCreate(title); title = "" }, enabled = title.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Добавить во входящие") }
        }
    if (tasks.isEmpty()) {
        EmptyState(emptyTitle, emptyDescription)
    } else {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(TaskFlowSpace.sm)) {
            items(tasks, key = TaskEntity::id) { task ->
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
        editingTask?.let { task -> TaskEditorDialog(task, onDismiss = { editingTask = null }) { taskTitle, priority -> onUpdate(task.id, taskTitle, priority); editingTask = null } }
    }
}

@Composable
private fun TaskEditorDialog(task: TaskEntity, onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var title by remember(task.id) { mutableStateOf(task.title) }
    var priority by remember(task.id) { mutableStateOf(task.priority) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Редактировать задачу") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(TaskFlowSpace.sm)) {
                OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), label = { Text("Название") }, singleLine = true)
                Text("Приоритет", style = MaterialTheme.typography.labelLarge)
                androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(TaskFlowSpace.xs)) {
                    listOf("low", "normal", "high", "urgent").forEach { value ->
                        TextButton(onClick = { priority = value }) { Text(if (priority == value) "[$value]" else value) }
                    }
                }
            }
        },
        confirmButton = { Button(onClick = { onSave(title, priority) }, enabled = title.isNotBlank()) { Text("Сохранить") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}
