package com.markazsayana.app.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.markazsayana.app.data.local.TokenManager
import com.markazsayana.app.data.repository.SessionState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class CurrentUserViewModel @Inject constructor(
    sessionState: SessionState,
    tokenManager: TokenManager,
) : ViewModel() {
    val user: StateFlow<com.markazsayana.app.data.remote.UserDto?> = sessionState.user

    /** null while still reading DataStore for the first time; false once a session ends (login or a 401 clears it). */
    val isLoggedIn: StateFlow<Boolean?> = tokenManager.tokenFlow
        .map { it != null }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
}
