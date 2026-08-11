package ru.taskflow.app.ui.kanban

import android.content.ClipData
import android.content.ClipDescription
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.draganddrop.dragAndDropSource
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DragIndicator
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.DragAndDropTransferData
import androidx.compose.ui.draganddrop.mimeTypes
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.taskflow.app.data.local.KanbanColumnEntity
import ru.taskflow.app.data.local.ProjectEntity
import ru.taskflow.app.data.local.TaskEntity
import ru.taskflow.app.ui.components.EmptyState
import ru.taskflow.app.ui.theme.TaskFlowSpace
import java.time.LocalDate

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun KanbanBoardContent(
    columns: List<KanbanColumnEntity>,
    tasks: List<TaskEntity>,
    projects: List<ProjectEntity>,
    busy: Boolean,
    syncing: Boolean,
    syncError: String?,
    onDismissError: () -> Unit,
    onRefresh: () -> Unit,
    onMove: (String, KanbanColumnEntity, String?) -> Unit,
    onCreateTask: (KanbanColumnEntity, String, String?) -> Unit,
    onCreateColumn: (String, String, String) -> Unit,
    onUpdateColumn: (KanbanColumnEntity, String, String, String) -> Unit,
    onReorderColumns: (List<KanbanColumnEntity>) -> Unit,
    onDeleteColumn: (KanbanColumnEntity, KanbanColumnEntity) -> Unit,
) {
    var projectFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var quickAddColumn by remember { mutableStateOf<KanbanColumnEntity?>(null) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    val boardScrollState = rememberScrollState()
    val edgeThresholdPx = with(LocalDensity.current) { 72.dp.toPx() }
    val autoScrollStepPx = with(LocalDensity.current) { 12.dp.toPx() }
    var boardViewportStart by remember { mutableFloatStateOf(0f) }
    var boardViewportEnd by remember { mutableFloatStateOf(0f) }
    var autoScrollDirection by remember { mutableIntStateOf(0) }
    val updateAutoScroll: (DragAndDropEvent) -> Unit = remember(boardScrollState, edgeThresholdPx) {
        { event ->
            autoScrollDirection = kanbanAutoScrollDirection(
                pointerX = event.toAndroidDragEvent().x,
                viewportStart = boardViewportStart,
                viewportEnd = boardViewportEnd,
                edgeThreshold = edgeThresholdPx,
                canScrollBackward = boardScrollState.value > 0,
                canScrollForward = boardScrollState.value < boardScrollState.maxValue,
            )
        }
    }
    val stopAutoScroll: () -> Unit = remember { { autoScrollDirection = 0 } }
    val boardDragTarget = remember(boardScrollState, edgeThresholdPx) {
        object : DragAndDropTarget {
            override fun onStarted(event: DragAndDropEvent) = updateAutoScroll(event)
            override fun onMoved(event: DragAndDropEvent) = updateAutoScroll(event)
            override fun onExited(event: DragAndDropEvent) = stopAutoScroll()
            override fun onEnded(event: DragAndDropEvent) = stopAutoScroll()
            override fun onDrop(event: DragAndDropEvent): Boolean {
                stopAutoScroll()
                return false
            }
        }
    }
    LaunchedEffect(autoScrollDirection, boardScrollState) {
        while (autoScrollDirection != 0) {
            withFrameNanos { }
            val consumed = boardScrollState.scrollBy(autoScrollStepPx * autoScrollDirection)
            if (consumed == 0f) autoScrollDirection = 0
        }
    }
    val visibleTasks = tasks.filter { projectFilter == null || it.projectId == projectFilter }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(TaskFlowSpace.md)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Доска", style = MaterialTheme.typography.headlineMedium)
                Text("Удерживайте карточку и перетащите её в нужную позицию", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (syncing) CircularProgressIndicator(Modifier.width(24.dp), strokeWidth = 2.dp)
            IconButton(onClick = onRefresh, enabled = !syncing) { Icon(Icons.Outlined.Refresh, "Синхронизировать доску") }
            IconButton(onClick = { settingsOpen = true }, enabled = !busy) { Icon(Icons.Outlined.Settings, "Настроить колонки") }
        }
        if (syncError != null) {
            Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.medium) {
                Row(Modifier.fillMaxWidth().padding(TaskFlowSpace.sm), verticalAlignment = Alignment.CenterVertically) {
                    Text(syncError, Modifier.weight(1f), color = MaterialTheme.colorScheme.onErrorContainer)
                    TextButton(onClick = onDismissError) { Text("Закрыть") }
                }
            }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(TaskFlowSpace.xs)) {
            FilterChip(selected = projectFilter == null, onClick = { projectFilter = null }, label = { Text("Все проекты") })
            projects.forEach { project ->
                FilterChip(selected = projectFilter == project.id, onClick = { projectFilter = project.id }, label = { Text(project.name) })
            }
        }
        if (columns.isEmpty()) {
            EmptyState("Колонки ещё не синхронизированы", "Подключитесь к серверу и обновите данные.")
            OutlinedButton(onClick = onRefresh, enabled = !syncing, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Повторить") }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .onGloballyPositioned {
                        val bounds = it.boundsInRoot()
                        boardViewportStart = bounds.left
                        boardViewportEnd = bounds.right
                    }
                    .dragAndDropTarget(
                        shouldStartDragAndDrop = { it.mimeTypes().contains(ClipDescription.MIMETYPE_TEXT_PLAIN) },
                        target = boardDragTarget,
                    )
                    .horizontalScroll(boardScrollState),
                horizontalArrangement = Arrangement.spacedBy(TaskFlowSpace.md),
            ) {
                columns.sortedBy(KanbanColumnEntity::position).forEach { column ->
                    KanbanColumn(
                        column = column,
                        tasks = visibleTasks.filter { it.columnId == column.id }.sortedBy(TaskEntity::kanbanPosition),
                        columns = columns,
                        onAdd = { quickAddColumn = column },
                        onMove = onMove,
                        onDragMoved = updateAutoScroll,
                        onDragEnded = stopAutoScroll,
                    )
                }
            }
        }
    }
    quickAddColumn?.let { column ->
        QuickAddDialog(column, projects.firstOrNull { it.id == projectFilter }, onDismiss = { quickAddColumn = null }) { title ->
            onCreateTask(column, title, projectFilter)
            quickAddColumn = null
        }
    }
    if (settingsOpen) {
        ColumnSettingsDialog(columns.sortedBy(KanbanColumnEntity::position), busy, onDismiss = { settingsOpen = false }, onCreateColumn, onUpdateColumn, onReorderColumns, onDeleteColumn)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun KanbanColumn(
    column: KanbanColumnEntity,
    tasks: List<TaskEntity>,
    columns: List<KanbanColumnEntity>,
    onAdd: () -> Unit,
    onMove: (String, KanbanColumnEntity, String?) -> Unit,
    onDragMoved: (DragAndDropEvent) -> Unit,
    onDragEnded: () -> Unit,
) {
    var dropActive by remember(column.id) { mutableStateOf(false) }
    val target = remember(column.id, onMove, onDragMoved, onDragEnded) {
        object : DragAndDropTarget {
            override fun onEntered(event: DragAndDropEvent) { dropActive = true; onDragMoved(event) }
            override fun onMoved(event: DragAndDropEvent) = onDragMoved(event)
            override fun onExited(event: DragAndDropEvent) { dropActive = false; onDragEnded() }
            override fun onEnded(event: DragAndDropEvent) { dropActive = false; onDragEnded() }
            override fun onDrop(event: DragAndDropEvent): Boolean {
                dropActive = false
                onDragEnded()
                val taskId = event.taskId() ?: return false
                onMove(taskId, column, null)
                return true
            }
        }
    }
    ElevatedCard(
        Modifier
            .width(310.dp)
            .fillMaxHeight()
            .heightIn(min = 420.dp)
            .dragAndDropTarget({ it.mimeTypes().contains(ClipDescription.MIMETYPE_TEXT_PLAIN) }, target)
            .then(if (dropActive) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.medium) else Modifier),
    ) {
        Column(Modifier.fillMaxSize().padding(TaskFlowSpace.md), verticalArrangement = Arrangement.spacedBy(TaskFlowSpace.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TaskFlowSpace.sm)) {
                Surface(color = parseColor(column.color), shape = MaterialTheme.shapes.extraSmall) { Text(" ", modifier = Modifier.padding(horizontal = TaskFlowSpace.xs)) }
                Column(Modifier.weight(1f)) {
                    Text(column.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(statusLabel(column.semanticStatus), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(tasks.size.toString(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                IconButton(onClick = onAdd) { Icon(Icons.Outlined.Add, "Добавить задачу в ${column.name}") }
            }
            if (tasks.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(if (dropActive) "Отпустите карточку здесь" else "Задач нет", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(TaskFlowSpace.sm)) {
                    items(tasks, key = TaskEntity::id) { task ->
                        KanbanTaskCard(task, column, columns.filter { it.id != column.id }, onMove, onDragMoved, onDragEnded)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun KanbanTaskCard(
    task: TaskEntity,
    column: KanbanColumnEntity,
    destinations: List<KanbanColumnEntity>,
    onMove: (String, KanbanColumnEntity, String?) -> Unit,
    onDragMoved: (DragAndDropEvent) -> Unit,
    onDragEnded: () -> Unit,
) {
    var menuOpen by rememberSaveable(task.id) { mutableStateOf(false) }
    var dropActive by remember(task.id) { mutableStateOf(false) }
    val target = remember(task.id, column.id, onMove, onDragMoved, onDragEnded) {
        object : DragAndDropTarget {
            override fun onEntered(event: DragAndDropEvent) { if (event.taskId() != task.id) dropActive = true; onDragMoved(event) }
            override fun onMoved(event: DragAndDropEvent) = onDragMoved(event)
            override fun onExited(event: DragAndDropEvent) { dropActive = false; onDragEnded() }
            override fun onEnded(event: DragAndDropEvent) { dropActive = false; onDragEnded() }
            override fun onDrop(event: DragAndDropEvent): Boolean {
                dropActive = false
                onDragEnded()
                val draggedId = event.taskId() ?: return false
                if (draggedId == task.id) return false
                onMove(draggedId, column, task.id)
                return true
            }
        }
    }
    val overdue = task.dueAt?.take(10)?.let { runCatching { LocalDate.parse(it).isBefore(LocalDate.now()) }.getOrDefault(false) } == true && task.status != "done"
    ElevatedCard(
        Modifier
            .fillMaxWidth()
            .dragAndDropTarget({ it.mimeTypes().contains(ClipDescription.MIMETYPE_TEXT_PLAIN) }, target)
            .dragAndDropSource {
                detectTapGestures(onLongPress = {
                    startTransfer(DragAndDropTransferData(ClipData.newPlainText(DRAG_LABEL, task.id)))
                })
            }
            .then(if (dropActive) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.medium) else Modifier)
            .semantics { contentDescription = "${task.title}. Удерживайте для перемещения" },
    ) {
        Row(Modifier.fillMaxWidth().padding(TaskFlowSpace.sm), verticalAlignment = Alignment.Top) {
            Icon(Icons.Outlined.DragIndicator, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
            Column(Modifier.weight(1f).padding(horizontal = TaskFlowSpace.xs), verticalArrangement = Arrangement.spacedBy(TaskFlowSpace.xs)) {
                Text(task.title, style = MaterialTheme.typography.titleSmall)
                task.description.takeIf(String::isNotBlank)?.let { Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Row(horizontalArrangement = Arrangement.spacedBy(TaskFlowSpace.sm)) {
                    Text(priorityLabel(task.priority), style = MaterialTheme.typography.labelMedium, color = priorityColor(task.priority))
                    task.dueAt?.let { Text("Срок ${it.take(10)}", style = MaterialTheme.typography.labelMedium, color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                if (task.tags.isNotEmpty()) Text(task.tags.take(3).joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            Box {
                IconButton(onClick = { menuOpen = true }) { Icon(Icons.Outlined.MoreVert, "Переместить задачу") }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    destinations.forEach { destination ->
                        DropdownMenuItem(
                            modifier = Modifier.semantics { contentDescription = "Переместить в ${destination.name}" },
                            text = { Text(destination.name) },
                            leadingIcon = { Icon(Icons.AutoMirrored.Outlined.ArrowForward, null) },
                            onClick = { menuOpen = false; onMove(task.id, destination, null) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickAddDialog(column: KanbanColumnEntity, project: ProjectEntity?, onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var title by rememberSaveable(column.id) { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Новая задача · ${column.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(TaskFlowSpace.sm)) {
                project?.let { Text("Проект: ${it.name}", color = MaterialTheme.colorScheme.primary) }
                OutlinedTextField(title, { title = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Название") }, singleLine = true)
            }
        },
        confirmButton = { Button(onClick = { onCreate(title.trim()) }, enabled = title.isNotBlank()) { Text("Добавить") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

@Composable
private fun ColumnSettingsDialog(
    columns: List<KanbanColumnEntity>,
    busy: Boolean,
    onDismiss: () -> Unit,
    onCreate: (String, String, String) -> Unit,
    onUpdate: (KanbanColumnEntity, String, String, String) -> Unit,
    onReorder: (List<KanbanColumnEntity>) -> Unit,
    onDelete: (KanbanColumnEntity, KanbanColumnEntity) -> Unit,
) {
    var editing by remember { mutableStateOf<KanbanColumnEntity?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<KanbanColumnEntity?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Колонки Kanban") },
        text = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(TaskFlowSpace.xs)) {
                columns.forEachIndexed { index, column ->
                    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceVariant) {
                        Row(Modifier.fillMaxWidth().padding(TaskFlowSpace.xs), verticalAlignment = Alignment.CenterVertically) {
                            Surface(color = parseColor(column.color), shape = MaterialTheme.shapes.extraSmall) { Text(" ", modifier = Modifier.padding(horizontal = 4.dp)) }
                            Column(Modifier.weight(1f).padding(horizontal = TaskFlowSpace.sm)) {
                                Text(column.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(statusLabel(column.semanticStatus), style = MaterialTheme.typography.labelSmall)
                            }
                            IconButton(onClick = { onReorder(columns.toMutableList().apply { add(index - 1, removeAt(index)) }) }, enabled = !busy && index > 0) { Icon(Icons.Outlined.ArrowUpward, "Поднять ${column.name}") }
                            IconButton(onClick = { onReorder(columns.toMutableList().apply { add(index + 1, removeAt(index)) }) }, enabled = !busy && index < columns.lastIndex) { Icon(Icons.Outlined.ArrowDownward, "Опустить ${column.name}") }
                            IconButton(onClick = { editing = column }, enabled = !busy) { Icon(Icons.Outlined.Edit, "Изменить ${column.name}") }
                            IconButton(onClick = { deleting = column }, enabled = !busy && columns.size > 1) { Icon(Icons.Outlined.DeleteOutline, "Удалить ${column.name}") }
                        }
                    }
                }
                OutlinedButton(onClick = { creating = true }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.Add, null); Text("Добавить колонку") }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Готово") } },
    )
    if (creating || editing != null) {
        ColumnEditorDialog(editing, onDismiss = { creating = false; editing = null }) { name, color, status ->
            editing?.let { onUpdate(it, name, color, status) } ?: onCreate(name, color, status)
            creating = false
            editing = null
        }
    }
    deleting?.let { column ->
        DeleteColumnDialog(column, columns.filter { it.id != column.id }, onDismiss = { deleting = null }) { destination ->
            onDelete(column, destination)
            deleting = null
        }
    }
}

@Composable
private fun ColumnEditorDialog(column: KanbanColumnEntity?, onDismiss: () -> Unit, onSave: (String, String, String) -> Unit) {
    var name by rememberSaveable(column?.id) { mutableStateOf(column?.name.orEmpty()) }
    var color by rememberSaveable(column?.id) { mutableStateOf(column?.color ?: COLORS.first()) }
    var status by rememberSaveable(column?.id) { mutableStateOf(column?.semanticStatus ?: "todo") }
    var statusMenu by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (column == null) "Новая колонка" else "Изменить колонку") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(TaskFlowSpace.sm)) {
                OutlinedTextField(name, { name = it }, label = { Text("Название") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(color, { color = it }, label = { Text("Цвет #RRGGBB") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(TaskFlowSpace.xs)) {
                    COLORS.forEach { preset -> FilterChip(selected = color.equals(preset, true), onClick = { color = preset }, label = { Surface(color = parseColor(preset), modifier = Modifier.width(20.dp)) { Text(" ") } }) }
                }
                Box {
                    OutlinedButton(onClick = { statusMenu = true }, modifier = Modifier.fillMaxWidth()) { Text("Статус: ${statusLabel(status)}") }
                    DropdownMenu(statusMenu, { statusMenu = false }) {
                        STATUSES.forEach { value -> DropdownMenuItem(text = { Text(statusLabel(value)) }, onClick = { status = value; statusMenu = false }) }
                    }
                }
            }
        },
        confirmButton = { Button(onClick = { onSave(name.trim(), color, status) }, enabled = name.isNotBlank() && COLOR.matches(color)) { Text("Сохранить") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

@Composable
private fun DeleteColumnDialog(column: KanbanColumnEntity, destinations: List<KanbanColumnEntity>, onDismiss: () -> Unit, onDelete: (KanbanColumnEntity) -> Unit) {
    var destination by remember { mutableStateOf(destinations.first()) }
    var menuOpen by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Удалить «${column.name}»?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(TaskFlowSpace.sm)) {
                Text("Все задачи будут атомарно перенесены в выбранную колонку.")
                Box {
                    OutlinedButton(onClick = { menuOpen = true }, modifier = Modifier.fillMaxWidth()) { Text("Перенести в: ${destination.name}") }
                    DropdownMenu(menuOpen, { menuOpen = false }) {
                        destinations.forEach { item -> DropdownMenuItem(text = { Text(item.name) }, onClick = { destination = item; menuOpen = false }) }
                    }
                }
            }
        },
        confirmButton = { Button(onClick = { onDelete(destination) }) { Text("Удалить и перенести") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

private fun DragAndDropEvent.taskId(): String? = toAndroidDragEvent().clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()

internal fun kanbanAutoScrollDirection(
    pointerX: Float,
    viewportStart: Float,
    viewportEnd: Float,
    edgeThreshold: Float,
    canScrollBackward: Boolean,
    canScrollForward: Boolean,
): Int = when {
    viewportEnd <= viewportStart -> 0
    pointerX <= viewportStart + edgeThreshold && canScrollBackward -> -1
    pointerX >= viewportEnd - edgeThreshold && canScrollForward -> 1
    else -> 0
}
private fun parseColor(value: String) = runCatching { Color(android.graphics.Color.parseColor(value)) }.getOrDefault(Color.Gray)
private fun priorityColor(priority: String) = when (priority) { "urgent" -> Color(0xFFB3261E); "high" -> Color(0xFFB85C00); "low" -> Color(0xFF2E7D32); else -> Color(0xFF6D5DFC) }
private fun priorityLabel(priority: String) = when (priority) { "low" -> "Низкий"; "high" -> "Высокий"; "urgent" -> "Срочный"; else -> "Обычный" }
private fun statusLabel(status: String) = when (status) { "inbox" -> "Входящие"; "in_progress" -> "В работе"; "done" -> "Готово"; else -> "Запланировано" }

private const val DRAG_LABEL = "taskflow-kanban-task"
private val COLOR = Regex("^#[0-9a-fA-F]{6}$")
private val COLORS = listOf("#6D5DFC", "#2563EB", "#0F9D7A", "#D97706", "#DC2626", "#7C3AED")
private val STATUSES = listOf("inbox", "todo", "in_progress", "done")
