package com.example.mindhaven.model

import android.content.Context
import com.example.mindhaven.BuildConfig
import com.example.mindhaven.data.auth.AuthRepository
import com.example.mindhaven.data.session.AuthActions
import com.example.mindhaven.data.session.BackendAuthNetwork
import com.example.mindhaven.data.session.BackendAuthRepository
import com.example.mindhaven.data.session.KeystoreTokenStore
import com.example.mindhaven.data.session.ScreenAuthAdapter
import com.example.mindhaven.data.session.TokenStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier @Retention(AnnotationRetention.BINARY) annotation class ApplicationScope
@Module @InstallIn(SingletonComponent::class)
object AppModule {
    @Provides @Singleton @ApplicationScope fun scope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Provides @Singleton fun tokenStore(@ApplicationContext context: Context): TokenStore = KeystoreTokenStore(context)
    @Provides @Singleton fun network(store: TokenStore): BackendAuthNetwork = BackendAuthNetwork((BuildConfig.API_BASE_URL.ifBlank { "https://invalid.invalid" }).toHttpUrl(), store)
    @Provides @Singleton fun client(network: BackendAuthNetwork): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(60,TimeUnit.SECONDS).readTimeout(60,TimeUnit.SECONDS).writeTimeout(60,TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false)
        .addInterceptor(network.interceptor).authenticator(network.authenticator).build()
    @Provides @Singleton fun backend(store: TokenStore, network: BackendAuthNetwork, client: OkHttpClient, @ApplicationScope scope: CoroutineScope): BackendAuthRepository = BackendAuthRepository(BuildConfig.API_BASE_URL,store,network,client,scope)
    @Provides fun authActions(backend: BackendAuthRepository): AuthActions = backend
    @Provides @Singleton fun repository(backend: BackendAuthRepository,@ApplicationScope scope: CoroutineScope): AuthRepository = ScreenAuthAdapter(backend,scope)
}
