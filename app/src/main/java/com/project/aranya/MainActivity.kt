package com.project.aranya

import android.app.Activity // For ActivityResult
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState // If using LiveData for AuthViewModel
// import androidx.compose.runtime.collectAsState // If using StateFlow for AuthViewModel
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.google.android.gms.auth.api.identity.Identity // For SignInClient
import com.google.android.gms.common.api.ApiException // For handling Google Sign-In errors
import com.project.aranya.navigation.Screen
import com.project.aranya.ui.screen.*
import com.project.aranya.ui.theme.AranyaTheme // Your theme
import com.project.aranya.viewmodel.AuthViewModel
import com.project.aranya.viewmodel.AuthState
// Import ReportDetailViewModel if you need to pass it with a factory (not needed if using Hilt or simple viewModel())
// import com.project.aranya.viewmodel.ReportDetailViewModel
// import com.project.aranya.viewmodel.ReportDetailViewModelFactory
// import androidx.compose.ui.platform.LocalSavedStateRegistryOwner
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AranyaTheme {
                AppNavigationHost()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavigationHost(
    authViewModel: AuthViewModel = viewModel()
) {
    val navController = rememberNavController()
    val authState by authViewModel.authState.observeAsState(initial = AuthState.Idle)
    // Observe the admin status from AuthViewModel (assuming AuthViewModel now exposes this)
    val isAdminUser by authViewModel.isAdminUser.observeAsState(false) // Default to false

    // --- Snackbar Setup ---
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    // Observe Login Success Event from AuthViewModel to show Snackbar
    LaunchedEffect(authViewModel) {
        authViewModel.loginSuccessEvent.collectLatest { message ->
            coroutineScope.launch {
                snackbarHostState.showSnackbar(
                    message = message,
                    duration = SnackbarDuration.Short
                )
            }
        }
    }
    // --- End Snackbar Setup ---

    // --- Role Checking and Initial Navigation Logic ---
    LaunchedEffect(authState, isAdminUser, navController) {
        val currentRoute = navController.currentBackStackEntry?.destination?.route
        Log.d("AppNavigationHost", "EFFECT: AuthState: $authState, isAdminUser: $isAdminUser, CurrentRoute: $currentRoute")

        if (authState is AuthState.Authenticated) {
            // isAdminUser is now directly observed from AuthViewModel
            // The AuthViewModel is responsible for determining this after any successful login (regular or admin attempt)
            navigateToDashboard(navController, isAdminUser, authState)
        } else if (authState is AuthState.Unauthenticated) {
            // If user becomes unauthenticated (e.g., logout), navigate back to Login
            if (currentRoute != Screen.Login.route) {
                Log.d("AppNavigationHost", "User logged out or unauthenticated, navigating to Login.")
                navController.navigate(Screen.Login.route) {
                    popUpTo(navController.graph.id) { inclusive = true } // Clear entire back stack
                    launchSingleTop = true
                }
            }
        }
    }
    // --- End Role Checking Logic ---


    // --- Google Sign-In Setup (managed within AppNavigationHost) ---
    val context = LocalContext.current
    val oneTapClient = remember(context) { Identity.getSignInClient(context) } // Re-remember if context changes (though unlikely here)

    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result: ActivityResult ->
        if (result.resultCode == Activity.RESULT_OK) {
            try {
                val credential = oneTapClient.getSignInCredentialFromIntent(result.data)
                val idToken = credential.googleIdToken
                if (idToken != null) {
                    Log.d("AppNavigationHost", "Google Sign-In successful, obtained ID token. Signing into Firebase...")
                    authViewModel.signInWithGoogleCredential(idToken) // This will also trigger admin role check in ViewModel
                } else {
                    Log.e("AppNavigationHost", "Google Sign-In: ID token is null.")
                    coroutineScope.launch { snackbarHostState.showSnackbar("Google Sign-In failed: No token.") }
                }
            } catch (e: ApiException) {
                Log.e("AppNavigationHost", "Google Sign-In result error: Code ${e.statusCode}", e)
                coroutineScope.launch { snackbarHostState.showSnackbar("Google Sign-In failed: ${e.localizedMessage ?: "Client error"} (Code: ${e.statusCode})") }
            } catch (e: Exception) {
                Log.e("AppNavigationHost", "Google Sign-In result processing error", e)
                coroutineScope.launch { snackbarHostState.showSnackbar("An error occurred during Google Sign-In.") }
            }
        } else {
            Log.w("AppNavigationHost", "Google Sign-In cancelled or failed. Result code: ${result.resultCode}")
        }
    }
    // --- End Google Sign-In Setup ---


    // Box contains the main app content (NavHost) and the SnackbarHost aligned at the top
    Box(modifier = Modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            NavHost(
                navController = navController,
                startDestination = Screen.Login.route, // Initial route
                modifier = Modifier.fillMaxSize()
            ) {
                // --- Authentication Routes ---
                composable(Screen.Login.route) {
                    if (authState is AuthState.Authenticated) {
                        // Let the LaunchedEffect handle redirection to dashboard
                        // To prevent flicker, you could show a loading or empty composable here.
                        // For example: Box(modifier = Modifier.fillMaxSize()){ CircularProgressIndicator(Modifier.align(Alignment.Center)) }
                    } else {
                        LoginScreen(
                            onLoginClick = { email, password -> authViewModel.login(email, password) },
                            onGoogleSignInClick = {
                                Log.d("AppNavigationHost", "Google Sign-In button clicked, initiating One Tap flow...")
                                authViewModel.beginGoogleSignIn(
                                    oneTapClient = oneTapClient,
                                    launcher = googleSignInLauncher
                                )
                            },
                            onNavigateToSignUp = { navController.navigate(Screen.SignUp.route) },
                            onAdminLoginClick = { email, password ->
                                Log.d("AppNavigationHost", "Admin login attempt from UI. Email: $email") // Check this log
                                authViewModel.adminLogin(email, password)
                            },
                            isLoading = authState is AuthState.Loading,
                            error = (authState as? AuthState.Error)?.message
                        )
                    }
                }
                composable(Screen.SignUp.route) {
                    SignUpScreen(
                        onSignUpClick = { email, password -> authViewModel.signUp(email, password) },
                        onNavigateToLogin = { navController.popBackStack() },
                        isLoading = authState is AuthState.Loading,
                        error = (authState as? AuthState.Error)?.message
                    )
                }

                // --- User Routes (Protected by RequireAuth) ---
                composable(Screen.Home.route) {
                    RequireAuth(authState = authState, navController = navController) {
                        HomeScreen(navController = navController, authViewModel = authViewModel)
                    }
                }
                composable(Screen.SubmitComplaint.route) {
                    RequireAuth(authState = authState, navController = navController) {
                        SubmitComplaintScreen(navController = navController)
                    }
                }
                composable(Screen.MyReports.route) {
                    RequireAuth(authState = authState, navController = navController) {
                        MyReportsScreen(navController = navController)
                    }
                }
                composable(
                    route = Screen.ReportDetail.route, // Format: "report_detail/{reportId}"
                    arguments = listOf(navArgument("reportId") { type = NavType.StringType })
                ) { backStackEntry ->
                    RequireAuth(authState = authState, navController = navController) { _ -> // User from RequireAuth
                        val reportId = backStackEntry.arguments?.getString("reportId")
                        if (reportId.isNullOrBlank()) {
                            Log.e("AppNavigationHost", "ReportDetail: reportId is missing/blank. Navigating back.")
                            // Ensure this LaunchedEffect doesn't cause issues if called during recomposition
                            LaunchedEffect(Unit) { navController.popBackStack() }
                        } else {
                            // ReportDetailViewModel is expected to get 'reportId' from SavedStateHandle
                            ReportDetailScreen(navController = navController)
                        }
                    }
                }
                composable(Screen.Profile.route) {
                    RequireAuth(authState = authState, navController = navController) {
                        ProfileScreen(navController = navController, authViewModel = authViewModel)
                    }
                }

                // --- Admin Route (Protected by RequireAuth and isAdminUser check) ---
                composable(Screen.AdminDashboard.route) {
                    RequireAuth(authState = authState, navController = navController) { _ -> // User from RequireAuth
                        if (isAdminUser) { // Use the observed isAdminUser state from AuthViewModel
                            AdminDashboardScreen(navController = navController, authViewModel = authViewModel)
                        } else {
                            // This case should ideally be prevented by navigateToDashboard redirecting
                            // non-admins to Screen.Home. This is a fallback.
                            Log.w("AppNavigationHost", "Non-admin user somehow reached AdminDashboard route. Redirecting to Home.")
                            LaunchedEffect(Unit) {
                                navController.navigate(Screen.Home.route) {
                                    popUpTo(Screen.AdminDashboard.route) { inclusive = true }
                                    launchSingleTop = true
                                }
                            }
                        }
                    }
                }
            } // End NavHost
        } // End Surface (main content)

        // SnackbarHost aligned to the top center of the Box
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 16.dp, start = 16.dp, end = 16.dp) // Padding for Snackbar visibility
        ) { data ->
            Snackbar(
                modifier = Modifier.padding(horizontal = 8.dp), // Inner padding for snackbar content itself
                snackbarData = data
            )
        }
    } // End Box (for NavHost + SnackbarHost)
}

/**
 * Helper composable to ensure user is authenticated before accessing certain routes.
 * If not authenticated, navigates to the Login screen.
 * It also provides the authenticated FirebaseUser object to its content.
 */
@Composable
private fun RequireAuth(
    authState: AuthState,
    navController: NavHostController,
    content: @Composable (user: com.google.firebase.auth.FirebaseUser) -> Unit
) {
    when (authState) {
        is AuthState.Authenticated -> {
            content(authState.user) // User is authenticated, show the protected content
        }
        is AuthState.Loading, AuthState.Idle -> {
            // Show a loading indicator while auth state is being determined
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        }
        is AuthState.Unauthenticated, is AuthState.Error -> {
            // User is not authenticated or an error occurred, redirect to Login
            LaunchedEffect(authState) { // Key on authState to re-trigger if it changes
                Log.d("RequireAuth", "User not authenticated or error in auth state ($authState), navigating to Login.")
                navController.navigate(Screen.Login.route) {
                    popUpTo(navController.graph.id) { inclusive = true } // Clear back stack
                    launchSingleTop = true // Avoid multiple login instances
                }
            }
        }
    }
}

/**
 * Helper function for navigating to the correct dashboard after login/auth state check.
 */
private fun navigateToDashboard(
    navController: NavHostController,
    isAdmin: Boolean, // This should be the reliably determined admin status
    authState: AuthState // Pass current authState for safety check
) {
    // Only proceed if actually authenticated
    if (authState !is AuthState.Authenticated) {
        Log.d("AppNavigationHost", "navigateToDashboard: Called but user is not truly authenticated ($authState). Aborting.")
        return
    }

    val destination = if (isAdmin) Screen.AdminDashboard.route else Screen.Home.route
    val currentRoute = navController.currentBackStackEntry?.destination?.route

    if (currentRoute != destination) {
        // Navigate if coming from Login/SignUp or if currentRoute is null (very initial app launch before any nav)
        if (currentRoute == Screen.Login.route || currentRoute == Screen.SignUp.route || currentRoute == null) {
            Log.i("AppNavigationHost", "navigateToDashboard: Navigating from '$currentRoute' to '$destination'. IsAdmin: $isAdmin")
            navController.navigate(destination) {
                popUpTo(navController.graph.startDestinationId) { inclusive = true } // Pop up to the start of the graph (Login)
                launchSingleTop = true
            }
        } else {
            Log.d("AppNavigationHost", "navigateToDashboard: Current route ('$currentRoute') is not Login/SignUp or null. Automatic navigation to '$destination' skipped.")
        }
    } else {
        Log.d("AppNavigationHost", "navigateToDashboard: Already on the correct destination ('$destination'). No navigation needed.")
    }
}