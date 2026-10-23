package com.example.mindhaven.ui.theme.screens

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mindhaven.data.auth.LinkState
import com.example.mindhaven.data.auth.LinkStep
import com.example.mindhaven.data.auth.ReauthMethod
import com.example.mindhaven.data.auth.linkProblemMessage
import com.example.mindhaven.ui.theme.LavenderBlue
import com.example.mindhaven.ui.theme.LavenderBlush
import com.example.mindhaven.ui.theme.MediumPurple
import com.example.mindhaven.ui.theme.PaleLavender
import com.example.mindhaven.ui.theme.TwilightLavender
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException

private val linkBackground = Brush.verticalGradient(listOf(LavenderBlush, PaleLavender, LavenderBlue))

/** What the screen offers for a given [LinkState]. Pure, so it is unit-tested without a Compose runtime. */
internal data class LinkScreenModel(
    val busy: Boolean,
    val progressText: String?,
    val showPasswordForm: Boolean,
    val reauth: ReauthMethod?,
    val message: String?,
    val info: String?
)

internal fun linkScreenModel(state: LinkState): LinkScreenModel = when (state) {
    LinkState.Idle -> LinkScreenModel(false, null, true, null, null, null)
    LinkState.Reauthenticated -> LinkScreenModel(false, null, true, null, null, "Sign-in confirmed. Enter your MantraAI password to finish linking.")
    is LinkState.Working -> LinkScreenModel(
        busy = true,
        progressText = when (state.step) {
            LinkStep.Starting -> "Checking your MantraAI password…"
            LinkStep.Completing -> "Linking your accounts…"
            LinkStep.Recovering -> "Confirming your account…"
            LinkStep.Reauthenticating -> "Confirming your sign-in…"
        },
        showPasswordForm = state.step != LinkStep.Reauthenticating,
        reauth = null, message = null, info = null
    )
    is LinkState.NeedsReauth -> LinkScreenModel(false, null, false, state.method, state.problem?.let { linkProblemMessage(it) }, null)
    is LinkState.Failed -> LinkScreenModel(false, null, true, null, linkProblemMessage(state.problem, state.retryAfterSeconds), null)
    LinkState.Linked -> LinkScreenModel(true, "Linked. Opening MindHaven…", false, null, null, null)
}

/** The Link button is usable only with consent, a password, and nothing running. */
internal fun canSubmitLink(model: LinkScreenModel, password: String, consent: Boolean) =
    !model.busy && model.showPasswordForm && consent && password.isNotEmpty()

/**
 * Restricted screen shown while the backend answers ACCOUNT_LINK_REQUIRED. Firebase is signed in, the app is NOT open.
 *
 * Secrets: the password field lives in remember{} (NOT rememberSaveable, so it never reaches saved instance state) and is
 * cleared the moment Link is pressed. The challenge and tokens never reach this screen. No e-mail or account id is shown.
 */
@Composable
fun AccountLinkScreen(
    state: LinkState,
    googleSignInIntent: (Context) -> Intent,
    onLink: (password: String, consent: Boolean) -> Unit,
    onReauthenticateWithGoogle: (googleIdToken: String) -> Unit,
    onReauthenticateWithPassword: (firebasePassword: String) -> Unit,
    onCancel: () -> Unit
) {
    val model = linkScreenModel(state)
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var consent by remember { mutableStateOf(false) } // never preselected
    var reauthPassword by remember { mutableStateOf("") }
    var localMessage by remember { mutableStateOf<String?>(null) }

    val googleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK || result.data == null) {
            localMessage = "Google confirmation was cancelled."
            return@rememberLauncherForActivityResult
        }
        val idToken = try {
            GoogleSignIn.getSignedInAccountFromIntent(result.data).getResult(ApiException::class.java)?.idToken
        } catch (e: ApiException) {
            null
        }
        if (idToken.isNullOrEmpty()) localMessage = "Google confirmation failed. Please try again." else { localMessage = null; onReauthenticateWithGoogle(idToken) }
    }

    fun submit() {
        if (!canSubmitLink(model, password, consent)) return
        val pw = password
        password = "" // cleared immediately; the value travels only in this one call
        passwordVisible = false
        localMessage = null
        focus.clearFocus()
        onLink(pw, consent)
    }

    Box(modifier = Modifier.fillMaxSize().background(linkBackground), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                "Link your MantraAI account",
                modifier = Modifier.semantics { heading() },
                style = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold, color = MediumPurple),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "You already have a MantraAI account associated with this sign-in. Enter your MantraAI web-account password " +
                    "to securely connect MindHaven.",
                fontSize = 15.sp, color = TwilightLavender, textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Your existing MantraAI data stays under the same account. Nothing is linked without your permission.",
                fontSize = 14.sp, color = TwilightLavender, textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(24.dp))

            val message = localMessage ?: model.message
            model.info?.let {
                Text(it, color = MediumPurple, fontSize = 14.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                Spacer(Modifier.height(12.dp))
            }

            when {
                model.reauth != null -> ReauthSection(
                    method = model.reauth,
                    password = reauthPassword,
                    onPasswordChange = { reauthPassword = it },
                    onGoogle = { localMessage = null; googleLauncher.launch(googleSignInIntent(context)) },
                    onPassword = {
                        val pw = reauthPassword
                        reauthPassword = ""
                        focus.clearFocus()
                        onReauthenticateWithPassword(pw)
                    },
                    onSignInAgain = onCancel
                )
                model.showPasswordForm -> {
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("MantraAI password", color = Color.Black) },
                        singleLine = true,
                        enabled = !model.busy,
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { submit() }),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = TextStyle(color = Color.Black),
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = if (passwordVisible) "Hide password" else "Show password",
                                    tint = Color.Black
                                )
                            }
                        }
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .toggleable(value = consent, enabled = !model.busy, role = Role.Checkbox, onValueChange = { consent = it }),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = consent, onCheckedChange = null, enabled = !model.busy,
                            colors = CheckboxDefaults.colors(checkedColor = MediumPurple)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("I want to link this MindHaven sign-in to my existing MantraAI account.", fontSize = 14.sp, color = Color.Black)
                    }
                }
            }

            message?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = Color.Red, fontSize = 14.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive })
            }

            Spacer(Modifier.height(20.dp))
            if (model.busy) {
                CircularProgressIndicator(color = MediumPurple)
                model.progressText?.let { Spacer(Modifier.height(8.dp)); Text(it, fontSize = 14.sp, color = TwilightLavender) }
            } else if (model.showPasswordForm) {
                Button(
                    onClick = { submit() },
                    enabled = canSubmitLink(model, password, consent),
                    modifier = Modifier.fillMaxWidth().height(55.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MediumPurple)
                ) { Text("Link account", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Color.White) }
            }

            Spacer(Modifier.height(12.dp))
            TextButton(onClick = { password = ""; reauthPassword = ""; onCancel() }) {
                Text("Cancel and use another account", color = MediumPurple)
            }
        }
    }
}

@Composable
private fun ReauthSection(
    method: ReauthMethod,
    password: String,
    onPasswordChange: (String) -> Unit,
    onGoogle: () -> Unit,
    onPassword: () -> Unit,
    onSignInAgain: () -> Unit
) {
    Text(
        "For your security, linking needs a recent sign-in. Please confirm it's you.",
        fontSize = 15.sp, color = Color.Black, textAlign = TextAlign.Center
    )
    Spacer(Modifier.height(16.dp))
    when (method) {
        ReauthMethod.Google -> Button(
            onClick = onGoogle,
            modifier = Modifier.fillMaxWidth().height(55.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MediumPurple)
        ) { Text("Confirm with Google", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Color.White) }
        ReauthMethod.Password -> {
            var visible by remember { mutableStateOf(false) }
            OutlinedTextField(
                value = password,
                onValueChange = onPasswordChange,
                label = { Text("MindHaven password", color = Color.Black) },
                singleLine = true,
                visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (password.isNotEmpty()) onPassword() }),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
                textStyle = TextStyle(color = Color.Black),
                trailingIcon = {
                    IconButton(onClick = { visible = !visible }) {
                        Icon(
                            imageVector = if (visible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = if (visible) "Hide password" else "Show password",
                            tint = Color.Black
                        )
                    }
                }
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onPassword,
                enabled = password.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().height(55.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MediumPurple)
            ) { Text("Confirm", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Color.White) }
        }
        ReauthMethod.SignInAgain -> Button(
            onClick = onSignInAgain,
            modifier = Modifier.fillMaxWidth().height(55.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MediumPurple)
        ) { Text("Sign in again", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Color.White) }
    }
}
