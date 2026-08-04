package ru.taskflow.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
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
                TaskListContent(emptyList(), emptyList(), "Входящие пусты", "", { createdTitle = it }, {}, {}, { _, _, _, _, _, _, _ -> })
            }
        }
        compose.onNodeWithText("Новая задача").performClick().performTextInput("Работать офлайн")
        compose.onNodeWithText("Добавить во входящие").performClick()
        compose.runOnIdle { assertEquals("Работать офлайн", createdTitle) }
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
}
