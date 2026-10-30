package com.example.mindhaven.data.session

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class TokenPair(val accessToken: String, val refreshToken: String, val accessExpiresAt: String, val refreshExpiresAt: String) {
    override fun toString() = "TokenPair(redacted)"
}

interface TokenStore {
    suspend fun read(): TokenPair?
    suspend fun write(tokens: TokenPair)
    suspend fun clear()
}

private val Context.tokenDataStore by preferencesDataStore(name = "auth_tokens")

/** AES-GCM envelope is also testable on the JVM with an ephemeral key. */
internal object TokenEnvelope {
    fun seal(tokens: TokenPair, key: SecretKey): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        return Base64.getEncoder().encodeToString(cipher.iv + cipher.doFinal(Json.encodeToString(TokenPair.serializer(), tokens).toByteArray()))
    }
    fun open(value: String, key: SecretKey): TokenPair {
        val bytes = Base64.getDecoder().decode(value)
        require(bytes.size > 12)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        return Json.decodeFromString<TokenPair>(cipher.doFinal(bytes.copyOfRange(12, bytes.size)).decodeToString())
    }
}

/** Ciphertext only is persisted; backup/transfer rules exclude the entire app. */
class KeystoreTokenStore(context: Context) : TokenStore {
    private val store = context.applicationContext.tokenDataStore
    private val keyName = stringPreferencesKey("ciphertext")
    private val alias = "mindhaven.auth.tokens.v1"
    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    override suspend fun read(): TokenPair? {
        val value = store.data.first()[keyName] ?: return null
        return try { TokenEnvelope.open(value, key()) }
        catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (_: Exception) { clear(); null }
    }
    override suspend fun write(tokens: TokenPair) {
        try { val value = TokenEnvelope.seal(tokens, key()); store.edit { it[keyName] = value } }
        catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: Exception) { clear(); throw e }
    }
    override suspend fun clear() { store.edit { it.remove(keyName) } }
}
