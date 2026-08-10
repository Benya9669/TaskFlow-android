package ru.taskflow.app.ui.kanban

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ru.taskflow.app.data.local.KanbanColumnEntity
import ru.taskflow.app.data.local.ProjectEntity
import ru.taskflow.app.data.local.TaskEntity
import ru.taskflow.app.ui.components.EmptyState
import ru.taskflow.app.ui.theme.TaskFlowSpace

@Composable
fun KanbanBoardContent(
    columns: List<KanbanColumnEntity>,
    tasks: List<TaskEntity>,
    projects: List<ProjectEntity>,
    onMove: (TaskEntity, KanbanColumnEntity) -> Unit,
) {
    var projectFilter by rememberSaveable { mutableStateOf<String?>(null) }
    val visibleTasks = tasks.filter { projectFilter == null || it.projectId == projectFilter }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(TaskFlowSpace.md)) {
        Text("Доска", style = MaterialTheme.typography.headlineMedium)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(TaskFlowSpace.xs)) {
            FilterChip(selected = projectFilter == null, onClick = { projectFilter = null }, label = { Text("Все проекты") })
            projects.forEach { project ->
                FilterChip(selected = projectFilter == project.id, onClick = { projectFilter = project.id }, label = { Text(project.name) })
            }
        }
        if (columns.isEmpty()) {
            EmptyState("Колонки ещё не синхронизированы", "Подключитесь к серверу и обновите данные.")
        } else {
            Row(
                modifier = Modifier.fillMaxSize().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(TaskFlowSpace.md),
            ) {
                columns.forEach { column ->
                    KanbanColumn(column, visibleTasks.filter { it.columnId == column.id }, columns, onMove)
                }
            }
        }
    }
}

@Composable
private fun KanbanColumn(
    column: KanbanColumnEntity,
    tasks: List<TaskEntity>,
    columns: List<KanbanColumnEntity>,
    onMove: (TaskEntity, KanbanColumnEntity) -> Unit,
) {
    ElevatedCard(Modifier.width(300.dp).fillMaxHeight()) {
        Column(Modifier.fillMaxSize().padding(TaskFlowSpace.md), verticalArrangement = Arrangement.spacedBy(TaskFlowSpace.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TaskFlowSpace.sm)) {
                Surface(color = parseColor(column.color), shape = MaterialTheme.shapes.extraSmall) { Text(" ", modifier = Modifier.padding(horizontal = TaskFlowSpace.xs)) }
                Text(column.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(tasks.size.toString(), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (tasks.isEmpty()) Text("Задач нет", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            else LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(TaskFlowSpace.sm)) {
                items(tasks, key = TaskEntity::id) { task -> KanbanTaskCard(task, columns.filter { it.id != column.id }, onMove) }
            }
        }
    }
}

@Composable
private fun KanbanTaskCard(task: TaskEntity, destinations: List<KanbanColumnEntity>, onMove: (TaskEntity, KanbanColumnEntity) -> Unit) {
    var menuOpen by rememberSaveable(task.id) { mutableStateOf(false) }
    ElevatedCard(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(TaskFlowSpace.sm), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(TaskFlowSpace.xs)) {
                Text(task.title, style = MaterialTheme.typography.titleSmall)
                Text(priorityLabel(task.priority), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                task.dueAt?.let { Text("Срок: ${it.take(10)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            androidx.compose.foundation.layout.Box {
                IconButton(onClick = { menuOpen = true }) { Icon(Icons.Outlined.MoreVert, "Переместить задачу") }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    destinations.forEach { destination ->
                        DropdownMenuItem(
                            modifier = Modifier.semantics { contentDescription = "Переместить в ${destination.name}" },
                            text = { Text(destination.name) },
                            leadingIcon = { Icon(Icons.AutoMirrored.Outlined.ArrowForward, null) },
                            onClick = { menuOpen = false; onMove(task, destination) },
                        )
                    }
                }
            }
        }
    }
}

private fun parseColor(value: String) = runCatching { Color(android.graphics.Color.parseColor(value)) }.getOrDefault(Color.Gray)
private fun priorityLabel(priority: String) = when (priority) { "low" -> "Низкий"; "high" -> "Высокий"; "urgent" -> "Срочный"; else -> "Обычный" }
