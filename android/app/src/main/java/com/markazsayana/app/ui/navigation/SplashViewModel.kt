package com.markazsayana.app.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.markazsayana.app.data.local.TokenManager
import com.markazsayana.app.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface SessionDestination {
    data object Checking : SessionDestination
    data object NeedsLogin : SessionDestination
    data class LoggedIn(val role: String) : SessionDestination
}

@HiltViewModel
class SplashViewModel @Inject constructor(
    private val tokenManager: TokenManager,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _destination = MutableStateFlow<SessionDestination>(SessionDestination.Checking)
    val destination: StateFlow<SessionDestination> = _destination

    init {
        viewModelScope.launch {
            val token = tokenManager.tokenFlow.first()
            if (token == null) {
                _destination.value = SessionDestination.NeedsLogin
                return@launch
            }
            try {
                val user = authRepository.currentUser()
                _destination.value = SessionDestination.LoggedIn(user.role)
            } catch (e: Exception) {
                authRepository.logout()
                _destination.value = SessionDestination.NeedsLogin
            }
        }
    }
}
