package com.markazsayana.app.di

import android.content.Context
import com.markazsayana.app.BuildConfig
import com.markazsayana.app.data.local.ServerSettingsStore
import com.markazsayana.app.data.local.TokenManager
import com.markazsayana.app.data.remote.ApiService
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
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideTokenManager(@ApplicationContext context: Context): TokenManager = TokenManager(context)

    @Provides
    @Singleton
    fun provideServerSettingsStore(@ApplicationContext context: Context): ServerSettingsStore = ServerSettingsStore(context)

    @Provides
    @Singleton
    fun provideJson(): Json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Provides
    @Singleton
    fun provideOkHttpClient(tokenManager: TokenManager, serverSettingsStore: ServerSettingsStore): OkHttpClient {
        val authInterceptor = okhttp3.Interceptor { chain ->
            val token = tokenManager.tokenBlocking()
            val request = if (token != null) {
                chain.request().newBuilder().addHeader("Authorization", "Bearer $token").build()
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
            val original = chain.request()
            val request = if (configured != null) {
                val newUrl = original.url.newBuilder()
                    .scheme(configured.scheme)
                    .host(configured.host)
                    .port(configured.port)
                    .build()
                original.newBuilder().url(newUrl).build()
            } else {
                original
            }
            chain.proceed(request)
        }
        // A 401 means the token is invalid/expired: clear it so the UI (which watches
        // TokenManager.tokenFlow) drops the user back to the login screen instead of every
        // screen just showing a generic "failed to load" error forever.
        val sessionExpiryInterceptor = okhttp3.Interceptor { chain ->
            val response = chain.proceed(chain.request())
            if (response.code == 401) {
                runBlocking { tokenManager.clear() }
            }
            response
        }
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.NONE
        }
        return OkHttpClient.Builder()
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
        .baseUrl(BuildConfig.BASE_URL)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    @Provides
    @Singleton
    fun provideApiService(retrofit: Retrofit): ApiService = retrofit.create(ApiService::class.java)
}
