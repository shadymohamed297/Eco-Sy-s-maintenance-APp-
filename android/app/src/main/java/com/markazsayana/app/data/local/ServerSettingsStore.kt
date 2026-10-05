package com.markazsayana.app.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.markazsayana.app.BuildConfig
import com.markazsayana.app.data.repository.SessionState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.HttpUrl
import javax.inject.Inject
import javax.inject.Singleton

private val Context.serverSettingsDataStore by preferencesDataStore(name = "server_settings")

fun normalizeServerBaseUrl(value: String): String {
    val url = value.trim().toHttpUrlOrNull()
        ?: throw IllegalArgumentException("اكتب رابط النظام كاملًا، بدايةً من https://")
    require(url.username.isEmpty() && url.password.isEmpty() && url.query == null && url.fragment == null) {
        "استخدم رابط النظام فقط، بدون بيانات دخول أو إضافات بعد الرابط"
    }
    return url.newBuilder().encodedPath(url.encodedPath.trimEnd('/') + "/").build().toString()
}

fun resolveServerRequestUrl(baseUrl: HttpUrl, requestUrl: HttpUrl): HttpUrl = baseUrl.newBuilder()
    .encodedPath(baseUrl.encodedPath.trimEnd('/') + requestUrl.encodedPath)
    .encodedQuery(requestUrl.encodedQuery)
    .fragment(null)
    .build()

/**
 * Lets the app point at a real deployed backend (e.g. a Render URL) without a rebuild —
 * the maintenance center sets this once from the login screen after first deploying the
 * API, and it's saved locally from then on.
 */
@Singleton
class ServerSettingsStore @Inject constructor(
    private val context: Context,
    private val tokenManager: TokenManager,
    private val sessionState: SessionState,
) {
    private val urlKey = stringPreferencesKey("base_url")

    val baseUrlFlow: Flow<String> = context.serverSettingsDataStore.data
        .map { it[urlKey] ?: BuildConfig.BASE_URL }

    fun baseUrlBlocking(): String = runBlocking { context.serverSettingsDataStore.data.first()[urlKey] ?: BuildConfig.BASE_URL }

    suspend fun setBaseUrl(url: String) {
        val normalized = normalizeServerBaseUrl(url)
        if (normalized == runCatching { normalizeServerBaseUrl(baseUrlFlow.first()) }.getOrNull()) return
        // Credentials belong to one backend; never carry them to a different address.
        tokenManager.clear()
        sessionState.clear()
        context.serverSettingsDataStore.edit { it[urlKey] = normalized }
    }
}
