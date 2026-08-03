package ru.taskflow.app.data.remote

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import ru.taskflow.app.data.session.SessionTokens
import ru.taskflow.app.data.session.TokenStore

class TaskFlowApiFactory(private val tokenStore: TokenStore) {
    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()

    fun create(serverUrl: String): TaskFlowApi {
        val refreshApi = Retrofit.Builder()
            .baseUrl(serverUrl)
            .client(OkHttpClient())
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(TaskFlowApi::class.java)
        val client = OkHttpClient.Builder()
            .addInterceptor(BearerInterceptor(tokenStore))
            .authenticator(RefreshingAuthenticator(tokenStore, refreshApi))
            .build()
        return Retrofit.Builder()
            .baseUrl(serverUrl)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(TaskFlowApi::class.java)
    }
}

private class BearerInterceptor(private val tokenStore: TokenStore) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = tokenStore.read()?.accessToken ?: return chain.proceed(chain.request())
        return chain.proceed(chain.request().newBuilder().header("Authorization", "Bearer $token").build())
    }
}

private class RefreshingAuthenticator(private val tokenStore: TokenStore, private val refreshApi: TaskFlowApi) : Authenticator {
    override fun authenticate(route: Route?, response: Response): Request? {
        if (response.request.url.encodedPath.endsWith("/auth/refresh") || responseCount(response) >= 2) return null
        val current = tokenStore.read() ?: return null
        return try {
            val refreshed = runBlocking { refreshApi.refresh(RefreshRequest(current.refreshToken)) }
            tokenStore.save(SessionTokens(refreshed.token, refreshed.refreshToken))
            response.request.newBuilder().header("Authorization", "Bearer ${refreshed.token}").build()
        } catch (_: Exception) {
            tokenStore.clear()
            null
        }
    }

    private fun responseCount(response: Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) { count++; prior = prior.priorResponse }
        return count
    }
}
