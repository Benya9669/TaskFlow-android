package ru.taskflow.app.data.session

import ru.taskflow.app.data.remote.LoginRequest
import ru.taskflow.app.data.remote.TaskFlowApiFactory

class SessionRepository(private val tokenStore: TokenStore) {
    fun hasSession(): Boolean = tokenStore.read() != null && tokenStore.serverUrl() != null

    suspend fun login(serverUrl: String, email: String, password: String) {
        val normalizedUrl = normalizeServerUrl(serverUrl)
        val response = TaskFlowApiFactory(tokenStore).create(normalizedUrl).login(LoginRequest(email.trim(), password))
        tokenStore.saveServerUrl(normalizedUrl)
        tokenStore.save(SessionTokens(response.token, response.refreshToken))
    }

    fun logout() = tokenStore.clear()

    private fun normalizeServerUrl(value: String): String {
        val base = value.trim().trimEnd('/')
        require(base.startsWith("https://") || base.startsWith("http://")) { "Укажите URL с https://" }
        return if (base.endsWith("/api/v1")) "$base/" else "$base/api/v1/"
    }
}
