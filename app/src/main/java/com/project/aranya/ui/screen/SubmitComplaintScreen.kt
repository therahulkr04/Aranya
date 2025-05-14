package com.project.aranya.ui.screen

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.project.aranya.data.*
import com.project.aranya.ui.theme.AranyaTheme
import com.project.aranya.viewmodel.SubmitComplaintViewModel
import com.project.aranya.viewmodel.SubmissionState
import java.text.SimpleDateFormat
import java.util.* // For SimpleDateFormat
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.core.content.ContextCompat
import androidx.navigation.compose.rememberNavController
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers // For checking location provider status
import kotlinx.coroutines.launch // For launching location fetching in coroutine scope
import kotlinx.coroutines.tasks.await // For awaiting location task
import kotlinx.coroutines.withContext // For switching context
import coil.compose.AsyncImage
import com.project.aranya.ui.screen.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubmitComplaintScreen(
    navController: androidx.navigation.NavHostController, // Use NavController for navigation
    viewModel: SubmitComplaintViewModel = viewModel()
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val scrollState = rememberScrollState()
    val complaintDataFromVM = viewModel.complaintDataState // Get state from ViewModel
    val submissionState = viewModel.submissionState
    val coroutineScope = rememberCoroutineScope()

    // State for Dropdown menus
    var categoryExpanded by remember { mutableStateOf(false) }
    var severityExpanded by remember { mutableStateOf(false) }

    // --- State for Date Picker ---
    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState(
        // Optionally pre-select date if already set in ViewModel
        initialSelectedDateMillis = complaintDataFromVM.incidentDate,
        // Set selectable years range if needed
        // yearRange = (2020..Calendar.getInstance().get(Calendar.YEAR))
    )

    // --- State for Time Picker ---
    var showTimePicker by remember { mutableStateOf(false) }
    // Initialize time picker state based on ViewModel or default to current time
    val calendar = Calendar.getInstance()
    val initialHour: Int
    val initialMinute: Int

    if (complaintDataFromVM.incidentTime != null) {
        calendar.timeInMillis = complaintDataFromVM.incidentTime
        initialHour = calendar.get(Calendar.HOUR_OF_DAY)
        initialMinute = calendar.get(Calendar.MINUTE)
    } else {
        initialHour = calendar.get(Calendar.HOUR_OF_DAY)
        initialMinute = calendar.get(Calendar.MINUTE)
    }
    val timePickerState = rememberTimePickerState(
        initialHour = initialHour,
        initialMinute = initialMinute,
    )
    var isFetchingLocation by remember { mutableStateOf(false) }

    // --- Handle Submission State ---
    LaunchedEffect(submissionState) {
        // ... (keep existing submission state handling: success/error toasts, navigation)
        when (submissionState) {
            is SubmissionState.Success -> {
                Toast.makeText(context, "Complaint Submitted Successfully!", Toast.LENGTH_LONG).show()
                navController.popBackStack()
            }
            is SubmissionState.Error -> {
                Toast.makeText(context, "Error: ${submissionState.message}", Toast.LENGTH_LONG).show()
                viewModel.resetSubmissionState()
            }
            else -> {}
        }
    }


    // --- FusedLocationProviderClient ---
    // Use 'remember' to keep the same client instance across recompositions
    val fusedLocationClient = remember {
        LocationServices.getFusedLocationProviderClient(context)
    }
    var cancellationTokenSource = remember { CancellationTokenSource() }


    // --- Permission Handling ---
    // Define the permissions we need
    val locationPermissions = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )

    // State to track if rationale should be shown (user denied once)
    var showPermissionRationale by remember { mutableStateOf(false) }


    // --- Helper function to check permissions ---
    fun hasLocationPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    // --- Helper function to check if GPS is enabled ---
    suspend fun isGpsEnabled(context: Context): Boolean = withContext(Dispatchers.IO) {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        try {
            return@withContext locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        } catch (e: Exception) {
            // Handle exceptions, e.g., security exception if somehow location services are restricted
            Log.e("LocationCheck", "Error checking GPS status", e)
            return@withContext false
        }
    }

    // --- Fetch Location Logic ---
    // (Moved outside the button's onClick for clarity)
    fun fetchLocation(
        context: Context,
        client: com.google.android.gms.location.FusedLocationProviderClient,
        cancellationTokenSource: CancellationTokenSource,
        onResult: (Double?, Double?) -> Unit
    ) {
        if (!hasLocationPermission(context)) {
            // Should not happen if called after permission check, but good safety check
            Toast.makeText(context, "Location permissions missing.", Toast.LENGTH_SHORT).show()
            onResult(null, null)
            return
        }

        coroutineScope.launch { // Launch in coroutine scope
            // 1. Check if GPS is enabled
            if (!isGpsEnabled(context)) {
                Toast.makeText(context, "Please enable GPS/Location Services.", Toast.LENGTH_LONG).show()
                isFetchingLocation = false // Update fetching state
                onResult(null, null)
                return@launch
            }

            // 2. Try getting current location (more accurate, might take longer)
            try {
                isFetchingLocation = true // Update fetching state here
                Log.d("LocationFetch", "Requesting current location...")
                // Use HIGH_ACCURACY. Add a CancellationToken.
                val location = client.getCurrentLocation(
                    Priority.PRIORITY_HIGH_ACCURACY, // Request high accuracy
                    cancellationTokenSource.token // Pass the token
                ).await() // Use await() suspend function

                if (location != null) {
                    Log.d("LocationFetch", "Current location success: Lat=${location.latitude}, Lon=${location.longitude}")
                    onResult(location.latitude, location.longitude)
                } else {
                    // If current location is null (rare with high accuracy request but possible),
                    // try last known location as fallback (can be null or outdated)
                    Log.w("LocationFetch", "getCurrentLocation returned null, trying getLastLocation.")
                    try {
                        val lastLocation = client.lastLocation.await() // await() for lastLocation
                        if (lastLocation != null) {
                            Log.d("LocationFetch", "Last location success: Lat=${lastLocation.latitude}, Lon=${lastLocation.longitude}")
                            onResult(lastLocation.latitude, lastLocation.longitude)
                        } else {
                            Log.e("LocationFetch", "Both current and last location are null.")
                            Toast.makeText(context, "Could not retrieve location.", Toast.LENGTH_SHORT).show()
                            onResult(null, null)
                        }
                    } catch (lastLocationException: SecurityException) {
                        // This should be caught by the initial permission check, but include for safety
                        Log.e("LocationFetch", "SecurityException getting last location", lastLocationException)
                        Toast.makeText(context, "Location permission error.", Toast.LENGTH_SHORT).show()
                        onResult(null, null)
                    } catch (e: Exception) {
                        Log.e("LocationFetch", "Exception getting last location", e)
                        Toast.makeText(context, "Error getting last location.", Toast.LENGTH_SHORT).show()
                        onResult(null, null)
                    }
                }
            } catch (securityException: SecurityException) {
                // Should be caught by hasLocationPermission, but handle defensively
                Log.e("LocationFetch", "SecurityException getting current location", securityException)
                Toast.makeText(context, "Location permission error.", Toast.LENGTH_SHORT).show()
                onResult(null, null)
            } catch (e: Exception) {
                // Handle other exceptions like CancellationException, TimeoutException, etc.
                Log.e("LocationFetch", "Exception getting current location", e)
                if (e is java.util.concurrent.TimeoutException) {
                    Toast.makeText(context, "Location request timed out. Check GPS signal.", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(context, "Error retrieving location.", Toast.LENGTH_SHORT).show()
                }
                onResult(null, null)
            } finally {
                isFetchingLocation = false // Ensure fetching state is reset
            }
        } // End coroutineScope.launch
    }

    // ActivityResultLauncher for requesting permissions
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { permissions ->
            val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
            val coarseLocationGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false

            if (fineLocationGranted || coarseLocationGranted) {
                // Permission granted, proceed to fetch location
                fetchLocation(context, fusedLocationClient, cancellationTokenSource) { lat, lon ->
                    viewModel.onLocationUpdate(lat, lon, complaintDataFromVM.landmark)
                    isFetchingLocation = false
                }
            } else {
                // Permission denied. Check if rationale should be shown.
                // This requires checking shouldShowRequestPermissionRationale, which is tricky in Compose.
                // For simplicity, show a toast indicating denial. A better UX involves guidance.
                Toast.makeText(context, "Location permission denied. Cannot get GPS coordinates.", Toast.LENGTH_LONG).show()
                isFetchingLocation = false
                // Consider setting showPermissionRationale = true if you implement a rationale dialog
            }
        }
    )




    // --- Date Picker Dialog ---
    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDatePicker = false
                        // Get selected date, ensuring it's UTC midnight
                        val selectedMillis = datePickerState.selectedDateMillis
                        // Pass selected date (and existing time) to ViewModel
                        viewModel.onDateTimeSelected(selectedMillis, complaintDataFromVM.incidentTime)
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) { // Content of the dialog
            DatePicker(state = datePickerState)
        }
    }

    // --- Time Picker Dialog ---
    if (showTimePicker) {
        TimePickerDialog( // Use our custom wrapper
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        showTimePicker = false
                        // Convert selected hour/minute to milliseconds since epoch (relative to selected date or just store as time offset?)
                        // Let's calculate the millis for that specific time on Jan 1, 1970 UTC for simplicity for now
                        // Alternatively, store hour/minute directly in ViewModel/Data class if preferred
                        val selectedTimeCalendar = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                            // Reset to epoch start
                            timeInMillis = 0L
                            set(Calendar.HOUR_OF_DAY, timePickerState.hour)
                            set(Calendar.MINUTE, timePickerState.minute)
                        }
                        val selectedTimeMillis = selectedTimeCalendar.timeInMillis
                        // Pass existing date and selected time to ViewModel
                        viewModel.onDateTimeSelected(complaintDataFromVM.incidentDate, selectedTimeMillis)
                    }
                ) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("Cancel") }
            }
        ) {
            TimePicker(
                state = timePickerState,
                // Use layout based on screen orientation
                // layoutType = if (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) TimePickerLayoutType.Horizontal else TimePickerLayoutType.Vertical
            )
        }
    }

    // --- Media Permission Handling ---
    // Photo Picker generally doesn't require explicit permission declaration for selection
    // BUT if you need to access the URI later persistently, or use Camera, you need permissions.
    val readMediaPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_IMAGES // Use specific media type if needed
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    val mediaPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            if (isGranted) {
                // Permission granted, maybe launch picker again or enable a feature
                Toast.makeText(context, "Permission Granted", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Storage Permission Denied", Toast.LENGTH_SHORT).show()
            }
        }
    )

    fun checkAndRequestMediaPermission(onGranted: () -> Unit) {
        if (ContextCompat.checkSelfPermission(context, readMediaPermission) == PackageManager.PERMISSION_GRANTED) {
            onGranted()
        } else {
            // Optional: Show rationale here if needed before launching
            mediaPermissionLauncher.launch(readMediaPermission)
        }
    }


    // --- Photo Picker Launcher ---
    // Handles selecting single or multiple images/videos
    val pickMultipleMediaLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(5), // Limit to 5 selections max
        onResult = { uris ->
            if (uris.isNotEmpty()) {
                Log.d("PhotoPicker", "Selected URIs: $uris")
                viewModel.addMultipleMediaUris(uris)
            } else {
                Log.d("PhotoPicker", "No media selected")
            }
        }
    )

    // Function to launch the picker
    fun launchPhotoPicker() {
        // Launch the picker to select images and videos
        pickMultipleMediaLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
        )
        // Or use ImageOnly or VideoOnly if needed:
        // PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
    }
    // --- Document Picker Launcher ---
    val pickMultipleDocumentsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(), // For selecting multiple documents
        onResult = { uris: List<Uri> ->
            if (uris.isNotEmpty()) {
                Log.d("DocumentPicker", "Selected document URIs: $uris")
                viewModel.addMultipleDocumentUris(uris)
            } else {
                Log.d("DocumentPicker", "No documents selected.")
            }
        }
    )

    fun launchDocumentPicker() {
        // Define the MIME types you want to allow for documents
        val mimeTypes = arrayOf(
            "application/pdf",                    // PDF
            "text/plain",                         // Plain Text
            "application/msword",                 // .doc
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document", // .docx
            "application/vnd.ms-excel",           // .xls
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",   // .xlsx
            "application/vnd.ms-powerpoint",      // .ppt
            "application/vnd.openxmlformats-officedocument.presentationml.presentation" // .pptx
            // Add more MIME types as needed
        )
        pickMultipleDocumentsLauncher.launch(mimeTypes)
    }


    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Submit Complaint") },
                navigationIcon = {
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
                .padding(horizontal = 16.dp)
                .verticalScroll(scrollState)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Incident Title
            OutlinedTextField(
                value = complaintDataFromVM.title,
                onValueChange = viewModel::onTitleChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Incident Title *") },
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences
                ),
                isError = submissionState is SubmissionState.Error && complaintDataFromVM.title.isBlank()
            )
            Spacer(modifier = Modifier.height(16.dp))

            // Detailed Description
            OutlinedTextField(
                value = complaintDataFromVM.description,
                onValueChange = viewModel::onDescriptionChange,
                modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp),
                label = { Text("Detailed Description *") },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences
                ),
                isError = submissionState is SubmissionState.Error && complaintDataFromVM.description.isBlank()
            )
            Spacer(modifier = Modifier.height(16.dp))

            // Date & Time Picker (Placeholders)
            Text("Date & Time of Incident", style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = formatMillis(complaintDataFromVM.incidentDate, "dd MMM yyyy") ?: "Select Date",
                    onValueChange = {}, // Not editable directly
                    readOnly = true,
                    label = { Text("Date") },
                    modifier = Modifier.weight(1f).clickable { showDatePicker = true },
                    trailingIcon = { Icon(Icons.Default.CalendarToday, "Select Date") }
                )
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedTextField(
                    value = formatMillis(complaintDataFromVM.incidentTime, "hh:mm a") ?: "Select Time",
                    onValueChange = {}, // Not editable directly
                    readOnly = true,
                    label = { Text("Time") },
                    modifier = Modifier.weight(1f).clickable { showTimePicker = true },
                    trailingIcon = { Icon(Icons.Default.AccessTime, "Select Time") }
                )
            }
            Spacer(modifier = Modifier.height(16.dp))

            // Location Details (Placeholders)
            Text("Location Details", style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Display Coordinates
                Text(
                    text = when {
                        isFetchingLocation -> "Fetching location..." // Show fetching status
                        complaintDataFromVM.latitude != null && complaintDataFromVM.longitude != null ->
                            "Lat: ${"%.4f".format(complaintDataFromVM.latitude)}, Lon: ${"%.4f".format(complaintDataFromVM.longitude)}"
                        else -> "No GPS coordinates captured"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))

                // Get GPS Button
                Button(
                    onClick = {
                        isFetchingLocation = true // Set loading state immediately
                        if (hasLocationPermission(context)) {
                            // Permissions granted, check GPS and fetch
                            fetchLocation(context, fusedLocationClient, cancellationTokenSource) { lat, lon ->
                                viewModel.onLocationUpdate(lat, lon, complaintDataFromVM.landmark)
                                isFetchingLocation = false // Reset loading state in callback
                            }
                        } else {
                            // Permissions not granted, launch request
                            // Optional: Show rationale if needed before launching
                            locationPermissionLauncher.launch(locationPermissions)
                            // Set isFetchingLocation = false here? Or wait for launcher result?
                            // It's better to wait for the launcher result to set isFetchingLocation = false
                        }
                    },
                    enabled = !isFetchingLocation // Disable button while fetching
                ) {
                    if (isFetchingLocation) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = LocalContentColor.current // Use appropriate color
                        )
                    } else {
                        Icon(Icons.Default.MyLocation, contentDescription = "Get GPS Location")
                        Spacer(Modifier.width(4.dp))
                        Text("Get GPS")
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = complaintDataFromVM.landmark,
                onValueChange = { viewModel.onLocationUpdate(complaintDataFromVM.latitude, complaintDataFromVM.longitude, it) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Nearest Landmark / Park Zone (Optional)") },
                singleLine = true
            )
            Spacer(modifier = Modifier.height(16.dp))

            // Category Dropdown
            ExposedDropdownMenuBox(
                expanded = categoryExpanded,
                onExpandedChange = { categoryExpanded = !categoryExpanded },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = complaintDataFromVM.category?.let { ComplaintCategory.valueOf(it).name.replace("_", " ").lowercase().replaceFirstChar { char -> char.titlecase() } } ?: "",
                    onValueChange = {}, // Not editable directly
                    readOnly = true,
                    label = { Text("Category (Optional)") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth() // Important for anchoring dropdown
                )
                ExposedDropdownMenu(
                    expanded = categoryExpanded,
                    onDismissRequest = { categoryExpanded = false }
                ) {
                    ComplaintCategory.values().forEach { selectionOption ->
                        DropdownMenuItem(
                            text = { Text(selectionOption.name.replace("_", " ").lowercase().replaceFirstChar { it.titlecase() }) },
                            onClick = {
                                viewModel.onCategoryChange(selectionOption)
                                categoryExpanded = false
                            }
                        )
                    }
                    // Option to clear selection
                    DropdownMenuItem(
                        text = { Text("Clear Selection", style = MaterialTheme.typography.bodySmall) },
                        onClick = {
                            viewModel.onCategoryChange(null)
                            categoryExpanded = false
                        }
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))


            // Species Involved
            OutlinedTextField(
                value = complaintDataFromVM.speciesInvolved,
                onValueChange = viewModel::onSpeciesChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Species Involved (Optional)") },
                singleLine = true
            )
            Spacer(modifier = Modifier.height(16.dp))

            // Severity Level Dropdown
            ExposedDropdownMenuBox(
                expanded = severityExpanded,
                onExpandedChange = { severityExpanded = !severityExpanded },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = complaintDataFromVM.severity?.let { SeverityLevel.valueOf(it).name.lowercase().replaceFirstChar { char -> char.titlecase() } } ?: "",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Severity Level (Optional)") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = severityExpanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = severityExpanded,
                    onDismissRequest = { severityExpanded = false }
                ) {
                    SeverityLevel.values().forEach { selectionOption ->
                        DropdownMenuItem(
                            text = { Text(selectionOption.name.lowercase().replaceFirstChar { it.titlecase() }) },
                            onClick = {
                                viewModel.onSeverityChange(selectionOption) // ViewModel handles storing .name
                                severityExpanded = false
                            }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Clear Selection", style = MaterialTheme.typography.bodySmall) },
                        onClick = {
                            viewModel.onSeverityChange(null)
                            severityExpanded = false
                        }
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            // Attachments (Placeholders)
            Text("Attachments (Optional)", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))

            // Display Selected Media Thumbnails
            if (complaintDataFromVM.attachedMediaUris.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(complaintDataFromVM.attachedMediaUris, key = { it }) { uriString ->
                        MediaThumbnailItem(
                            uriString = uriString,
                            onRemoveClick = { viewModel.removeMediaUri(uriString) }
                        )
                    }
                }
            }
            // Display Selected Documents
            if (complaintDataFromVM.attachedDocumentUris.isNotEmpty()) {
                Text("Documents:", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
                complaintDataFromVM.attachedDocumentUris.forEachIndexed { _, uriString -> // Use _ if index not needed
                    DocumentListItem( // Use the DocumentListItem composable defined earlier/below
                        uriString = uriString,
                        onRemoveClick = { viewModel.removeDocumentUri(uriString) }
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (complaintDataFromVM.attachedMediaUris.isEmpty() && complaintDataFromVM.attachedDocumentUris.isEmpty()) {
                Text("No media or documents attached.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 8.dp))
            }
            else {
                Text(
                    "No media attached.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }


            // Buttons to Add Media
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                // Using Photo Picker
                Button(onClick = {
                    // Check permission only if strictly needed for post-selection access,
                    // Picker itself often works without explicit permission request.
                    // If unsure, check and request first:
                    // checkAndRequestMediaPermission { launchPhotoPicker() }
                    // For simplicity now, assume picker handles it or permission already granted:
                    launchPhotoPicker()
                }) {
                    Icon(Icons.Default.AddPhotoAlternate, contentDescription = null) // Choose appropriate icon
                    Spacer(Modifier.width(4.dp))
                    Text("Add Media")
                }

                // Button for Document Picker (Placeholder)
                Button(onClick = {
                    // TODO: Implement Document Picker (e.g., ActivityResultContracts.GetContent with "*/*")
                    launchDocumentPicker()
                }) {
                    Icon(Icons.Default.AttachFile, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Document")
                }

                // Optional: Button for Camera Capture
                /*
                Button(onClick = {
                    // TODO: Implement Camera Permission Check & Capture Intent
                    Toast.makeText(context, "Camera Placeholder", Toast.LENGTH_SHORT).show()
                }) {
                    Icon(Icons.Default.AddAPhoto, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Camera")
                }
                */
            }
            Spacer(modifier = Modifier.height(16.dp))

            // Witness Information (Optional)
            Text("Witness Information (Optional)", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = complaintDataFromVM.witnessName,
                onValueChange = viewModel::onWitnessNameChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Witness Name") },
                singleLine = true
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = complaintDataFromVM.witnessContact,
                onValueChange = viewModel::onWitnessContactChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Witness Contact Info") },
                singleLine = true
            )
            Spacer(modifier = Modifier.height(16.dp))


            // Contact Preference
            Text("Contact Preference", style = MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth()) {
                ContactPreference.values().forEach { preference ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .weight(1f) // Distribute space
                            .clickable { viewModel.onContactPreferenceChange(preference) }
                            .padding(vertical = 4.dp)
                    ) {
                        RadioButton(
                            selected = (complaintDataFromVM.contactPreference == preference.name),
                            onClick = { viewModel.onContactPreferenceChange(preference) }
                        )
                        Text(
                            text = preference.name.lowercase().replaceFirstChar { it.titlecase() },
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            // Additional Notes
            OutlinedTextField(
                value = complaintDataFromVM.additionalNotes,
                onValueChange = viewModel::onAdditionalNotesChange,
                modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp),
                label = { Text("Additional Notes (Optional)") }
            )
            Spacer(modifier = Modifier.height(24.dp))

            // Submit Button
            Button(
                onClick = { viewModel.submitComplaint(context) },
                enabled = submissionState != SubmissionState.Loading, // Disable while loading
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                if (submissionState == SubmissionState.Loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Submit Complaint")
                }
            }
            Spacer(modifier = Modifier.height(16.dp)) // Bottom padding
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            cancellationTokenSource.cancel()
        }
    }
}

// --- Composable for displaying a single document item ---
// (Ensure this is defined, as created in a previous step)
@Composable
fun DocumentListItem(
    uriString: String,
    onRemoveClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var fileName by remember { mutableStateOf("Loading...") }

    LaunchedEffect(uriString) {
        val uri = Uri.parse(uriString)
        fileName = try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val displayNameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (displayNameIndex != -1) cursor.getString(displayNameIndex) else uri.lastPathSegment ?: "Unknown Document"
                } else {
                    uri.lastPathSegment ?: "Unknown Document"
                }
            } ?: uri.lastPathSegment ?: "Unknown Document"
        } catch (e: Exception) {
            Log.e("DocumentListItem", "Error getting filename for $uriString", e)
            "Error: Invalid URI"
        }
    }

    Row( /* ... styling as previously defined for DocumentListItem ... */
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .border(1.dp, Color.LightGray, RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Icon(Icons.Default.Description, "Document", modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(fileName, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
        IconButton(onClick = onRemoveClick, modifier = Modifier.size(24.dp)) {
            Icon(Icons.Default.Close, "Remove document", modifier = Modifier.size(18.dp))
        }
    }
}


@Composable
fun MediaThumbnailItem(
    uriString: String,
    onRemoveClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.size(80.dp)) { // Container for image and remove button
        AsyncImage(
            model = Uri.parse(uriString), // Parse string back to Uri for Coil
            contentDescription = "Attached media thumbnail",
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, Color.LightGray, RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop // Crop to fit the square size
        )
        // Remove Button positioned at the top-right corner
        IconButton(
            onClick = onRemoveClick,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .size(20.dp) // Smaller icon button
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
        ) {
            Icon(
                imageVector = Icons.Default.Close, // Use standard close icon
                contentDescription = "Remove media",
                modifier = Modifier.size(14.dp), // Smaller icon
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}


// Helper function to format milliseconds to String, returns null if millis is null
private fun formatMillis(millis: Long?, pattern: String): String? {
    // Handle specific case for time where millis might represent offset from epoch start
    if (millis != null && pattern == "hh:mm a") {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = millis }
        // Ensure it's formatting time part only
        return try {
            val sdf = SimpleDateFormat(pattern, Locale.getDefault())
            // sdf.timeZone = TimeZone.getDefault() // Format in local timezone for display
            sdf.format(calendar.time)
        } catch (e: Exception) { null }

    } else if (millis != null) { // For date or other patterns
        return try {
            val sdf = SimpleDateFormat(pattern, Locale.getDefault())
            // sdf.timeZone = TimeZone.getTimeZone("UTC") // Assume stored date is UTC midnight
            sdf.format(Date(millis))
        } catch (e: Exception) {
            null
        }
    }
    return null
}


// TimePickerDialog wrapper (Needed for Material 3 as of some versions)
// Check if this is still required in your M3 version. If TimePickerDialog exists directly, use it.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerDialog(
    title: @Composable (() -> Unit)? = null,
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: @Composable (() -> Unit)? = null,
    containerColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.surface,
    content: @Composable () -> Unit, // Pass TimePicker composable here
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false), // Allow custom width if needed
        modifier = Modifier.widthIn(max = 360.dp), // Typical dialog width
        // Provide explicit confirm/dismiss buttons layout
        confirmButton = confirmButton,
        dismissButton = dismissButton,
        title = title,
        text = content, // Place TimePicker content in the text area
        containerColor = containerColor
    )
}


// --- Preview ---
@Preview(showBackground = true)
@Composable
fun SubmitComplaintScreenPreview() {
    AranyaTheme {
        SubmitComplaintScreen(navController = rememberNavController())
    }
}