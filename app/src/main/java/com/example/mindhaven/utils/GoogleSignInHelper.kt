package com.example.mindhaven.utils

import android.content.Context
import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.tasks.Task
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GoogleSignInHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var googleSignInClient: GoogleSignInClient? = null
    private var onSignInResult: ((Result<String>) -> Unit)? = null

    // You need to replace this with your actual Google OAuth client ID
    // Get this from Google Cloud Console > APIs & Credentials > OAuth 2.0 Client IDs
    private val webClientId = "576522713268-7uadji6icjjrl112odskn5mhoe0s1050.apps.googleusercontent.com"

    fun initializeGoogleSignIn(): GoogleSignInClient {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(webClientId)
            .requestEmail()
            .build()

        googleSignInClient = GoogleSignIn.getClient(context, gso)
        return googleSignInClient!!
    }

    fun getSignInIntent(): Intent? {
        return googleSignInClient?.signInIntent
    }

    fun handleSignInResult(data: Intent?, onResult: (Result<String>) -> Unit) {
        try {
            val task: Task<GoogleSignInAccount> = GoogleSignIn.getSignedInAccountFromIntent(data)
            val account = task.getResult(ApiException::class.java)
            val idToken = account.idToken
            
            if (idToken != null) {
                onResult(Result.success(idToken))
            } else {
                onResult(Result.failure(Exception("Failed to get ID token from Google")))
            }
        } catch (e: ApiException) {
            onResult(Result.failure(e))
        }
    }

    fun signOut() {
        googleSignInClient?.signOut()
    }

    fun revokeAccess() {
        googleSignInClient?.revokeAccess()
    }
}
