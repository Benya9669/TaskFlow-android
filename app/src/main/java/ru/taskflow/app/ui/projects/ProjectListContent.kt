@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package ru.taskflow.app.ui.projects

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Workspaces
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ru.taskflow.app.data.local.ProjectEntity
import ru.taskflow.app.data.local.TaskEntity
import ru.taskflow.app.ui.components.EmptyState
import ru.taskflow.app.ui.theme.TaskFlowSpace

@Composable
fun ProjectListContent(
    projects: List<ProjectEntity>,
    archivedProjects: List<ProjectEntity>,
    tasks: List<TaskEntity>,
    busy: Boolean,
    onOpenTasks: (String) -> Unit,
    onCreate: (String, String) -> Unit,
    onUpdate: (ProjectEntity, String, String) -> Unit,
    onArchive: (ProjectEntity) -> Unit,
    onRestore: (ProjectEntity) -> Unit,
) {
    var archivedTab by rememberSaveable { mutableStateOf(false) }
    var editorProject by rememberSaveable { mutableStateOf<String?>(null) }
    var creating by rememberSaveable { mutableStateOf(false) }
    val visible = if (archivedTab) archivedProjects else projects

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(TaskFlowSpace.md)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Проекты", style = MaterialTheme.typography.headlineMedium)
                Text("${projects.size} активных · ${archivedProjects.size} в архиве", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            FilledTonalButton(onClick = { creating = true }, enabled = !busy) {
                Icon(Icons.Outlined.Add, contentDescription = null)
                Text("Новый", modifier = Modifier.padding(start = TaskFlowSpace.xs))
            }
        }
        PrimaryTabRow(selectedTabIndex = if (archivedTab) 1 else 0) {
            Tab(selected = !archivedTab, onClick = { archivedTab = false }, text = { Text("Активные") })
            Tab(selected = archivedTab, onClick = { archivedTab = true }, text = { Text("Архив") })
        }
        if (visible.isEmpty()) {
            EmptyState(
                if (archivedTab) "Архив пуст" else "Проектов пока нет",
                if (archivedTab) "Архивированные проекты появятся здесь." else "Создайте проект и распределите задачи по контекстам.",
                if (archivedTab) null else "Создать проект",
                if (archivedTab) null else ({ creating = true }),
            )
        } else {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(TaskFlowSpace.sm)) {
                items(visible, key = ProjectEntity::id) { project ->
                    ProjectCard(
                        project = project,
                        taskCount = tasks.count { it.projectId == project.id },
                        archived = archivedTab,
                        busy = busy,
                        onOpen = { onOpenTasks(project.id) },
                        onEdit = { editorProject = project.id },
                        onRestore = { onRestore(project) },
                    )
                }
            }
        }
    }

    if (creating) {
        ProjectEditorDialog(
            project = null,
            busy = busy,
            onDismiss = { creating = false },
            onSave = { name, color -> onCreate(name, color); creating = false },
        )
    }
    editorProject?.let { id ->
        (projects + archivedProjects).firstOrNull { it.id == id }?.let { project ->
            ProjectEditorDialog(
                project = project,
                busy = busy,
                onDismiss = { editorProject = null },
                onSave = { name, color -> onUpdate(project, name, color); editorProject = null },
                onArchive = if (project.archivedAt == null) ({ onArchive(project); editorProject = null }) else null,
            )
        }
    }
}

@Composable
private fun ProjectCard(project: ProjectEntity, taskCount: Int, archived: Boolean, busy: Boolean, onOpen: () -> Unit, onEdit: () -> Unit, onRestore: () -> Unit) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onOpen).padding(TaskFlowSpace.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(TaskFlowSpace.md),
        ) {
            Box(
                Modifier.size(TaskFlowSpace.lg).background(projectColor(project.color), CircleShape)
                    .semantics { contentDescription = "Цвет проекта ${project.color}" },
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(TaskFlowSpace.xs)) {
                Text(project.name, style = MaterialTheme.typography.titleMedium)
                Text("$taskCount ${taskWord(taskCount)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (archived) {
                IconButton(onClick = onRestore, enabled = !busy) { Icon(Icons.Outlined.Restore, "Восстановить проект") }
            } else {
                IconButton(onClick = onEdit, enabled = !busy) { Icon(Icons.Outlined.Edit, "Редактировать проект") }
            }
        }
    }
}

@Composable
private fun ProjectEditorDialog(project: ProjectEntity?, busy: Boolean, onDismiss: () -> Unit, onSave: (String, String) -> Unit, onArchive: (() -> Unit)? = null) {
    var name by rememberSaveable(project?.id) { mutableStateOf(project?.name.orEmpty()) }
    var color by rememberSaveable(project?.id) { mutableStateOf(project?.color ?: COLORS.first()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Workspaces, contentDescription = null) },
        title = { Text(if (project == null) "Новый проект" else "Настроить проект") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(TaskFlowSpace.md)) {
                OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Название") }, singleLine = true)
                Text("Цвет", style = MaterialTheme.typography.labelLarge)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    COLORS.forEach { value ->
                        val selected = color.equals(value, ignoreCase = true)
                        Box(
                            Modifier.size(if (selected) 42.dp else 36.dp)
                                .background(projectColor(value), CircleShape)
                                .clickable(role = Role.RadioButton) { color = value }
                                .semantics { contentDescription = "Цвет $value${if (selected) ", выбран" else ""}" },
                        )
                    }
                }
                if (onArchive != null) {
                    TextButton(onClick = onArchive, enabled = !busy) {
                        Icon(Icons.Outlined.Archive, contentDescription = null)
                        Text("В архив", modifier = Modifier.padding(start = TaskFlowSpace.xs))
                    }
                }
            }
        },
        confirmButton = { Button(onClick = { onSave(name.trim(), color) }, enabled = name.isNotBlank() && !busy) { Text("Сохранить") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

private fun projectColor(value: String): Color = runCatching { Color(android.graphics.Color.parseColor(value)) }.getOrDefault(Color(0xFF6D5DFC))
private val COLORS = listOf("#6d5dfc", "#3b82f6", "#06a5a5", "#22a447", "#e8a126", "#df4f68")
private fun taskWord(count: Int) = when {
    count % 10 == 1 && count % 100 != 11 -> "задача"
    count % 10 in 2..4 && count % 100 !in 12..14 -> "задачи"
    else -> "задач"
}
