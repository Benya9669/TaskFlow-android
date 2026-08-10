package ru.taskflow.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.taskflow.app.data.local.TaskConflictEntity
import ru.taskflow.app.data.local.TaskEntity
import ru.taskflow.app.ui.auth.LoginScreen
import ru.taskflow.app.ui.auth.SessionUiState
import ru.taskflow.app.ui.tasks.ConflictDialog
import ru.taskflow.app.ui.tasks.TaskListContent
import ru.taskflow.app.ui.projects.ProjectListContent
import ru.taskflow.app.ui.kanban.KanbanBoardContent
import ru.taskflow.app.data.local.KanbanColumnEntity

@RunWith(AndroidJUnit4::class)
class BetaFlowUiTest {
    @get:Rule val compose = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @Test fun loginFormSendsEnteredCredentials() {
        var submitted: List<String>? = null
        compose.setContent {
            MaterialTheme { LoginScreen(SessionUiState(isSignedIn = false), { server, email, password -> submitted = listOf(server, email, password) }, { _, _, _, _ -> }, {}) }
        }
        compose.onNodeWithText("URL сервера").performClick().performTextInput("https://taskflow.example.com")
        compose.onNodeWithText("Email").performClick().performTextInput("user@example.com")
        compose.onNodeWithText("Пароль").performClick().performTextInput("password")
        compose.onNodeWithText("Войти").performClick()
        compose.runOnIdle { assertEquals(listOf("https://taskflow.example.com", "user@example.com", "password"), submitted) }
    }

    @Test fun inboxCreateSendsOfflineTaskTitle() {
        var createdTitle: String? = null
        compose.setContent {
            MaterialTheme {
                TaskListContent(emptyList(), emptyList(), "Входящие пусты", "", { title, _, _ -> createdTitle = title }, {}, {}, { _, _ -> })
            }
        }
        compose.onNodeWithText("Новая задача").performClick()
        compose.onNodeWithText("Название").performClick().performTextInput("Работать офлайн")
        compose.onNodeWithText("Добавить").performClick()
        compose.runOnIdle { assertEquals("Работать офлайн", createdTitle) }
    }

    @Test fun taskListSeparatesCompletedTasksAndRefreshes() {
        var refreshes = 0
        val active = TaskEntity("active", "owner", null, "column", "Активная", "", "inbox", "normal", null, null, null, 0, null, emptyList(), emptyList(), "", "", 1, null)
        val completed = active.copy(id = "done", title = "Готовая", status = "done")
        compose.setContent {
            MaterialTheme {
                TaskListContent(listOf(active, completed), emptyList(), "", "", onComplete = {}, onDelete = {}, onUpdate = { _, _ -> }, onRefresh = { refreshes++ })
            }
        }
        compose.onNodeWithText("1 активная задача").assertIsDisplayed()
        compose.onNodeWithText("Завершено (1)").assertIsDisplayed()
        compose.onNodeWithContentDescription("Синхронизировать").performClick()
        compose.runOnIdle { assertEquals(1, refreshes) }
    }

    @Test fun taskEditorOpensDatePickerInsteadOfIsoInput() {
        val task = TaskEntity("task", "owner", null, "column", "Запланировать встречу", "", "inbox", "normal", null, null, null, 0, null, emptyList(), emptyList(), "", "", 1, null)
        compose.setContent {
            MaterialTheme {
                TaskListContent(listOf(task), emptyList(), "", "", onComplete = {}, onDelete = {}, onUpdate = { _, _ -> })
            }
        }
        compose.onNodeWithText("Запланировать встречу").performClick()
        compose.onNodeWithText("Запланировать").performClick()
        compose.onNodeWithText("Готово").assertIsDisplayed()
    }

    @Test fun conflictDialogKeepsLocalVersion() {
        var resolution: String? = null
        val conflict = TaskConflictEntity("mutation", "task", "{\"title\":\"Локальная\",\"priority\":\"high\"}", "Серверная", "normal", 1L)
        compose.setContent { MaterialTheme { ConflictDialog(conflict, { resolution = "server" }, { resolution = "local" }) } }
        compose.onNodeWithText("Конфликт изменений").assertIsDisplayed()
        compose.onNodeWithText("Оставить локальную").performClick()
        compose.runOnIdle { assertEquals("local", resolution) }
    }

    @Test fun conflictDialogAcceptsServerVersion() {
        var resolution: String? = null
        val conflict = TaskConflictEntity("mutation", "task", "{\"title\":\"Локальная\",\"priority\":\"high\"}", "Серверная", "normal", 1L)
        compose.setContent { MaterialTheme { ConflictDialog(conflict, { resolution = "server" }, { resolution = "local" }) } }
        compose.onNodeWithText("Принять серверную").performClick()
        compose.runOnIdle { assertEquals("server", resolution) }
    }

    @Test fun taskSearchFiltersLocalResults() {
        val alpha = TaskEntity("alpha", "owner", null, "column", "Alpha task", "", "inbox", "normal", null, null, null, 0, null, emptyList(), emptyList(), "", "2026-08-10T10:00:00Z", 1, null)
        val beta = alpha.copy(id = "beta", title = "Beta task")
        compose.setContent { MaterialTheme { TaskListContent(listOf(alpha, beta), emptyList(), "", "", onComplete = {}, onDelete = {}, onUpdate = { _, _ -> }) } }
        compose.onNodeWithContentDescription("Открыть поиск").performClick()
        compose.onNodeWithText("Поиск задач").performClick().performTextInput("Alpha")
        compose.onNodeWithText("Alpha task").assertIsDisplayed()
        compose.onAllNodesWithText("Beta task").assertCountEquals(0)
    }

    @Test fun taskDeleteRequiresConfirmation() {
        var deleted: String? = null
        val task = TaskEntity("delete-me", "owner", null, "column", "Удалить после подтверждения", "", "inbox", "normal", null, null, null, 0, null, emptyList(), emptyList(), "", "", 1, null)
        compose.setContent { MaterialTheme { TaskListContent(listOf(task), emptyList(), "", "", onComplete = {}, onDelete = { deleted = it }, onUpdate = { _, _ -> }) } }
        compose.onNodeWithContentDescription("Действия задачи").performClick()
        compose.onNodeWithText("Удалить").performClick()
        compose.runOnIdle { assertEquals(null, deleted) }
        compose.onNodeWithText("Удалить").performClick()
        compose.onNodeWithText("Задача удалена").assertIsDisplayed()
        compose.onNodeWithText("Отменить").performClick()
        compose.onNodeWithText("Удалить после подтверждения").assertIsDisplayed()
        compose.runOnIdle { assertEquals(null, deleted) }
    }

    @Test fun projectCreateDialogReturnsNameAndColor() {
        var created: Pair<String, String>? = null
        compose.setContent {
            MaterialTheme {
                ProjectListContent(emptyList(), emptyList(), emptyList(), false, {}, { name, color -> created = name to color }, { _, _, _ -> }, {}, {})
            }
        }
        compose.onNodeWithText("Создать проект").performClick()
        compose.onNodeWithText("Название").performClick().performTextInput("Мобильный проект")
        compose.onNodeWithText("Сохранить").performClick()
        compose.runOnIdle { assertEquals("Мобильный проект", created?.first) }
    }

    @Test fun kanbanMovesTaskToSelectedColumn() {
        val inbox = KanbanColumnEntity("inbox", "owner", "Входящие", "#2563EB", "inbox", 0, "", "", 1, null)
        val done = KanbanColumnEntity("done", "owner", "Завершено", "#16A34A", "done", 1, "", "", 1, null)
        val task = TaskEntity("task", "owner", null, "inbox", "Карточка канбан", "", "inbox", "normal", null, null, null, 0, null, emptyList(), emptyList(), "", "", 1, null)
        var movedTo: String? = null
        compose.setContent {
            MaterialTheme {
                KanbanBoardContent(
                    columns = listOf(inbox, done), tasks = listOf(task), projects = emptyList(), busy = false, syncing = false,
                    syncError = null, onDismissError = {}, onRefresh = {}, onMove = { _, column, _ -> movedTo = column.id },
                    onCreateTask = { _, _, _ -> }, onCreateColumn = { _, _, _ -> }, onUpdateColumn = { _, _, _, _ -> },
                    onReorderColumns = {}, onDeleteColumn = { _, _ -> },
                )
            }
        }
        compose.onNodeWithContentDescription("Переместить задачу").performClick()
        compose.onNodeWithContentDescription("Переместить в Завершено").performClick()
        compose.runOnIdle { assertEquals("done", movedTo) }
    }

    @Test fun kanbanQuickAddCreatesTaskInSelectedColumn() {
        val doing = KanbanColumnEntity("doing", "owner", "В работе", "#7C3AED", "doing", 0, "", "", 1, null)
        var created: Pair<String, String>? = null
        compose.setContent {
            MaterialTheme {
                KanbanBoardContent(
                    columns = listOf(doing), tasks = emptyList(), projects = emptyList(), busy = false, syncing = false,
                    syncError = null, onDismissError = {}, onRefresh = {}, onMove = { _, _, _ -> },
                    onCreateTask = { column, title, _ -> created = column.id to title }, onCreateColumn = { _, _, _ -> },
                    onUpdateColumn = { _, _, _, _ -> }, onReorderColumns = {}, onDeleteColumn = { _, _ -> },
                )
            }
        }
        compose.onNodeWithContentDescription("Добавить задачу в В работе").performClick()
        compose.onNodeWithText("Название").performClick().performTextInput("Новая карточка")
        compose.onNodeWithText("Добавить").performClick()
        compose.runOnIdle { assertEquals("doing" to "Новая карточка", created) }
    }

    @Test fun kanbanSettingsCreatesCustomColumn() {
        val inbox = KanbanColumnEntity("inbox", "owner", "Входящие", "#2563EB", "inbox", 0, "", "", 1, null)
        var createdName: String? = null
        compose.setContent {
            MaterialTheme {
                KanbanBoardContent(
                    columns = listOf(inbox), tasks = emptyList(), projects = emptyList(), busy = false, syncing = false,
                    syncError = null, onDismissError = {}, onRefresh = {}, onMove = { _, _, _ -> }, onCreateTask = { _, _, _ -> },
                    onCreateColumn = { name, _, _ -> createdName = name }, onUpdateColumn = { _, _, _, _ -> },
                    onReorderColumns = {}, onDeleteColumn = { _, _ -> },
                )
            }
        }
        compose.onNodeWithContentDescription("Настроить колонки").performClick()
        compose.onNodeWithText("Добавить колонку").performClick()
        compose.onNodeWithText("Название").performClick().performTextInput("Проверка")
        compose.onNodeWithText("Сохранить").performClick()
        compose.runOnIdle { assertEquals("Проверка", createdName) }
    }

    @Test fun loginRemainsUsableWithLargeFontScale() {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(density = 1f, fontScale = 2f)) {
                MaterialTheme { LoginScreen(SessionUiState(isSignedIn = false), { _, _, _ -> }, { _, _, _, _ -> }, {}) }
            }
        }
        compose.onNodeWithText("URL сервера").assertIsDisplayed()
    }
}
