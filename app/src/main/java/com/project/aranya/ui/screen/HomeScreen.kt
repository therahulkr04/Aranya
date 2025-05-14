package com.project.aranya.ui.screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AddLocationAlt
import androidx.compose.material.icons.filled.NaturePeople
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource // If using drawable resource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.project.aranya.R // Assuming you might add drawable background
import com.project.aranya.navigation.Screen
import com.project.aranya.ui.theme.AranyaTheme
import com.project.aranya.viewmodel.AuthViewModel
// Need to import Icons.Filled.MenuBook if using the placeholder card
import androidx.compose.material.icons.filled.MenuBook

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    navController: NavHostController,
    authViewModel: AuthViewModel = viewModel()
) {
    val user = authViewModel.getCurrentUser()
    val scrollState = rememberScrollState()

    AranyaTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Wildlife Reporting") }, // Slightly different title
                    // Use custom colors defined in Theme.kt if needed
                    // colors = TopAppBarDefaults.topAppBarColors(
                    //    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    //    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    // ),
                    actions = {
                        IconButton(onClick = { navController.navigate(Screen.Profile.route) }) {
                            Icon(Icons.Filled.AccountCircle, contentDescription = "Profile")
                        }
                        IconButton(onClick = { authViewModel.logout() }) {
                            Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Logout")
                        }
                    }
                )
            }
        ) { paddingValues ->
            // Optional: Background Image (use subtly)
            // Box(modifier = Modifier.fillMaxSize()) {
            //    Image(
            //        painter = painterResource(id = R.drawable.ic_forest_background), // Replace with your drawable
            //        contentDescription = null,
            //        modifier = Modifier.fillMaxSize(),
            //        contentScale = ContentScale.Crop,
            //        alpha = 0.1f // Make it subtle
            //    )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(scrollState) // Allow scrolling if content overflows
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Welcome Message or Header Image
                Icon(
                    imageVector = Icons.Filled.NaturePeople, // Example theme icon
                    contentDescription = "Environment Icon",
                    modifier = Modifier.size(80.dp).padding(bottom = 16.dp),
                    tint = MaterialTheme.colorScheme.primary // Use theme color
                )
                // Or replace Icon with:
                // Image(painter = painterResource(id = R.drawable.your_logo_or_graphic), ...)

                Text(
                    text = "Protecting Our Natural Heritage",
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                user?.email?.let {
                    Text(
                        text = "Welcome, ${it}!", // More personal
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 24.dp)
                    )
                }

                ActionCard(
                    title = "Report an Incident",
                    description = "Submit a new report about poaching, injured animals, or habitat issues.",
                    icon = Icons.Filled.AddLocationAlt, // Icon related to adding report/location
                    onClick = { navController.navigate(Screen.SubmitComplaint.route) }
                )

                Spacer(modifier = Modifier.height(16.dp))

                ActionCard(
                    title = "View My Reports",
                    description = "Check the status and details of your submitted reports.",
                    icon = Icons.AutoMirrored.Filled.ListAlt, // Icon for list/reports
                    onClick = { navController.navigate(Screen.MyReports.route) }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Example Placeholder Card for future feature
                /*ActionCard(
                    title = "Wildlife Guide (Coming Soon)",
                    description = "Learn about local species and habitats.",
                    icon = Icons.Filled.MenuBook, // Example Icon for book/guide
                    onClick = { /* TODO */ }
                )*/

                Spacer(modifier = Modifier.weight(1f)) // Push footer down if content is short

                // Footer text (Optional)
                Text(
                    text = "Your reports make a difference. Thank you!",
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)
                )
            } // End Column
            // } // End Box (if using background image)
        } // End Scaffold Padding lambda
    } // End Scaffold
}

/**
 * Reusable Composable for the action cards on the home screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionCard(
    title: String,
    description: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        // colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant) // Example styling
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null, // Decorative
                modifier = Modifier.size(48.dp).padding(end = 16.dp),
                tint = MaterialTheme.colorScheme.primary // Use theme color for icon
            )
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}
