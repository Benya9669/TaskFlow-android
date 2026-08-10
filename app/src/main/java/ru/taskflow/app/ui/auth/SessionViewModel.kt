package ru.taskflow.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.taskflow.app.data.session.SessionRepository
import ru.taskflow.app.data.session.TokenStore

data class SessionUiState(val isSignedIn: Boolean, val isLoading: Boolean = false, val error: String? = null, val verificationEmail: String? = null)
data class ProfileUiState(val displayName: String = "", val email: String = "", val timezone: String = "", val loading: Boolean = false, val saving: Boolean = false, val error: String? = null)

class SessionViewModel(private val repository: SessionRepository) : ViewModel() {
    private val _state = MutableStateFlow(SessionUiState(repository.hasSession()))
    val state = _state.asStateFlow()
    private val _profile = MutableStateFlow(ProfileUiState())
    val profile = _profile.asStateFlow()

    init { if (repository.hasSession()) loadProfile() }

    fun login(serverUrl: String, email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            _state.value = _state.value.copy(error = "Введите email и пароль")
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            runCatching { repository.login(serverUrl, email, password) }
                .onSuccess { _state.value = SessionUiState(isSignedIn = true); loadProfile() }
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

    fun verifyEmail(serverUrl: String?, token: String?) {
        if (serverUrl.isNullOrBlank() || token.isNullOrBlank()) {
            _state.value = _state.value.copy(error = "Ссылка подтверждения не содержит сервера или токена")
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            runCatching { repository.verifyEmail(serverUrl, token) }
                .onSuccess { _state.value = SessionUiState(isSignedIn = true); loadProfile() }
                .onFailure { _state.value = SessionUiState(isSignedIn = false, error = it.message ?: "Не удалось подтвердить email") }
        }
    }

    fun loadProfile() {
        if (!_state.value.isSignedIn || _profile.value.loading) return
        viewModelScope.launch {
            _profile.value = _profile.value.copy(loading = true, error = null)
            runCatching { repository.profile() }
                .onSuccess { _profile.value = ProfileUiState(it.displayName, it.email, it.timezone) }
                .onFailure { _profile.value = _profile.value.copy(loading = false, error = it.message ?: "Не удалось загрузить профиль") }
        }
    }

    fun updateProfile(displayName: String, timezone: String) {
        if (_profile.value.saving) return
        viewModelScope.launch {
            _profile.value = _profile.value.copy(saving = true, error = null)
            runCatching { repository.updateProfile(displayName, timezone) }
                .onSuccess { _profile.value = ProfileUiState(it.displayName, it.email, it.timezone) }
                .onFailure { _profile.value = _profile.value.copy(saving = false, error = it.message ?: "Не удалось сохранить профиль") }
        }
    }

    fun logout() { repository.logout(); _profile.value = ProfileUiState(); _state.value = SessionUiState(isSignedIn = false) }
}

class SessionViewModelFactory(private val tokenStore: TokenStore) : androidx.lifecycle.ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = SessionViewModel(SessionRepository(tokenStore)) as T
}
