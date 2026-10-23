package com.example.mindhaven.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Static checks of the Phase 2B.5B.3 account-linking sources (no Android runtime). */
class AccountLinkSecurityTest {
    private fun module(path: String) = File(path).takeIf { it.exists() } ?: File("app/$path")
    private fun read(path: String) = module(path).readText()

    /** Source without comment lines: comments may name a banned API to explain why it is not used. */
    private fun code(path: String) = read(path).lines()
        .filterNot { it.trimStart().startsWith("*") || it.trimStart().startsWith("//") || it.trimStart().startsWith("/*") }
        .joinToString("\n")

    private val screen = "src/main/java/com/example/mindhaven/ui/theme/screens/AccountLinkScreen.kt"
    private val linker = "src/main/java/com/example/mindhaven/data/auth/AccountLinker.kt"
    private val vm = "src/main/java/com/example/mindhaven/viewmodel/AuthViewModel.kt"
    private val nav = "src/main/java/com/example/mindhaven/ui/theme/navigation/Navigation.kt"

    @Test fun `passwords, challenges and tokens are never put into saved state, persistence or route arguments`() {
        for (f in listOf(screen, linker, vm, nav)) {
            val c = code(f)
            assertFalse("$f must not call rememberSaveable", Regex("""\brememberSaveable\s*[({]""").containsMatchIn(c))
            for (banned in listOf("SavedStateHandle", "SharedPreferences", "DataStore", "Bundle", "putString", "navArgument")) {
                assertFalse("$f must not use $banned", c.contains(banned))
            }
        }
        val authNav = read("src/main/java/com/example/mindhaven/ui/theme/navigation/AuthNavigation.kt")
        assertFalse("no argument placeholder in the linking route", Regex("""LINK_ROUTE\s*=\s*"[^"]*[{?/]""").containsMatchIn(authNav))
    }

    @Test fun `no logging anywhere in the linking UI`() {
        val c = code(screen)
        assertFalse(Regex("""\bLog\.[dviwe]\(""").containsMatchIn(c))
        assertFalse(Regex("""\b(println|print|printStackTrace)\(""").containsMatchIn(c))
    }

    @Test fun `consent starts unchecked and the password field is cleared on submit`() {
        val t = read(screen)
        assertTrue(t.contains("var consent by remember { mutableStateOf(false) }"))
        assertTrue(t.contains("password = \"\" // cleared immediately"))
    }

    @Test fun `the screen never displays the e-mail, the Firebase uid or the canonical id`() {
        val c = code(screen)
        for (banned in listOf(".email", ".uid", "userId", "firebaseUid")) assertFalse("screen must not read $banned", c.contains(banned))
    }

    @Test fun `the link client sends no identity fields`() {
        val api = read("src/main/java/com/example/mindhaven/data/remote/BackendApi.kt")
        for (banned in listOf("userId", "user_id", "firebaseUid", "projectId", "emailVerified")) assertFalse(banned, api.contains(banned))
    }
}
