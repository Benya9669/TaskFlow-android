package ru.taskflow.app.ui.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import ru.taskflow.app.data.local.TaskConflictEntity
import ru.taskflow.app.ui.theme.TaskFlowSpace

@Composable
fun ConflictDialog(conflict: TaskConflictEntity, onKeepServer: () -> Unit, onKeepLocal: () -> Unit) {
    val local = localBodyAdapter.fromJson(conflict.localBodyJson).orEmpty()
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Конфликт изменений") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(TaskFlowSpace.sm)) {
                Text("Задача была изменена на сервере. Выберите версию, которую нужно сохранить.")
                Text("Сервер", style = MaterialTheme.typography.labelLarge)
                Text(conflict.serverTitle)
                Text(conflict.serverPriority, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Локально", style = MaterialTheme.typography.labelLarge)
                Text(local["title"] as? String ?: conflict.serverTitle)
                Text(local["priority"] as? String ?: conflict.serverPriority, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { Button(onClick = onKeepLocal) { Text("Оставить локальную") } },
        dismissButton = { TextButton(onClick = onKeepServer) { Text("Принять серверную") } },
    )
}

private val localBodyAdapter = Moshi.Builder().build().adapter<Map<String, Any?>>(Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java))
