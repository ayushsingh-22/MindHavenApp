package com.example.mindhaven.data.auth

import android.content.Context
import android.content.Intent
import com.example.mindhaven.R
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions

/** Lets the repository clear the Google account selection on logout without knowing about Android. */
fun interface GoogleSignOutHandle {
    fun signOut()
}

/** Source of the Google Sign-In intent (lets the ViewModel stay free of Android Context). */
fun interface GoogleSignInIntentSource {
    fun signInIntent(): Intent
}

/**
 * The single place that builds the Google Sign-In client. Google Sign-In only yields a GOOGLE ID token, which is used to
 * sign in to Firebase; the backend exchange always uses the FIREBASE ID token (FirebaseAuthGateway.idToken).
 *
 * Holds the APPLICATION context only (never a screen), so it cannot leak one.
 */
class GoogleSignInProvider(context: Context) : GoogleSignOutHandle, GoogleSignInIntentSource {
    private val appContext: Context = context.applicationContext

    private val client: GoogleSignInClient by lazy {
        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(appContext.getString(R.string.default_web_client_id)) // generated from google-services.json
            .requestEmail()
            .build()
        GoogleSignIn.getClient(appContext, options)
    }

    override fun signInIntent(): Intent = client.signInIntent

    override fun signOut() {
        client.signOut() // forget the chosen Google account so the next login shows the account picker
    }
}
