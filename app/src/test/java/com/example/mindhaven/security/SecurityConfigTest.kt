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

    @Test fun `tokens are never persisted (no SharedPreferences, DataStore, files or SQLite in the auth and network code)`() {
        val banned = listOf("SharedPreferences", "getSharedPreferences", "DataStore", "openFileOutput", "File(", "SQLite", "Room", "EncryptedSharedPreferences")
        for (f in authSources()) for (b in banned) assertFalse("${f.name} must not use $b", f.readText().contains(b))
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
        val api = read("src/main/java/com/example/mindhaven/data/remote/BackendApi.kt")
        assertFalse(api.contains("userId")); assertFalse(api.contains("user_id"))
    }

    @Test fun `no backend or admin secret is embedded in the Android sources or build files`() {
        val files = kotlinFiles("src/main") + listOf(module("build.gradle.kts"))
        val patterns = listOf("API_JWT_SECRET", "DATABASE_URL", "postgresql://", "BEGIN PRIVATE KEY", "\"private_key\"", "service_account", "FIREBASE_PRIVATE_KEY", "NEXTAUTH_SECRET")
        for (f in files) for (p in patterns) assertFalse("${f.name} must not contain $p", f.readText().contains(p))
    }

    @Test fun `main network policy forbids cleartext and the debug policy only allows emulator and loopback hosts`() {
        val main = read("src/main/res/xml/network_security_config.xml")
        assertTrue(main.contains("""cleartextTrafficPermitted="false""""))
        assertFalse(main.contains("""cleartextTrafficPermitted="true""""))
        val debug = read("src/debug/res/xml/network_security_config.xml")
        val allowed = Regex("""<domain-config cleartextTrafficPermitted="true">(.*?)</domain-config>""", RegexOption.DOT_MATCHES_ALL).find(debug)!!.groupValues[1]
        val domains = Regex("""<domain[^>]*>([^<]+)</domain>""").findAll(allowed).map { it.groupValues[1].trim() }.toSet()
        assertTrue(domains == setOf("10.0.2.2", "localhost", "127.0.0.1"))
        assertFalse("base config of the debug file must still deny cleartext", Regex("""<base-config cleartextTrafficPermitted="true"""").containsMatchIn(debug))
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

    @Test fun `gradle refuses production URLs, release cleartext and defaults the release URL to empty`() {
        val g = read("build.gradle.kts")
        assertTrue(g.contains("emotionsense-ai-3vxa.onrender.com"))
        assertTrue(g.contains("""must be https://"""))
        assertTrue(g.contains("""val releaseApiUrl = cleanUrl(project.findProperty("mindhavenReleaseApiUrl") as String?)"""))
        assertFalse("no default production URL for release", Regex("""releaseApiUrl\s*=.*onrender""").containsMatchIn(g))
        assertTrue(g.contains("""http://10.0.2.2:8080"""))
    }

    @Test fun `release and debug base URLs come from BuildConfig only`() {
        val m = read("src/main/java/com/example/mindhaven/model/AppModule.kt")
        assertTrue(m.contains("BuildConfig.API_BASE_URL"))
        assertTrue(m.contains("allowCleartext = BuildConfig.DEBUG"))
        assertFalse(m.contains("onrender.com"))
        assertTrue(m.contains("followRedirects(false)"))
    }

    @Test fun `ViewModel keeps no Context or Activity reference`() {
        val vm = read(authDirs[1])
        assertFalse("must not store a Context in a property", Regex("""(val|var)\s+\w+\s*:\s*(Context|Activity|android\.content\.Context)""").containsMatchIn(vm))
        assertTrue(vm.contains("fun getGoogleSignInIntent(context: Context)"))
    }

    @Test fun `Google Sign-In holds the application context only`() {
        val g = read("src/main/java/com/example/mindhaven/data/auth/GoogleSignInProvider.kt")
        assertTrue(g.contains("context.applicationContext"))
        assertFalse(g.contains("Activity"))
    }

    @Test fun `appwrite audio sources are untouched by the auth work`() {
        val vm = read("src/main/java/com/example/mindhaven/viewmodel/MeditationViewModel.kt")
        assertTrue(vm.contains("syd.cloud.appwrite.io"))
    }
}
