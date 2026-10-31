package com.example.mindhaven.data.session

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.security.KeyStore
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KeystoreTokenStoreDeviceTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private fun keystore() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    @Test fun encryptedDataSurvivesStoreRecreation() = runBlocking {
        val alias = "mindhaven.test.${UUID.randomUUID()}"
        val file = File(context.filesDir, "datastore/test-${UUID.randomUUID()}.preferences_pb")
        val key = stringPreferencesKey("ciphertext")
        val job1: Job = SupervisorJob(); val first = PreferenceDataStoreFactory.create(scope = CoroutineScope(job1 + Dispatchers.IO)) { file }
        val pair = TokenPair("synthetic-access", "synthetic-refresh", "expiry", "expiry")
        try {
            KeystoreTokenStore(first,alias).write(pair)
            assertTrue(keystore().containsAlias(alias)); assertNotNull(first.data.first()[key])
            job1.cancelAndJoin()
            val job2: Job = SupervisorJob(); val second = PreferenceDataStoreFactory.create(scope = CoroutineScope(job2 + Dispatchers.IO)) { file }
            try { assertEquals(pair,KeystoreTokenStore(second,alias).read()) } finally { job2.cancelAndJoin() }
        } finally { job1.cancelAndJoin(); keystore().deleteEntry(alias); file.delete() }
    }
    @Test fun tamperingClearsStoredSession() = runBlocking {
        val alias = "mindhaven.test.${UUID.randomUUID()}"; val file = File(context.filesDir,"datastore/test-${UUID.randomUUID()}.preferences_pb")
        val job: Job = SupervisorJob(); val data = PreferenceDataStoreFactory.create(scope = CoroutineScope(job + Dispatchers.IO)) { file }
        try {
            val store=KeystoreTokenStore(data,alias);store.write(TokenPair("synthetic-access","synthetic-refresh","e","e"))
            val key=stringPreferencesKey("ciphertext")
            val bytes=java.util.Base64.getDecoder().decode(data.data.first()[key]!!)
            bytes[bytes.lastIndex]=(bytes.last().toInt() xor 1).toByte()
            data.edit { it[key] = java.util.Base64.getEncoder().encodeToString(bytes) }
            assertNull(store.read());assertNull(data.data.first()[key])
        } finally { job.cancelAndJoin();keystore().deleteEntry(alias);file.delete() }
    }
    @Test fun missingKeyClearsStoredSession() = runBlocking {
        val alias = "mindhaven.test.${UUID.randomUUID()}"; val file = File(context.filesDir,"datastore/test-${UUID.randomUUID()}.preferences_pb")
        val job: Job = SupervisorJob(); val data = PreferenceDataStoreFactory.create(scope = CoroutineScope(job + Dispatchers.IO)) { file }
        try {
            val store=KeystoreTokenStore(data,alias);store.write(TokenPair("synthetic-access","synthetic-refresh","e","e"))
            keystore().deleteEntry(alias)
            assertNull(store.read());assertNull(data.data.first()[stringPreferencesKey("ciphertext")])
        } finally { job.cancelAndJoin();keystore().deleteEntry(alias);file.delete() }
    }
}
