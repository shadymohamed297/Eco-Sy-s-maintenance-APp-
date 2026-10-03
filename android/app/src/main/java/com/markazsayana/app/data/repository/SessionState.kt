package com.markazsayana.app.data.repository

import com.markazsayana.app.data.remote.UserDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** In-memory cache of the signed-in user, populated on login so screens can show name/branch/avatar without refetching. */
@Singleton
class SessionState @Inject constructor() {
    private val _user = MutableStateFlow<UserDto?>(null)
    val user: StateFlow<UserDto?> = _user

    fun set(user: UserDto) {
        _user.value = user
    }

    fun clear() {
        _user.value = null
    }
}
