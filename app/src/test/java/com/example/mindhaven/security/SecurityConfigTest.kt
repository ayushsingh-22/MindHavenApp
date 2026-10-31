package com.example.mindhaven.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Static checks of the repository's security-relevant source/config (no Android runtime needed).
 * Gradle runs unit tests with the module directory (app/) as the working directory.
 */
class SecurityConfigTest {
    private fun module(path: String) = File(path).takeIf { it.exists() } ?: File("app/$path")
    private fun read(path: String) = module(path).readText()
    private fun kotlinFiles(dir: String) = module(dir).walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    private val authDirs = listOf("src/main/java/com/example/mindhaven/data", "src/main/java/com/example/mindhaven/viewmodel/AuthViewModel.kt")
    private fun authSources(): List<File> = kotlinFiles(authDirs[0]) + module(authDirs[1])

    @Test fun `only encrypted tokens use DataStore and other storage APIs stay prohibited`() {
        val banned = listOf("SharedPreferences", "getSharedPreferences", "openFileOutput", "File(", "SQLite", "EncryptedSharedPreferences")
        for (f in authSources()) for (b in banned) assertFalse("${f.name} must not use $b", f.readText().contains(b))
        val source = read("src/main/java/com/example/mindhaven/data/session/TokenStore.kt")
        assertTrue(source.contains("AES/GCM/NoPadding")); assertTrue(source.contains("AndroidKeyStore")); assertTrue(source.contains("cipher.doFinal"))
        for (f in authSources().filterNot { it.name == "TokenStore.kt" }) assertFalse("${f.name} must not use DataStore", f.readText().contains("DataStore"))
    }

    @Test fun `no logging of anything in the auth and network code`() {
        for (f in authSources()) {
            val text = f.readText()
            assertFalse("${f.name} must not call android.util.Log", Regex("""\bLog\.[dviwe]\(""").containsMatchIn(text))
            assertFalse("${f.name} must not print", Regex("""\b(println|print|printStackTrace)\(""").containsMatchIn(text))
            assertFalse("${f.name} must not use a logging interceptor", text.contains("HttpLoggingInterceptor"))
        }
        assertFalse(read("src/main/java/com/example/mindhaven/model/AppModule.kt").contains("HttpLoggingInterceptor"))
    }

    @Test fun `the backend client never builds a userId query or body`() {
        val api = read("src/main/java/com/example/mindhaven/data/session/BackendAuthRepository.kt")
        assertFalse(api.contains("\"userId\" to")); assertFalse(api.contains("\"user_id\" to"))
    }

    @Test fun `no backend or admin secret is embedded in the Android sources or build files`() {
        val files = kotlinFiles("src/main") + listOf(module("build.gradle.kts"))
        val patterns = listOf("API_JWT_SECRET", "DATABASE_URL", "postgresql://", "BEGIN PRIVATE KEY", "\"private_key\"", "service_account", "NEXTAUTH_SECRET")
        for (f in files) for (p in patterns) assertFalse("${f.name} must not contain $p", f.readText().contains(p))
    }

    @Test fun `release denies cleartext and debug permits a local development server`() {
        val main = read("src/main/res/xml/network_security_config.xml")
        assertTrue(main.contains("""cleartextTrafficPermitted="false""""))
        val debug = read("src/debug/res/xml/network_security_config.xml")
        assertTrue(debug.contains("""cleartextTrafficPermitted="true""""))
    }

    @Test fun `manifest disables cleartext and backups and uses the network security config`() {
        val m = read("src/main/AndroidManifest.xml")
        assertTrue(m.contains("""android:usesCleartextTraffic="false""""))
        assertTrue(m.contains("""android:allowBackup="false""""))
        assertTrue(m.contains("""android:networkSecurityConfig="@xml/network_security_config""""))
    }

    @Test fun `backup and extraction rules exclude every domain`() {
        for (f in listOf("src/main/res/xml/backup_rules.xml", "src/main/res/xml/data_extraction_rules.xml")) {
            val x = read(f)
            for (d in listOf("root", "file", "database", "sharedpref", "external")) assertTrue("$f must exclude $d", x.contains("""<exclude domain="$d" path="." />"""))
            assertFalse("$f must not include anything", x.contains("<include"))
        }
    }

    @Test fun `gradle loads development origin locally and requires HTTPS for release`() {
        val g = read("build.gradle.kts")
        assertTrue(g.contains("local.properties")); assertTrue(g.contains("mindhavenDevApiUrl"))
        assertTrue(g.contains("Release backend must be https://")); assertTrue(g.contains("http://10.0.2.2:8080"))
    }

    @Test fun `release and debug base URLs come from BuildConfig only`() {
        val m = read("src/main/java/com/example/mindhaven/model/AppModule.kt")
        assertTrue(m.contains("BuildConfig.API_BASE_URL"))
        assertTrue(m.contains("authenticator(network.authenticator)"))
        assertFalse(m.contains("onrender.com"))
        assertTrue(m.contains("followRedirects(false)"))
    }

    @Test fun `ViewModel keeps no Context or Activity reference`() {
        val vm = read(authDirs[1])
        assertFalse("must not store a Context in a property", Regex("""(val|var)\s+\w+\s*:\s*(Context|Activity|android\.content\.Context)""").containsMatchIn(vm))
    }

    @Test fun `appwrite audio sources are untouched by the auth work`() {
        val vm = read("src/main/java/com/example/mindhaven/viewmodel/MeditationViewModel.kt")
        assertTrue(vm.contains("syd.cloud.appwrite.io"))
    }
}
