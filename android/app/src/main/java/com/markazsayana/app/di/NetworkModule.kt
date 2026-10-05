package com.markazsayana.app.di

import android.content.Context
import com.markazsayana.app.data.local.ServerSettingsStore
import com.markazsayana.app.data.local.resolveServerRequestUrl
import com.markazsayana.app.data.local.TokenManager
import com.markazsayana.app.data.remote.ApiService
import com.markazsayana.app.data.repository.SessionState
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import java.io.IOException
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideTokenManager(@ApplicationContext context: Context): TokenManager = TokenManager(context)

    @Provides
    @Singleton
    fun provideServerSettingsStore(@ApplicationContext context: Context, tokenManager: TokenManager, sessionState: SessionState): ServerSettingsStore =
        ServerSettingsStore(context, tokenManager, sessionState)

    @Provides
    @Singleton
    fun provideJson(): Json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Provides
    @Singleton
    fun provideOkHttpClient(tokenManager: TokenManager, serverSettingsStore: ServerSettingsStore, sessionState: SessionState): OkHttpClient {
        val authInterceptor = okhttp3.Interceptor { chain ->
            val token = tokenManager.tokenBlocking()
            val request = if (token != null) {
                chain.request().newBuilder().header("Authorization", "Bearer $token").build()
            } else {
                chain.request()
            }
            chain.proceed(request)
        }
        // Retrofit is built once with a fixed placeholder base URL; this interceptor swaps
        // in whatever server address is actually configured (see ServerSettingsStore) on
        // every request, so changing the backend URL at runtime needs no rebuild.
        val dynamicBaseUrlInterceptor = okhttp3.Interceptor { chain ->
            val configured = serverSettingsStore.baseUrlBlocking().toHttpUrlOrNull()
                ?: throw IOException("عنوان النظام غير صالح")
            val original = chain.request()
            val newUrl = resolveServerRequestUrl(configured, original.url)
            val request = original.newBuilder().url(newUrl).build()
            chain.proceed(request)
        }
        // A 401 means the token is invalid/expired: clear it so the UI (which watches
        // TokenManager.tokenFlow) drops the user back to the login screen instead of every
        // screen just showing a generic "failed to load" error forever.
        val sessionExpiryInterceptor = okhttp3.Interceptor { chain ->
            val response = chain.proceed(chain.request())
            val sentToken = chain.request().header("Authorization")?.removePrefix("Bearer ")
            if (response.code == 401 && sentToken != null && sentToken == tokenManager.tokenBlocking()) {
                runBlocking { tokenManager.clear() }
                sessionState.clear()
            }
            response
        }
        val logging = HttpLoggingInterceptor().apply {
            // Staff APKs may be debug builds. Requests contain passwords, personal data,
            // photos and signatures, so none of them belong in device logs.
            level = HttpLoggingInterceptor.Level.NONE
        }
        return OkHttpClient.Builder()
            // The free hosting tier this app's backend typically runs on sleeps after
            // inactivity and can take 50+ seconds to wake on the first request — OkHttp's
            // 10s default would wrongly surface that as "couldn't connect to server".
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .addInterceptor(dynamicBaseUrlInterceptor)
            .addInterceptor(authInterceptor)
            .addInterceptor(sessionExpiryInterceptor)
            .addInterceptor(logging)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient, json: Json): Retrofit = Retrofit.Builder()
        // Retrofit requires a syntactically valid base URL at construction time, but the
        // actual host used is whatever dynamicBaseUrlInterceptor rewrites it to per-request.
        .baseUrl("https://maintenance.invalid/")
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    @Provides
    @Singleton
    fun provideApiService(retrofit: Retrofit): ApiService = retrofit.create(ApiService::class.java)
}
