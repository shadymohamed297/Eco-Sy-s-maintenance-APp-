package com.markazsayana.app.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

private val Context.authDataStore by preferencesDataStore(name = "auth")

@Singleton
class TokenManager @Inject constructor(
    private val context: Context,
) {
    private val tokenKey = stringPreferencesKey("token")
    private val roleKey = stringPreferencesKey("role")

    val tokenFlow: Flow<String?> = context.authDataStore.data.map { it[tokenKey] }
    val roleFlow: Flow<String?> = context.authDataStore.data.map { it[roleKey] }

    /** Synchronous read used by the OkHttp auth interceptor, which cannot suspend. */
    fun tokenBlocking(): String? = runBlocking { context.authDataStore.data.first()[tokenKey] }

    suspend fun save(token: String, role: String) {
        context.authDataStore.edit {
            it[tokenKey] = token
            it[roleKey] = role
        }
    }

    suspend fun clear() {
        context.authDataStore.edit { it.clear() }
    }
}
