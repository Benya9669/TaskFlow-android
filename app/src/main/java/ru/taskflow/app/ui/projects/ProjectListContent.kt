package ru.taskflow.app.ui.projects

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import ru.taskflow.app.data.local.ProjectEntity
import ru.taskflow.app.data.local.TaskEntity
import ru.taskflow.app.ui.components.EmptyState
import ru.taskflow.app.ui.theme.TaskFlowSpace

@Composable
fun ProjectListContent(projects: List<ProjectEntity>, tasks: List<TaskEntity>) {
    if (projects.isEmpty()) { EmptyState("Проектов пока нет", "Проекты появятся после синхронизации с сервером."); return }
    LazyColumn {
        items(projects, key = ProjectEntity::id) { project ->
            Card(Modifier.fillMaxWidth().padding(vertical = TaskFlowSpace.xs)) {
                Column(Modifier.padding(TaskFlowSpace.md)) {
                    Text(project.name, style = MaterialTheme.typography.titleMedium)
                    Text("${tasks.count { it.projectId == project.id }} задач", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
