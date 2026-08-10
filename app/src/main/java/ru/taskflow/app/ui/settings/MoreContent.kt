package ru.taskflow.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import ru.taskflow.app.ui.auth.ProfileUiState
import ru.taskflow.app.ui.theme.TaskFlowSpace

@Composable
fun MoreContent(
    serverUrl: String?,
    versionName: String,
    syncing: Boolean,
    syncError: String?,
    notificationsEnabled: Boolean,
    profile: ProfileUiState,
    onRefresh: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onSaveProfile: (String, String) -> Unit,
    onLogout: () -> Unit,
) {
    var displayName by rememberSaveable(profile.displayName) { mutableStateOf(profile.displayName) }
    var timezone by rememberSaveable(profile.timezone) { mutableStateOf(profile.timezone) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(TaskFlowSpace.md),
    ) {
        Text("Приложение", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
        Text("Профиль, синхронизация, уведомления и безопасность.", color = MaterialTheme.colorScheme.onSurfaceVariant)

        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(TaskFlowSpace.md), verticalArrangement = Arrangement.spacedBy(TaskFlowSpace.md)) {
                SettingRow({ Icon(Icons.Outlined.Person, null) }, "Профиль", profile.email.ifBlank { "Загрузка профиля…" })
                OutlinedTextField(displayName, { displayName = it }, Modifier.fillMaxWidth(), label = { Text("Имя") }, singleLine = true, enabled = !profile.loading && !profile.saving)
                OutlinedTextField(timezone, { timezone = it }, Modifier.fillMaxWidth(), label = { Text("Часовой пояс") }, placeholder = { Text("Europe/Moscow") }, singleLine = true, enabled = !profile.loading && !profile.saving)
                profile.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Button(onClick = { onSaveProfile(displayName, timezone) }, enabled = displayName.isNotBlank() && timezone.isNotBlank() && !profile.saving) {
                    Text(if (profile.saving) "Сохранение…" else "Сохранить профиль")
                }
            }
        }

        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(TaskFlowSpace.md), verticalArrangement = Arrangement.spacedBy(TaskFlowSpace.md)) {
                SettingRow({ Icon(Icons.Outlined.CloudDone, null) }, if (syncing) "Синхронизация…" else "Offline-first включён", serverUrl ?: "Адрес сервера не найден")
                syncError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                OutlinedButton(onClick = onRefresh, enabled = !syncing, modifier = Modifier.fillMaxWidth()) { Text("Синхронизировать сейчас") }
                HorizontalDivider()
                SettingRow({ Icon(Icons.Outlined.Notifications, null) }, "Уведомления", if (notificationsEnabled) "Разрешены" else "Выключены для TaskFlow")
                OutlinedButton(onClick = onOpenNotificationSettings, modifier = Modifier.fillMaxWidth()) { Text("Настроить уведомления") }
                HorizontalDivider()
                SettingRow({ Icon(Icons.Outlined.Security, null) }, "Сессия на этом устройстве", "Токены хранятся в Android Keystore")
            }
        }

        OutlinedButton(onClick = onLogout, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
            Icon(Icons.AutoMirrored.Outlined.Logout, null)
            Text("Выйти", modifier = Modifier.padding(start = TaskFlowSpace.sm))
        }
        Text("TaskFlow Android $versionName", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SettingRow(icon: @Composable () -> Unit, title: String, detail: String) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(TaskFlowSpace.md)) {
        icon()
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(TaskFlowSpace.xs)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
