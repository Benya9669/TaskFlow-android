package ru.taskflow.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.taskflow.app.data.session.SessionRepository
import ru.taskflow.app.data.session.TokenStore

data class SessionUiState(val isSignedIn: Boolean, val isLoading: Boolean = false, val error: String? = null, val verificationEmail: String? = null)

class SessionViewModel(private val repository: SessionRepository) : ViewModel() {
    private val _state = MutableStateFlow(SessionUiState(repository.hasSession()))
    val state = _state.asStateFlow()

    fun login(serverUrl: String, email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            _state.value = _state.value.copy(error = "Введите email и пароль")
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            runCatching { repository.login(serverUrl, email, password) }
                .onSuccess { _state.value = SessionUiState(isSignedIn = true) }
                .onFailure { _state.value = SessionUiState(isSignedIn = false, error = it.message ?: "Не удалось войти") }
        }
    }

    fun register(serverUrl: String, email: String, password: String, displayName: String) {
        if (serverUrl.isBlank() || email.isBlank() || password.isBlank() || displayName.isBlank()) {
            _state.value = _state.value.copy(error = "Заполните все поля")
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null, verificationEmail = null)
            runCatching { repository.register(serverUrl, email, password, displayName) }
                .onSuccess { _state.value = SessionUiState(isSignedIn = false, verificationEmail = it) }
                .onFailure { _state.value = SessionUiState(isSignedIn = false, error = it.message ?: "Не удалось зарегистрироваться") }
        }
    }

    fun dismissVerification() { _state.value = SessionUiState(isSignedIn = false) }

    fun logout() { repository.logout(); _state.value = SessionUiState(isSignedIn = false) }
}

class SessionViewModelFactory(private val tokenStore: TokenStore) : androidx.lifecycle.ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = SessionViewModel(SessionRepository(tokenStore)) as T
}
