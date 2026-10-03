package com.markazsayana.app.data.repository

import com.markazsayana.app.data.local.TokenManager
import com.markazsayana.app.data.remote.ApiService
import com.markazsayana.app.data.remote.ChangePasswordRequest
import com.markazsayana.app.data.remote.LoginRequest
import com.markazsayana.app.data.remote.UserDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val api: ApiService,
    private val tokenManager: TokenManager,
    private val sessionState: SessionState,
) {
    val isLoggedIn: Flow<Boolean> = tokenManager.tokenFlow.map { it != null }

    suspend fun login(identifier: String, password: String): UserDto {
        val response = api.login(LoginRequest(identifier, password))
        tokenManager.save(response.token, response.user.role)
        sessionState.set(response.user)
        return response.user
    }

    suspend fun currentUser(): UserDto {
        val user = api.me().user
        sessionState.set(user)
        return user
    }

    suspend fun logout() {
        tokenManager.clear()
        sessionState.clear()
    }

    suspend fun changePassword(currentPassword: String, newPassword: String) {
        api.changePassword(ChangePasswordRequest(currentPassword, newPassword))
    }
}
