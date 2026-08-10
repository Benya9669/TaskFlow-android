package ru.taskflow.app.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ru.taskflow.app.ui.components.TaskFlowMark
import ru.taskflow.app.ui.theme.Ink
import ru.taskflow.app.ui.theme.InkDark
import ru.taskflow.app.ui.theme.TaskFlowSpace

@Composable
fun LoginScreen(state: SessionUiState, onLogin: (String, String, String) -> Unit, onRegister: (String, String, String, String) -> Unit, onDismissVerification: () -> Unit) {
    var serverUrl by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var displayName by rememberSaveable { mutableStateOf("") }
    var registering by rememberSaveable { mutableStateOf(false) }

    val wideLayout = LocalConfiguration.current.screenWidthDp >= 720
    Row(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (wideLayout) {
            Column(
                modifier = Modifier.weight(1f).fillMaxSize().background(Ink).padding(48.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TaskFlowSpace.sm)) {
                    TaskFlowMark(size = 38.dp, ringColor = InkDark)
                    Text("TaskFlow", style = MaterialTheme.typography.titleLarge, color = Color.White)
                }
                Column(verticalArrangement = Arrangement.spacedBy(TaskFlowSpace.md)) {
                    Text("Спокойное место\nдля важных дел.", style = MaterialTheme.typography.displaySmall, color = Color.White)
                    Text("Задачи остаются доступны без сети и синхронизируются с вашим сервером.", style = MaterialTheme.typography.bodyLarge, color = InkDark)
                }
                Text("Ваши данные остаются на вашем TaskFlow-сервере.", style = MaterialTheme.typography.bodySmall, color = InkDark.copy(alpha = .72f))
            }
        }
        Box(
            modifier = Modifier.weight(1f).fillMaxSize().padding(TaskFlowSpace.lg),
            contentAlignment = Alignment.Center,
        ) {
            LoginForm(
                state = state,
                registering = registering,
                serverUrl = serverUrl,
                email = email,
                password = password,
                displayName = displayName,
                onServerUrlChange = { serverUrl = it },
                onEmailChange = { email = it },
                onPasswordChange = { password = it },
                onDisplayNameChange = { displayName = it },
                onSubmit = { if (registering) onRegister(serverUrl, email, password, displayName) else onLogin(serverUrl, email, password) },
                onDismissVerification = onDismissVerification,
                onToggleMode = { registering = !registering },
                showBrand = !wideLayout,
            )
        }
    }
}

@Composable
private fun LoginForm(
    state: SessionUiState,
    registering: Boolean,
    serverUrl: String,
    email: String,
    password: String,
    displayName: String,
    onServerUrlChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onDisplayNameChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onDismissVerification: () -> Unit,
    onToggleMode: () -> Unit,
    showBrand: Boolean,
) {
    Column(modifier = Modifier.fillMaxWidth().widthIn(max = 460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(TaskFlowSpace.lg)) {
        if (showBrand) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TaskFlowSpace.sm)) {
                TaskFlowMark()
                Column {
                    Text("TaskFlow", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
                    Text("Спокойное пространство для задач", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(modifier = Modifier.padding(TaskFlowSpace.md), verticalArrangement = Arrangement.spacedBy(TaskFlowSpace.md)) {
                Text(if (registering) "Создайте аккаунт" else "Войдите в аккаунт", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                Text(if (registering) "Подключитесь к своему self-hosted серверу." else "Введите данные вашего self-hosted сервера.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(serverUrl, onServerUrlChange, Modifier.fillMaxWidth(), label = { Text("URL сервера") }, placeholder = { Text("https://taskflow.example.com") }, singleLine = true, enabled = !state.isLoading)
                OutlinedTextField(email, onEmailChange, Modifier.fillMaxWidth(), label = { Text("Email") }, singleLine = true, enabled = !state.isLoading)
                if (registering) OutlinedTextField(displayName, onDisplayNameChange, Modifier.fillMaxWidth(), label = { Text("Имя") }, singleLine = true, enabled = !state.isLoading)
                OutlinedTextField(password, onPasswordChange, Modifier.fillMaxWidth(), label = { Text("Пароль") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), enabled = !state.isLoading)
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                state.verificationEmail?.let {
                    Text("Письмо с подтверждением отправлено на $it. Подтвердите email и затем войдите.", color = MaterialTheme.colorScheme.primary)
                    TextButton(onClick = onDismissVerification) { Text("Понятно") }
                }
                Button(onClick = onSubmit, modifier = Modifier.fillMaxWidth(), enabled = !state.isLoading) {
                    if (state.isLoading) CircularProgressIndicator(modifier = Modifier.size(TaskFlowSpace.lg), strokeWidth = 2.dp) else Text(if (registering) "Зарегистрироваться" else "Войти")
                }
            }
        }
        TextButton(onClick = onToggleMode, enabled = !state.isLoading, modifier = Modifier.fillMaxWidth()) { Text(if (registering) "Уже есть аккаунт" else "Создать аккаунт") }
    }
}
