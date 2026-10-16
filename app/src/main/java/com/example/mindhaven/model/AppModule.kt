package com.example.mindhaven.model

import android.content.Context
import com.example.mindhaven.BuildConfig
import com.example.mindhaven.data.auth.AuthRepository
import com.example.mindhaven.data.auth.BackendSessionManager
import com.example.mindhaven.data.auth.DefaultAuthRepository
import com.example.mindhaven.data.auth.DefaultFirebaseAuthGateway
import com.example.mindhaven.data.auth.FirebaseAuthGateway
import com.example.mindhaven.data.auth.GoogleSignInIntentSource
import com.example.mindhaven.data.auth.GoogleSignInProvider
import com.example.mindhaven.data.remote.BackendApi
import com.example.mindhaven.data.remote.OkHttpBackendApi
import com.google.firebase.auth.FirebaseAuth
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance()

    @Provides
    @Singleton
    fun provideFirebaseAuthGateway(auth: FirebaseAuth): FirebaseAuthGateway = DefaultFirebaseAuthGateway(auth)

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * ONE HTTP client for the backend. Deliberately no logging interceptor (it would print Authorization headers and the
     * token-exchange body) and no redirects (a redirect must never carry a credential somewhere else).
     * Timeouts allow for a Render free-tier cold start.
     */
    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .callTimeout(60, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    /** Base URL comes from BuildConfig (debug: dev backend / emulator host, release: explicit HTTPS or empty = not configured). */
    @Provides
    @Singleton
    fun provideBackendApi(client: OkHttpClient): BackendApi =
        OkHttpBackendApi(BuildConfig.API_BASE_URL, client, allowCleartext = BuildConfig.DEBUG)

    @Provides
    @Singleton
    fun provideBackendSessionManager(
        gateway: FirebaseAuthGateway,
        api: BackendApi,
        @ApplicationScope scope: CoroutineScope
    ): BackendSessionManager = BackendSessionManager(gateway, api, scope)

    @Provides
    @Singleton
    fun provideGoogleSignInProvider(@ApplicationContext context: Context): GoogleSignInProvider = GoogleSignInProvider(context)

    @Provides
    fun provideGoogleSignInIntentSource(provider: GoogleSignInProvider): GoogleSignInIntentSource = provider

    @Provides
    @Singleton
    fun provideAuthRepository(
        gateway: FirebaseAuthGateway,
        session: BackendSessionManager,
        google: GoogleSignInProvider,
        @ApplicationScope scope: CoroutineScope
    ): AuthRepository = DefaultAuthRepository(gateway, session, google, scope)
}
