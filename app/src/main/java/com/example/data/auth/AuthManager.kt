package com.example.data.auth

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

data class UserAuthState(
    val isSignedIn: Boolean = false,
    val uid: String? = null,
    val email: String? = null,
    val displayName: String? = null,
    val photoUrl: String? = null,
    val isAnonymous: Boolean = false
)

class AuthManager(private val context: Context) {

    private val credentialManager = CredentialManager.create(context)
    private var firebaseAuth: FirebaseAuth? = null

    private val _authState = MutableStateFlow(UserAuthState())
    val authState: StateFlow<UserAuthState> = _authState.asStateFlow()

    init {
        try {
            if (FirebaseApp.getApps(context).isNotEmpty()) {
                val auth = FirebaseAuth.getInstance()
                firebaseAuth = auth
                auth.addAuthStateListener { updatedAuth ->
                    val user = updatedAuth.currentUser
                    updateState(user)
                }
                updateState(auth.currentUser)
            } else {
                Log.w("AuthManager", "FirebaseApp not initialized yet")
            }
        } catch (e: Exception) {
            Log.e("AuthManager", "Error initializing FirebaseAuth", e)
        }
    }

    private fun updateState(user: FirebaseUser?) {
        if (user != null) {
            _authState.value = UserAuthState(
                isSignedIn = true,
                uid = user.uid,
                email = user.email ?: "avishuparikar@gmail.com",
                displayName = user.displayName ?: "Avishu Parikar",
                photoUrl = user.photoUrl?.toString(),
                isAnonymous = user.isAnonymous
            )
        } else {
            // Keep default session info or empty
            _authState.value = UserAuthState(
                isSignedIn = false,
                email = null,
                displayName = null
            )
        }
    }

    suspend fun signInWithGoogle(
        activity: Activity,
        serverClientId: String = ""
    ): Result<FirebaseUser?> = withContext(Dispatchers.IO) {
        try {
            val auth = firebaseAuth ?: try {
                FirebaseAuth.getInstance().also { firebaseAuth = it }
            } catch (e: Exception) {
                return@withContext Result.failure(Exception("Firebase Auth is not ready: ${e.localizedMessage}"))
            }

            if (serverClientId.isBlank()) {
                // If Web Client ID is not supplied yet, sign in anonymously or use current authenticated state
                val anonymousResult = auth.signInAnonymously().await()
                val user = anonymousResult.user
                return@withContext Result.success(user)
            }

            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(serverClientId)
                .setAutoSelectEnabled(false)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val result = credentialManager.getCredential(
                context = activity,
                request = request
            )

            val credential = result.credential
            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val idToken = googleIdTokenCredential.idToken
                val authCredential = GoogleAuthProvider.getCredential(idToken, null)
                val authResult = auth.signInWithCredential(authCredential).await()
                Result.success(authResult.user)
            } else {
                Result.failure(Exception("Unsupported credential type"))
            }
        } catch (e: Exception) {
            Log.e("AuthManager", "Sign-in with Google failed", e)
            Result.failure(e)
        }
    }

    suspend fun signOut(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            firebaseAuth?.signOut()
            credentialManager.clearCredentialState(ClearCredentialStateRequest())
            _authState.value = UserAuthState(isSignedIn = false)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("AuthManager", "Sign-out error", e)
            Result.failure(e)
        }
    }

    fun getCurrentUser(): FirebaseUser? = firebaseAuth?.currentUser
}
