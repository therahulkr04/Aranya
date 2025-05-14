package com.project.aranya.ui.screen

import android.content.Context // For FileDisplayItem
import android.content.Intent // For FileDisplayItem
import android.net.Uri // For FileDisplayItem
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable // If DetailItem or other parts are clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.* // For various icons like EditNote, Save, etc.
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import coil.compose.AsyncImage // For displaying Cloudinary images
import com.project.aranya.data.CloudinaryFileReference // For attached files
import com.project.aranya.data.CloudinaryFileType
import com.project.aranya.data.ComplaintCategory
import com.project.aranya.data.ComplaintData
import com.project.aranya.data.ContactPreference
import com.project.aranya.data.SeverityLevel
import com.project.aranya.navigation.Screen // Your navigation sealed class
import com.project.aranya.ui.theme.AranyaTheme
import com.project.aranya.viewmodel.ReportDetailState
import com.project.aranya.viewmodel.ReportDetailViewModel
import com.project.aranya.viewmodel.UpdateReportState
import java.text.SimpleDateFormat
import java.util.*

// List of statuses an admin can set (move to a constants file or ViewModel if preferred)
val adminComplaintStatuses = listOf(
    "Pending",
    "In Progress",
    "Needs More Info",
    "Resolved",
    "Rejected"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportDetailScreen(
    navController: NavHostController,
    // Assuming ReportDetailViewModel gets reportId from SavedStateHandle (via Hilt or Factory)
    viewModel: ReportDetailViewModel = viewModel()
) {
    val detailState by viewModel.reportDetailState.collectAsState()
    val updateState by viewModel.updateReportState.collectAsState()
    val isCurrentUserAdmin by viewModel.isCurrentUserAdmin.collectAsState() // From ViewModel

    val context = LocalContext.current

    // Local state for admin input fields, initialized/updated when report data (detailState) changes.
    var currentSelectedStatus by remember { mutableStateOf("") }
    var currentAdminRemarksInput by remember { mutableStateOf("") }
    var statusDropdownExpanded by remember { mutableStateOf(false) }

    // Update local UI state when the fetched report data changes (e.g., on initial load or refresh)
    LaunchedEffect(detailState) {
        if (detailState is ReportDetailState.Success) {
            val report = (detailState as ReportDetailState.Success).report
            currentSelectedStatus = report.status.ifBlank { adminComplaintStatuses.firstOrNull() ?: "Pending" }
            currentAdminRemarksInput = report.adminRemarks
            Log.d("ReportDetailScreen", "Report data loaded. Initial status for UI: $currentSelectedStatus")
        }
    }

    // Handle feedback from the update operation (e.g., show toasts)
    LaunchedEffect(updateState) {
        when (val state = updateState) {
            is UpdateReportState.Success -> {
                Toast.makeText(context, "Report updated successfully!", Toast.LENGTH_SHORT).show()
                viewModel.resetUpdateState() // Reset state in ViewModel after showing message
            }
            is UpdateReportState.Error -> {
                Toast.makeText(context, "Update failed: ${state.message}", Toast.LENGTH_LONG).show()
                viewModel.resetUpdateState()
            }
            else -> { /* Idle, Loading states are handled by button's enabled/indicator state */ }
        }
    }

    AranyaTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Report Details") },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.refreshReportDetails() }) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Refresh Details")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant // Example color
                    )
                )
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                when (val state = detailState) {
                    is ReportDetailState.Loading -> {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }
                    is ReportDetailState.Success -> {
                        ReportContentViewInternal( // Pass all necessary states and callbacks
                            report = state.report,
                            isUserAdmin = isCurrentUserAdmin,
                            currentStatusSelection = currentSelectedStatus,
                            onStatusSelected = { newStatus -> currentSelectedStatus = newStatus },
                            currentAdminRemarks = currentAdminRemarksInput,
                            onAdminRemarksChanged = { newRemarks -> currentAdminRemarksInput = newRemarks },
                            onSaveChanges = {
                                viewModel.updateComplaintStatusAndRemarks(
                                    newStatus = currentSelectedStatus,
                                    newRemarks = currentAdminRemarksInput
                                )
                            },
                            statusDropdownExpanded = statusDropdownExpanded,
                            onStatusDropdownExpandedChange = { expanded -> statusDropdownExpanded = expanded },
                            isUpdating = updateState is UpdateReportState.Loading
                        )
                    }
                    is ReportDetailState.NotFound -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Report not found.", style = MaterialTheme.typography.headlineSmall)
                        }
                    }
                    is ReportDetailState.Error -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Error loading details: ${state.message}", color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center, modifier = Modifier.padding(16.dp))
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(onClick = { viewModel.refreshReportDetails() }) {
                                    Text("Retry")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportContentViewInternal(
    report: ComplaintData,
    isUserAdmin: Boolean,
    currentStatusSelection: String,
    onStatusSelected: (String) -> Unit,
    currentAdminRemarks: String,
    onAdminRemarksChanged: (String) -> Unit,
    onSaveChanges: () -> Unit,
    statusDropdownExpanded: Boolean,
    onStatusDropdownExpandedChange: (Boolean) -> Unit,
    isUpdating: Boolean
) {
    val scrollState = rememberScrollState()
    val context = LocalContext.current // For FileDisplayItem

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        DetailSection("Incident Overview") {
            DetailItem("Report ID:", report.id.takeLast(8).uppercase()) // Show partial ID
            DetailItem("Title:", report.title)
            DetailItem("Description:", report.description)
            DetailItem("Incident Date & Time:", report.getFormattedIncidentDateTime())
        }

        DetailSection("Location & Categorization") {
            if (report.latitude != null && report.longitude != null) DetailItem("Coordinates:", "${"%.5f".format(report.latitude)}, ${"%.5f".format(report.longitude)}")
            if (report.landmark.isNotBlank()) DetailItem("Landmark:", report.landmark)
            report.category?.let { DetailItem("Category:", try { ComplaintCategory.valueOf(it).name.replace("_", " ").replaceFirstChar(Char::titlecase) } catch (e: Exception) { it }) }
            if (report.speciesInvolved.isNotBlank()) DetailItem("Species:", report.speciesInvolved)
            report.severity?.let { DetailItem("Severity:", try { SeverityLevel.valueOf(it).name.replaceFirstChar(Char::titlecase) } catch (e: Exception) { it }) }
        }

        if (report.attachedFiles.isNotEmpty()) {
            DetailSection("Attached Files") {
                report.attachedFiles.forEach { fileRef ->
                    when (try { CloudinaryFileType.valueOf(fileRef.fileType) } catch (e: IllegalArgumentException) { CloudinaryFileType.RAW } ) {
                        CloudinaryFileType.IMAGE -> {
                            AsyncImage(
                                model = fileRef.secureUrl,
                                contentDescription = "Attached image: ${fileRef.originalFilename}",
                                modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp).padding(vertical = 4.dp).clip(RoundedCornerShape(8.dp)).clickable {
                                    // TODO: Implement full-screen image viewer
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(fileRef.secureUrl))
                                    try { context.startActivity(intent) } catch (e: Exception) { Toast.makeText(context, "Cannot open image.", Toast.LENGTH_SHORT).show()}
                                },
                                contentScale = ContentScale.Fit
                            )
                        }
                        CloudinaryFileType.VIDEO, CloudinaryFileType.DOCUMENT, CloudinaryFileType.RAW -> {
                            FileDisplayItem(fileRef = fileRef, context = context)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }

        if (report.witnessName.isNotBlank() || report.witnessContact.isNotBlank()) {
            DetailSection("Witness Information") {
                if (report.witnessName.isNotBlank()) DetailItem("Witness Name:", report.witnessName)
                if (report.witnessContact.isNotBlank()) DetailItem("Witness Contact:", report.witnessContact)
            }
        }

        DetailSection("Reporter & Submission Details") {
            DetailItem("Contact Preference:", try { ContactPreference.valueOf(report.contactPreference).name.replaceFirstChar(Char::titlecase) } catch (e: Exception) { report.contactPreference })
            if (report.additionalNotes.isNotBlank()) DetailItem("Additional Notes (Reporter):", report.additionalNotes)
            DetailItem("Submitted By User ID:", report.userId.take(10)+"...") // Show partial for privacy
            report.submissionTimestamp?.let { DetailItem("Submitted On (Server):", formatMillisTimestamp(it.time, "dd MMM yyyy, hh:mm a"))}
        }


        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
        DetailItem("Current Status:", report.status, valueColor = when (report.status.lowercase(Locale.ROOT)) {
            "resolved" -> Color(0xFF4CAF50)
            "pending" -> MaterialTheme.colorScheme.secondary
            "in progress" -> MaterialTheme.colorScheme.primaryContainer
            "rejected" -> MaterialTheme.colorScheme.error
            "needs more info" -> Color(0xFFFFC107) // Amber
            else -> MaterialTheme.colorScheme.onSurface
        }, labelStyle = MaterialTheme.typography.titleMedium, valueStyle = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))

        if (report.adminRemarks.isNotBlank()) {
            DetailItem("Latest Admin Remarks:", report.adminRemarks, labelStyle = MaterialTheme.typography.titleSmall)
        }
        report.lastStatusUpdateTimestamp?.let { DetailItem("Last Admin Update:", formatMillisTimestamp(it.time, "dd MMM yyyy, hh:mm a"), labelStyle = MaterialTheme.typography.labelSmall) }
        report.lastUpdatedByAdminUid?.let { DetailItem("Updated By Admin:", it.take(8)+"...", labelStyle = MaterialTheme.typography.labelSmall) }


        // --- Admin Action Section (Conditionally Shown) ---
        if (isUserAdmin) {
            Spacer(modifier = Modifier.height(16.dp))
            Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(4.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Admin Actions", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = 12.dp))

                    Text("Set New Status:", style = MaterialTheme.typography.titleMedium)
                    ExposedDropdownMenuBox(
                        expanded = statusDropdownExpanded,
                        onExpandedChange = { onStatusDropdownExpandedChange(!statusDropdownExpanded) },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                    ) {
                        OutlinedTextField(
                            value = currentStatusSelection, onValueChange = {}, readOnly = true,
                            label = { Text("Select Status") },
                            leadingIcon = { Icon(Icons.Filled.PlaylistPlay, "Status") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = statusDropdownExpanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(expanded = statusDropdownExpanded, onDismissRequest = { onStatusDropdownExpandedChange(false) }) {
                            adminComplaintStatuses.forEach { statusOption ->
                                DropdownMenuItem(text = { Text(statusOption) }, onClick = { onStatusSelected(statusOption); onStatusDropdownExpandedChange(false) })
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    Text("Add/Edit Remarks:", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(
                        value = currentAdminRemarks,
                        onValueChange = onAdminRemarksChanged,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp).padding(vertical = 8.dp),
                        label = { Text("Admin Remarks") },
                        leadingIcon = { Icon(Icons.Default.EditNote, "Remarks") }
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = onSaveChanges,
                        enabled = !isUpdating && (currentStatusSelection != report.status || currentAdminRemarks != report.adminRemarks),
                        modifier = Modifier.fillMaxWidth().height(50.dp)
                    ) {
                        if (isUpdating) CircularProgressIndicator(Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                        else { Icon(Icons.Filled.Save, "Save"); Spacer(Modifier.size(ButtonDefaults.IconSpacing)); Text("Save Admin Changes") }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(24.dp)) // Bottom padding
    }
}

// Helper composable for section titles
@Composable
fun DetailSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.padding(bottom = 16.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 8.dp))
        content()
        HorizontalDivider(modifier = Modifier.padding(top=8.dp))
    }
}


// Helper composable for individual detail items
@Composable
fun DetailItem(label: String, value: String?, valueColor: Color = LocalContentColor.current, labelStyle: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.labelLarge, valueStyle: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyLarge) {
    if (!value.isNullOrBlank()) {
        Row(modifier = Modifier.padding(vertical = 4.dp)) {
            Text(
                text = "$label ", // Add space for better separation
                style = labelStyle,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.widthIn(min = 100.dp) // Give label some min width
            )
            Text(
                text = value,
                style = valueStyle,
                color = valueColor
            )
        }
    }
}

// Helper for formatting client-side Long timestamp (ensure it's defined)
private fun formatMillisTimestamp(millis: Long, pattern: String): String {
    if (millis == 0L) return "N/A" // Handle default constructor value
    return try { SimpleDateFormat(pattern, Locale.getDefault()).format(Date(millis)) }
    catch (e: Exception) { "Invalid Date" }
}

// Helper composable for displaying non-image files with an "Open" button
@Composable
fun FileDisplayItem(fileRef: CloudinaryFileReference, context: Context) {
    val intent = remember { Intent(Intent.ACTION_VIEW, Uri.parse(fileRef.secureUrl)) }
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), elevation = CardDefaults.cardElevation(1.dp)) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Icon(
                imageVector = when (try {CloudinaryFileType.valueOf(fileRef.fileType)} catch(e:IllegalArgumentException){CloudinaryFileType.RAW}) {
                    CloudinaryFileType.VIDEO -> Icons.Filled.Videocam // Specific video icon
                    CloudinaryFileType.DOCUMENT -> Icons.Filled.Description // Specific document icon
                    else -> Icons.Filled.AttachFile // Generic file icon
                },
                contentDescription = fileRef.fileType,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(fileRef.originalFilename, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${(fileRef.bytes / 1024.0).toInt()} KB - ${fileRef.format.uppercase()}", style = MaterialTheme.typography.bodySmall)
            }
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedButton(onClick = {
                try { context.startActivity(intent) }
                catch (e: Exception) { Toast.makeText(context, "No application available to open this file type.", Toast.LENGTH_SHORT).show() }
            }) { Text("Open") }
        }
    }
}

