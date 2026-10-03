package com.markazsayana.app.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.markazsayana.app.BuildConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

private val Context.serverSettingsDataStore by preferencesDataStore(name = "server_settings")

/**
 * Lets the app point at a real deployed backend (e.g. a Render URL) without a rebuild —
 * the maintenance center sets this once from the login screen after first deploying the
 * API, and it's saved locally from then on.
 */
@Singleton
class ServerSettingsStore @Inject constructor(
    private val context: Context,
) {
    private val urlKey = stringPreferencesKey("base_url")

    val baseUrlFlow: Flow<String> = context.serverSettingsDataStore.data
        .map { it[urlKey] ?: BuildConfig.BASE_URL }

    fun baseUrlBlocking(): String = runBlocking { context.serverSettingsDataStore.data.first()[urlKey] ?: BuildConfig.BASE_URL }

    suspend fun setBaseUrl(url: String) {
        val normalized = if (url.endsWith("/")) url else "$url/"
        context.serverSettingsDataStore.edit { it[urlKey] = normalized }
    }
}
