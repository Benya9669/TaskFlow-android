package ru.taskflow.app.ui.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import ru.taskflow.app.data.local.TaskEntity
import ru.taskflow.app.ui.components.EmptyState
import ru.taskflow.app.ui.theme.TaskFlowSpace

@Composable
fun TaskListContent(tasks: List<TaskEntity>, emptyTitle: String, emptyDescription: String, onCreate: ((String) -> Unit)? = null) {
    var title by remember { mutableStateOf("") }
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
                        if (task.description.isNotBlank()) Text(task.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(task.priority.replaceFirstChar(Char::uppercase), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
    }
}
