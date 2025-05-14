package com.project.aranya.ui.screen

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.project.aranya.navigation.Screen
import com.project.aranya.ui.components.ReportListItem // Import your list item
import com.project.aranya.viewmodel.ReportListState
import com.project.aranya.viewmodel.*
import com.project.aranya.ui.screen.*
import com.project.aranya.data.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyReportsScreen(
    navController: NavHostController,
    viewModel: ReportListViewModel = viewModel()
) {
    val reportListState by viewModel.reportListState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Submitted Reports") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refreshReports() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh Reports")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (val state = reportListState) {
                is ReportListState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                is ReportListState.Success -> {
                    if (state.reports.isEmpty()) { // Handle case where success returns empty list
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("You haven't submitted any reports yet.")
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            items(state.reports, key = { report -> report.id }) { reportData -> // reportData is ComplaintData
                                ReportListItem(
                                    report = reportData,
                                    onItemClick = { clickedComplaintData ->
                                        val reportId = clickedComplaintData.id
                                        if (reportId.isNotBlank()) {
                                            navController.navigate(Screen.ReportDetail.createRoute(reportId))
                                        } else {
                                            // Log an error or show a toast if the ID is unexpectedly blank
                                            Log.e(
                                                "MyReportsScreen",
                                                "Clicked report has a blank ID. Cannot navigate. Report: $clickedComplaintData"
                                            )
                                        }
                                        }
                                )
                            }
                        }
                    }
                }
                is ReportListState.Empty -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("You haven't submitted any reports yet.")
                    }
                }
                is ReportListState.Error -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Error: ${state.message}", color = MaterialTheme.colorScheme.error)
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(onClick = { viewModel.refreshReports() }) {
                                Text("Retry")
                            }
                        }
                    }
                }
            }
        }
    }
}
