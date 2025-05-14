// Located in: app/src/main/java/com/project/aranya/viewmodel/
package com.project.aranya.viewmodel

import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.auth.api.identity.BeginSignInRequest
import com.google.android.gms.auth.api.identity.SignInClient
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.tasks.Task // For Task type
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GetTokenResult // For GetTokenResult type (used in commented out custom claims check)
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore // For Firestore role check
// kotlinx.coroutines.tasks.await is still useful for other Firebase tasks
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

// Sealed class representing different authentication states
sealed class AuthState {
    object Idle : AuthState()
    object Loading : AuthState()
    data class Authenticated(val user: FirebaseUser) : AuthState()
    data class Error(val message: String) : AuthState()
    object Unauthenticated : AuthState()
}

class AuthViewModel : ViewModel() {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance() // Firestore instance for role check

    private val _authState = MutableLiveData<AuthState>()
    val authState: LiveData<AuthState> = _authState

    private val _isAdminUser = MutableLiveData<Boolean>(false) // True if current authenticated user is an admin
    val isAdminUser: LiveData<Boolean> = _isAdminUser

    private val _loginSuccessEvent = MutableSharedFlow<String>(
        replay = 0, extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val loginSuccessEvent: SharedFlow<String> = _loginSuccessEvent.asSharedFlow()

    // **CRITICAL: REPLACE WITH YOUR ACTUAL WEB CLIENT ID for Google Sign-In**
    private val googleSignInWebClientId = "229648396736-bn4evm6vp2auje0q3cl18pcrefi0jll5.apps.googleusercontent.com"

    init {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            Log.d("AuthVM", "Init: User already authenticated (${currentUser.uid}). Checking role from Firestore.")
            // Check admin role from Firestore on init.
            // AuthState will be set to Authenticated inside checkRoleFromFirestore.
            checkRoleFromFirestore(currentUser, isInitialCheck = true) { /* Optional onComplete */ }
        } else {
            Log.d("AuthVM", "Init: No authenticated user.")
            _authState.value = AuthState.Unauthenticated
            _isAdminUser.value = false
        }
    }

    fun getCurrentUser(): FirebaseUser? = auth.currentUser

    fun signUp(email: String, password: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            _isAdminUser.value = false // New users are not admins by default
            try {
                auth.createUserWithEmailAndPassword(email, password).await()
                val user = auth.currentUser
                if (user != null) {
                    Log.i("AuthVM", "Sign up successful for: ${user.email}")
                    _authState.value = AuthState.Authenticated(user)
                    // Optionally create a user document in Firestore here with isAdminRole: false
                    // db.collection("users").document(user.uid).set(mapOf("email" to user.email, "isAdminRole" to false, "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()))
                } else {
                    Log.e("AuthVM", "Sign up failed: User data null after creation.")
                    _authState.value = AuthState.Error("Sign up failed: Could not retrieve user details.")
                }
            } catch (e: Exception) {
                Log.e("AuthVM", "Sign Up Error: ${e.message}", e)
                _authState.value = AuthState.Error(e.message ?: "An unknown sign-up error occurred.")
            }
        }
    }

    fun login(email: String, password: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            _isAdminUser.value = false // Assume regular user until role is verified from Firestore
            try {
                val result = auth.signInWithEmailAndPassword(email, password).await()
                val user = result.user
                if (user != null) {
                    Log.i("AuthVM", "Regular Login: Firebase Auth successful for ${user.email}. Checking role.")
                    checkRoleFromFirestore(user) { isAdmin ->
                        // This callback runs after the Firestore role check is complete.
                        // _authState and _isAdminUser are set within checkRoleFromFirestore.
                        val userName = user.displayName ?: user.email ?: "User"
                        viewModelScope.launch { // Ensure event emission is from main context
                            if (isAdmin) {
                                _loginSuccessEvent.emit("Admin (via regular login) logged in as $userName!")
                            } else {
                                _loginSuccessEvent.emit("Logged in successfully as $userName!")
                            }
                        }
                    }
                } else {
                    Log.e("AuthVM", "Login failed: User data null after sign-in.")
                    _authState.value = AuthState.Error("Login failed: Could not retrieve user details.")
                }
            } catch (e: Exception) {
                Log.e("AuthVM", "Login Error: ${e.message}", e)
                _authState.value = AuthState.Error(e.message ?: "An unknown login error occurred.")
            }
        }
    }

    fun adminLogin(email: String, password: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            try {
                val authResult = auth.signInWithEmailAndPassword(email, password).await()
                val user = authResult.user
                if (user != null) {
                    Log.i("AuthVM", "Admin Login Attempt: Firebase Auth successful for ${user.email}. Checking role.")
                    // checkRoleFromFirestore will handle setting _isAdminUser, _authState,
                    // and signing out if not an admin during an adminLoginAttempt.
                    checkRoleFromFirestore(user, isAdminLoginAttempt = true) { isAdmin ->
                        if (isAdmin) {
                            val userName = user.displayName ?: user.email ?: "Admin"
                            viewModelScope.launch { // Ensure event emission from main context
                                _loginSuccessEvent.emit("Admin login successful as $userName!")
                            }
                        }
                        // If not admin, checkRoleFromFirestore already handled AuthState.Error and sign out.
                    }
                } else {
                    Log.e("AuthVM", "Admin login failed: User data null after sign-in.")
                    _authState.value = AuthState.Error("Admin login failed: Could not retrieve user details.")
                }
            } catch (e: Exception) {
                Log.e("AuthVM", "Admin Login Auth Error: ${e.message}", e)
                _authState.value = AuthState.Error("Admin login failed: ${e.message ?: "Invalid credentials or user not found."}")
            }
        }
    }

    /**
     * Checks the user's role by reading the 'isAdminRole' field from their document
     * in the 'users' collection in Firestore.
     *
     * @param user The FirebaseUser whose role is to be checked.
     * @param isAdminLoginAttempt If true, and the user is not an admin, they will be signed out.
     * @param isInitialCheck True if called from init block, to manage AuthState setting.
     * @param onComplete Optional callback invoked with the determined admin status.
     */
    private fun checkRoleFromFirestore(
        user: FirebaseUser,
        isAdminLoginAttempt: Boolean = false,
        isInitialCheck: Boolean = false,
        onComplete: ((isAdmin: Boolean) -> Unit)? = null
    ) {
        Log.d("AuthVM_FirestoreRole", "Checking Firestore role for ${user.uid}. AdminLoginAttempt: $isAdminLoginAttempt, InitialCheck: $isInitialCheck")
        db.collection("users").document(user.uid).get()
            .addOnCompleteListener { task ->
                viewModelScope.launch { // Ensure LiveData/StateFlow updates are on the main thread
                    var determinedIsAdmin = false
                    if (task.isSuccessful) {
                        val document = task.result
                        if (document != null && document.exists()) {
                            determinedIsAdmin = document.getBoolean("isAdminRole") == true
                            Log.i("AuthVM_FirestoreRole", "User ${user.email} isAdminRole from Firestore: $determinedIsAdmin")
                        } else {
                            // No user document in 'users' collection, or isAdminRole field missing.
                            Log.w("AuthVM_FirestoreRole", "User document for ${user.uid} not found or isAdminRole field missing in Firestore. Assuming not admin.")
                            determinedIsAdmin = false
                        }
                    } else {
                        Log.e("AuthVM_FirestoreRole", "Error fetching user role document from Firestore for ${user.uid}", task.exception)
                        determinedIsAdmin = false // Default to not admin on error fetching role
                    }

                    _isAdminUser.value = determinedIsAdmin

                    if (isAdminLoginAttempt && !determinedIsAdmin) {
                        Log.w("AuthVM_FirestoreRole", "Admin login attempt by non-admin ${user.email}. Signing out.")
                        auth.signOut() // Sign out user if they tried admin login but aren't an admin
                        _authState.value = AuthState.Error("Access Denied: Not an authorized administrator.")
                    } else {
                        // For regular login, Google Sign-In, init check, or if admin role IS confirmed:
                        // Ensure auth state is set to Authenticated.
                        // This is important if this function is the one responsible for setting it after initial Firebase Auth.
                        _authState.value = AuthState.Authenticated(user)
                    }
                    onComplete?.invoke(determinedIsAdmin)
                }
            }
    }

    // --- Google Sign-In ---
    fun beginGoogleSignIn(oneTapClient: SignInClient, launcher: ActivityResultLauncher<IntentSenderRequest>) {
        _authState.value = AuthState.Loading
        _isAdminUser.value = false // Reset for Google Sign-In flow
        Log.d("AuthVM", "beginGoogleSignIn: Initiating Google Sign-In flow.")
        if (googleSignInWebClientId == "YOUR_WEB_CLIENT_ID.apps.googleusercontent.com" || googleSignInWebClientId.isBlank()) {
            Log.e("AuthVM", "CRITICAL: googleSignInWebClientId is not configured in AuthViewModel!")
            _authState.value = AuthState.Error("Google Sign-In is not configured correctly in the app (Web Client ID missing).")
            return
        }
        val signInRequest = BeginSignInRequest.builder()
            .setGoogleIdTokenRequestOptions(
                BeginSignInRequest.GoogleIdTokenRequestOptions.builder()
                    .setSupported(true).setServerClientId(googleSignInWebClientId)
                    .setFilterByAuthorizedAccounts(false).build()
            ).setAutoSelectEnabled(false).build()

        oneTapClient.beginSignIn(signInRequest).addOnSuccessListener { result ->
            try {
                launcher.launch(IntentSenderRequest.Builder(result.pendingIntent.intentSender).build())
            } catch (e: Exception) {
                Log.e("AuthVM", "Google Sign-In: Could not start intent: ${e.localizedMessage}", e)
                _authState.value = AuthState.Error("Google Sign-In start failed: ${e.localizedMessage}")
            }
        }.addOnFailureListener { e ->
            Log.e("AuthVM", "Google Sign-In: beginSignIn API call failed: ${e.localizedMessage}", e)
            val errorMsg = if (e is ApiException) "Google Sign-In failed (Code: ${e.statusCode}). Check app config." else e.localizedMessage
            _authState.value = AuthState.Error(errorMsg ?: "Google Sign-In setup error.")
        }
    }

    fun signInWithGoogleCredential(idToken: String?) {
        if (idToken == null) {
            _authState.value = AuthState.Error("Google Sign-In: ID token from Google was null.")
            return
        }
        Log.d("AuthVM", "signInWithGoogleCredential: Attempting Firebase sign-in with Google ID token.")
        viewModelScope.launch {
            // _authState is already Loading from beginGoogleSignIn
            try {
                val credential = GoogleAuthProvider.getCredential(idToken, null)
                val result = auth.signInWithCredential(credential).await() // .await() here is generally fine
                val user = result.user
                if (user != null) {
                    Log.i("AuthVM", "Firebase authentication with Google successful for: ${user.email}. Checking role.")
                    // After successful Firebase auth with Google, check their admin role from Firestore
                    checkRoleFromFirestore(user) { isAdmin ->
                        val userName = user.displayName ?: user.email ?: "User"
                        viewModelScope.launch { // Ensure event emission from main context
                            if (isAdmin) {
                                _loginSuccessEvent.emit("Admin (via Google) logged in as $userName!")
                            } else {
                                _loginSuccessEvent.emit("Logged in successfully with Google as $userName!")
                            }
                        }
                        // _authState is set to Authenticated within checkRoleFromFirestore.
                    }
                } else {
                    Log.e("AuthVM", "Google Sign-In: Firebase user null after successful credential sign-in.")
                    _authState.value = AuthState.Error("Google Sign-In failed: Could not retrieve Firebase user details.")
                }
            } catch (e: Exception) {
                Log.e("AuthVM", "signInWithGoogleCredential: Firebase Google Auth Error: ${e.message}", e)
                _authState.value = AuthState.Error(e.message ?: "Firebase authentication with Google failed.")
            }
        }
    }

    fun logout(oneTapClient: SignInClient? = null) {
        viewModelScope.launch {
            Log.d("AuthVM", "Logout initiated.")
            auth.signOut()
            oneTapClient?.signOut()?.addOnCompleteListener { task ->
                if (task.isSuccessful) Log.i("AuthVM", "Google One Tap sign out successful.")
                else Log.w("AuthVM", "Google One Tap sign out failed.", task.exception)
            }
            _isAdminUser.value = false // Reset admin status
            _authState.value = AuthState.Unauthenticated
            Log.i("AuthVM", "User logged out.")
        }
    }
}