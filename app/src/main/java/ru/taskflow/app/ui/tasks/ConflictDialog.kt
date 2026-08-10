package ru.taskflow.app.ui.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import ru.taskflow.app.data.local.TaskConflictEntity
import ru.taskflow.app.data.local.ProjectConflictEntity
import ru.taskflow.app.ui.theme.TaskFlowSpace

@Composable
fun ConflictDialog(conflict: TaskConflictEntity, onKeepServer: () -> Unit, onKeepLocal: () -> Unit) {
    val local = localBodyAdapter.fromJson(conflict.localBodyJson).orEmpty()
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Конфликт изменений") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(TaskFlowSpace.md)) {
                Text("Задача была изменена на сервере. Выберите версию, которую нужно сохранить.")
                ConflictVersion("Сервер", conflict.serverTitle, conflict.serverPriority)
                ConflictVersion(
                    "Локально",
                    local["title"] as? String ?: conflict.serverTitle,
                    local["priority"] as? String ?: conflict.serverPriority,
                )
            }
        },
        confirmButton = { Button(onClick = onKeepLocal) { Text("Оставить локальную") } },
        dismissButton = { TextButton(onClick = onKeepServer) { Text("Принять серверную") } },
    )
}

@Composable
fun ProjectConflictDialog(conflict: ProjectConflictEntity, onKeepServer: () -> Unit, onKeepLocal: () -> Unit) {
    val local = localBodyAdapter.fromJson(conflict.localBodyJson).orEmpty()
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Конфликт проекта") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(TaskFlowSpace.md)) {
                Text("Проект был изменён на другом устройстве. Выберите версию, которую нужно сохранить.")
                ConflictVersion("Сервер", conflict.serverName, conflict.serverColor)
                ConflictVersion("Локально", local["name"] as? String ?: conflict.serverName, local["color"] as? String ?: conflict.serverColor)
            }
        },
        confirmButton = { Button(onClick = onKeepLocal) { Text("Оставить локальную") } },
        dismissButton = { TextButton(onClick = onKeepServer) { Text("Принять серверную") } },
    )
}

@Composable
private fun ConflictVersion(label: String, title: String, priority: String) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(TaskFlowSpace.sm),
            verticalArrangement = Arrangement.spacedBy(TaskFlowSpace.xs),
        ) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(priority, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private val localBodyAdapter = Moshi.Builder().build().adapter<Map<String, Any?>>(
    Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java),
)
