package com.project.aranya.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.PeopleAlt // Example icon for user management
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings // Example icon for settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.project.aranya.navigation.Screen // Your Navigation Screen sealed class
import com.project.aranya.ui.components.ReportListItem // Assuming you have this in a components package
import com.project.aranya.ui.theme.AranyaTheme
import com.project.aranya.viewmodel.AuthViewModel
import com.project.aranya.viewmodel.AdminComplaintListViewModel // ViewModel for this screen
import com.project.aranya.viewmodel.ReportListState // Reusing state from user's report list

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDashboardScreen(
    navController: NavHostController,
    authViewModel: AuthViewModel = viewModel(), // For logout action
    adminComplaintListViewModel: AdminComplaintListViewModel = viewModel() // ViewModel for fetching all complaints
) {
    // Observe the state of fetching all reports
    val allReportsState by adminComplaintListViewModel.allReportsState.collectAsState()
    val currentUser = authViewModel.getCurrentUser() // For displaying admin email if desired

    AranyaTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Admin Dashboard") },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    actions = {
                        IconButton(onClick = { adminComplaintListViewModel.refreshAllComplaints() }) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Refresh All Complaints")
                        }
                        IconButton(onClick = {
                            authViewModel.logout()
                            // Navigation to login screen is handled by AppNavigationHost observing authState
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Logout Admin")
                        }
                    }
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 8.dp) // Use horizontal padding for the list container
            ) {
                // Admin Welcome Message
                currentUser?.email?.let {
                    Text(
                        text = "Welcome, Admin (${it})",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier
                            .padding(vertical = 16.dp, horizontal = 8.dp) // Add horizontal padding here too
                            .fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                }

                // Section for other admin actions (e.g., user management, settings) - Future Expansion
                // Row(
                //     modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp, horizontal = 8.dp),
                //     horizontalArrangement = Arrangement.SpaceEvenly
                // ) {
                //     Button(onClick = { /* TODO: Navigate to User Management */ }) {
                //         Icon(Icons.Filled.PeopleAlt, contentDescription = "User Management")
                //         Spacer(Modifier.width(4.dp))
                //         Text("Users")
                //     }
                //     Button(onClick = { /* TODO: Navigate to Admin Settings */ }) {
                //         Icon(Icons.Filled.Settings, contentDescription = "Settings")
                //         Spacer(Modifier.width(4.dp))
                //         Text("Settings")
                //     }
                // }
                // Divider(modifier = Modifier.padding(vertical = 8.dp))

                Text(
                    text = "All Submitted Complaints",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(start = 8.dp, top = 8.dp, bottom = 8.dp)
                )

                // Display the list of all complaints or the current state
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) { // Make the list take remaining space
                    when (val state = allReportsState) {
                        is ReportListState.Loading -> {
                            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                        }
                        is ReportListState.Success -> {
                            if (state.reports.isEmpty()) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text("No complaints have been submitted to the system yet.")
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(bottom = 16.dp) // Padding at the end of the list
                                ) {
                                    items(state.reports, key = { report -> report.id }) { report ->
                                        ReportListItem( // Reusing the ReportListItem from user's view
                                            report = report,
                                            onItemClick = { clickedReport ->
                                                // Admins navigate to the same ReportDetailScreen.
                                                // The ReportDetailViewModel will check if the viewer is an admin
                                                // and ReportDetailScreen will conditionally show admin actions.
                                                if (clickedReport.id.isNotBlank()) {
                                                    navController.navigate(Screen.ReportDetail.createRoute(clickedReport.id))
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                        is ReportListState.Empty -> { // Explicitly handle empty state if different from Success with empty list
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("No complaints found in the system.")
                            }
                        }
                        is ReportListState.Error -> {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        "Error fetching complaints: ${state.message}",
                                        color = MaterialTheme.colorScheme.error,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(horizontal = 16.dp)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Button(onClick = { adminComplaintListViewModel.refreshAllComplaints() }) {
                                        Text("Retry Fetching Complaints")
                                    }
                                }
                            }
                        }
                    }
                } // End Box for list content
            } // End Main Column
        } // End Scaffold Padding lambda
    } // End AranyaTheme
}

@Preview(showBackground = true)
@Composable
fun AdminDashboardScreenPreview() {
    AranyaTheme {
        // You would need to provide mock ViewModels for a complete preview
        // or simplify the composable for preview purposes.
        AdminDashboardScreen(
            navController = rememberNavController(),
            // authViewModel = // provide a mock or default instance,
            // adminComplaintListViewModel = // provide a mock or default instance
        )
    }
}