package com.project.aranya.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.project.aranya.viewmodel.AuthViewModel // Import AuthViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    navController: NavHostController,
    authViewModel: AuthViewModel = viewModel() // Get the shared AuthViewModel
) {
    val currentUser = authViewModel.getCurrentUser() // Get current user info

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Profile") },
                navigationIcon = {
                    // Provide a back button to return to the previous screen (e.g., Home)
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween // Push logout button to bottom
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) { // Group info at top
                Spacer(modifier = Modifier.height(32.dp))

                // Display User Info (Example: Email)
                Text(
                    text = "Logged In User",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(16.dp))

                if (currentUser != null) {
                    InfoRow(label = "Email:", value = currentUser.email ?: "Not available")
                    Spacer(modifier = Modifier.height(8.dp))
                    InfoRow(label = "User ID:", value = currentUser.uid) // Usually not shown to user, but for example
                    Spacer(modifier = Modifier.height(8.dp))
                    // Add more profile info here if available (e.g., display name, phone number if stored)

                } else {
                    Text("User information not available.")
                    // This case ideally shouldn't happen if the screen is protected by auth check
                }

                // Placeholder for future profile actions
                Spacer(modifier = Modifier.height(24.dp))
                // Button(onClick = { /* TODO: Navigate to Edit Profile */ }) {
                //    Text("Edit Profile")
                // }
                // Button(onClick = { /* TODO: Navigate to Change Password */ }) {
                //    Text("Change Password")
                // }
            }


            // Logout Button at the bottom
            Button(
                onClick = {
                    authViewModel.logout()
                    // Navigation back to Login is handled by the LaunchedEffect watching authState in AppNavigation
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp), // Padding from bottom edge
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error) // Use error color for logout
            ) {
                Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Log Out")
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(80.dp) // Align labels
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}