package ru.taskflow.app.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import ru.taskflow.app.ui.theme.TaskFlowSpace

@Composable
fun LoginScreen(state: SessionUiState, onLogin: (String, String, String) -> Unit, onRegister: (String, String, String, String) -> Unit, onDismissVerification: () -> Unit) {
    var serverUrl by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var registering by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxWidth().padding(TaskFlowSpace.lg),
        verticalArrangement = Arrangement.spacedBy(TaskFlowSpace.md),
    ) {
        Text("TaskFlow", style = MaterialTheme.typography.headlineMedium)
        Text(if (registering) "Создайте аккаунт на своём self-hosted сервере." else "Войдите в свой self-hosted сервер.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(serverUrl, { serverUrl = it }, Modifier.fillMaxWidth(), label = { Text("URL сервера") }, placeholder = { Text("https://taskflow.example.com") }, singleLine = true, enabled = !state.isLoading)
        OutlinedTextField(email, { email = it }, Modifier.fillMaxWidth(), label = { Text("Email") }, singleLine = true, enabled = !state.isLoading)
        if (registering) OutlinedTextField(displayName, { displayName = it }, Modifier.fillMaxWidth(), label = { Text("Имя") }, singleLine = true, enabled = !state.isLoading)
        OutlinedTextField(password, { password = it }, Modifier.fillMaxWidth(), label = { Text("Пароль") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), enabled = !state.isLoading)
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        state.verificationEmail?.let {
            Text("Письмо с подтверждением отправлено на $it. Подтвердите email и затем войдите.", color = MaterialTheme.colorScheme.primary)
            TextButton(onClick = onDismissVerification) { Text("Понятно") }
        }
        Button(onClick = { if (registering) onRegister(serverUrl, email, password, displayName) else onLogin(serverUrl, email, password) }, modifier = Modifier.fillMaxWidth(), enabled = !state.isLoading) {
            if (state.isLoading) CircularProgressIndicator() else Text(if (registering) "Зарегистрироваться" else "Войти")
        }
        TextButton(onClick = { registering = !registering }, enabled = !state.isLoading) { Text(if (registering) "Уже есть аккаунт" else "Создать аккаунт") }
    }
}
