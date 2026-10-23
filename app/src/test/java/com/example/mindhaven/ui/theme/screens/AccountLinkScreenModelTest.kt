package com.example.mindhaven.ui.theme.screens

import com.example.mindhaven.data.auth.LinkProblem
import com.example.mindhaven.data.auth.LinkState
import com.example.mindhaven.data.auth.LinkStep
import com.example.mindhaven.data.auth.ReauthMethod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The account-linking screen's decisions (pure; the Composable only renders this model). */
class AccountLinkScreenModelTest {

    @Test fun `the Link button needs explicit consent AND a password, and is disabled while anything runs`() {
        val idle = linkScreenModel(LinkState.Idle)
        assertFalse("consent is never implied", canSubmitLink(idle, "pw", consent = false))
        assertFalse(canSubmitLink(idle, "", consent = true))
        assertTrue(canSubmitLink(idle, "pw", consent = true))
        for (step in LinkStep.values()) assertFalse(step.name, canSubmitLink(linkScreenModel(LinkState.Working(step)), "pw", true))
        assertFalse(canSubmitLink(linkScreenModel(LinkState.Linked), "pw", true))
    }

    @Test fun `re-authentication replaces the password form with the provider-specific confirmation`() {
        for (method in ReauthMethod.values()) {
            val m = linkScreenModel(LinkState.NeedsReauth(method))
            assertEquals(method, m.reauth); assertFalse(m.showPasswordForm)
            assertFalse(canSubmitLink(m, "pw", true))
        }
        val failed = linkScreenModel(LinkState.NeedsReauth(ReauthMethod.Password, LinkProblem.ReauthFailed))
        assertNotNull(failed.message)
    }

    @Test fun `failures show a safe message and let the user try again`() {
        for (p in LinkProblem.values()) {
            val m = linkScreenModel(LinkState.Failed(p))
            assertTrue(m.showPasswordForm); assertFalse(m.busy)
            val msg = m.message!!
            assertFalse("no backend codes leak into the UI: $msg", Regex("[A-Z]{3,}_[A-Z]{3,}").containsMatchIn(msg))
        }
        assertNotNull(linkScreenModel(LinkState.Reauthenticated).info)
        assertNull(linkScreenModel(LinkState.Idle).message)
    }

    @Test fun `progress text names the step and success shows no form`() {
        assertEquals("Linking your accounts…", linkScreenModel(LinkState.Working(LinkStep.Completing)).progressText)
        val linked = linkScreenModel(LinkState.Linked)
        assertTrue(linked.busy); assertFalse(linked.showPasswordForm)
    }
}
