// Located in: app/src/main/java/com/project/aranya/viewmodel/
package com.project.aranya.viewmodel

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cloudinary.android.MediaManager
import com.cloudinary.android.callback.ErrorInfo
import com.cloudinary.android.callback.UploadCallback
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.project.aranya.data.CloudinaryFileReference
import com.project.aranya.data.CloudinaryFileType
import com.project.aranya.data.ComplaintCategory
import com.project.aranya.data.ComplaintData
import com.project.aranya.data.ContactPreference
import com.project.aranya.data.SeverityLevel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

// Represents the possible outcomes of the submission process
sealed class SubmissionState {
    object Idle : SubmissionState()
    object Loading : SubmissionState()
    object Success : SubmissionState()
    data class Error(val message: String) : SubmissionState()
    // data class PartialSuccess(val message: String) : SubmissionState() // Kept if needed, but less likely now
}

class SubmitComplaintViewModel : ViewModel() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    var complaintDataState by mutableStateOf(ComplaintData())
        private set
    var submissionState by mutableStateOf<SubmissionState>(SubmissionState.Idle)
        private set

    // --- Validation States & Functions ---
    var titleError by mutableStateOf<String?>(null); private set
    var descriptionError by mutableStateOf<String?>(null); private set
    var titleTouched by mutableStateOf(false); private set
    var descriptionTouched by mutableStateOf(false); private set

    fun onTitleChange(newTitle: String) {
        complaintDataState = complaintDataState.copy(title = newTitle)
        if (titleTouched) validateTitle()
    }
    fun onDescriptionChange(newDescription: String) {
        complaintDataState = complaintDataState.copy(description = newDescription)
        if (descriptionTouched) validateDescription()
    }
    fun onTitleFocusChanged(isFocused: Boolean) { if (!isFocused && !titleTouched) { titleTouched = true; validateTitle() } else if (isFocused) titleError = null }
    fun onDescriptionFocusChanged(isFocused: Boolean) { if (!isFocused && !descriptionTouched) { descriptionTouched = true; validateDescription() } else if (isFocused) descriptionError = null }

    private fun validateTitle(): Boolean { return if (complaintDataState.title.isBlank()) { titleError = "Title cannot be empty."; false } else { titleError = null; true } }
    private fun validateDescription(): Boolean { return if (complaintDataState.description.isBlank()) { descriptionError = "Description cannot be empty."; false } else { descriptionError = null; true } }

    private fun validateAllFields(): Boolean {
        titleTouched = true // Mark as touched to show errors if user directly clicks submit
        descriptionTouched = true
        val isTitleValid = validateTitle()
        val isDescriptionValid = validateDescription()
        // Add other field validations here and combine with &&
        return isTitleValid && isDescriptionValid
    }
    // --- End Validation ---

    // --- Other field update functions ---
    fun onDateTimeSelected(dateMillis: Long?, timeMillis: Long?) { complaintDataState = complaintDataState.copy(incidentDate = dateMillis, incidentTime = timeMillis) }
    fun onLocationUpdate(lat: Double?, lon: Double?, landmark: String) { complaintDataState = complaintDataState.copy(latitude = lat, longitude = lon, landmark = landmark) }
    fun onCategoryChange(newCategory: ComplaintCategory?) { complaintDataState = complaintDataState.copy(category = newCategory?.name) }
    fun onSpeciesChange(newSpecies: String) { complaintDataState = complaintDataState.copy(speciesInvolved = newSpecies) }
    fun onSeverityChange(newSeverity: SeverityLevel?) { complaintDataState = complaintDataState.copy(severity = newSeverity?.name) }
    fun onWitnessNameChange(newName: String) { complaintDataState = complaintDataState.copy(witnessName = newName) }
    fun onWitnessContactChange(newContact: String) { complaintDataState = complaintDataState.copy(witnessContact = newContact) }
    fun onContactPreferenceChange(newPreference: ContactPreference) { complaintDataState = complaintDataState.copy(contactPreference = newPreference.name) }
    fun onAdditionalNotesChange(newNotes: String) { complaintDataState = complaintDataState.copy(additionalNotes = newNotes) }

    // --- URI handling for media and documents ---
    fun addMediaUri(uri: Uri) { val uris = complaintDataState.attachedMediaUris.toMutableList(); uris.add(uri.toString()); complaintDataState = complaintDataState.copy(attachedMediaUris = uris) }
    fun addMultipleMediaUris(uris: List<Uri>) { val current = complaintDataState.attachedMediaUris.toMutableList(); uris.forEach { current.add(it.toString()) }; complaintDataState = complaintDataState.copy(attachedMediaUris = current)}
    fun removeMediaUri(uriString: String) { val uris = complaintDataState.attachedMediaUris.toMutableList(); uris.remove(uriString); complaintDataState = complaintDataState.copy(attachedMediaUris = uris) }
    fun addDocumentUri(uri: Uri) { val uris = complaintDataState.attachedDocumentUris.toMutableList(); uris.add(uri.toString()); complaintDataState = complaintDataState.copy(attachedDocumentUris = uris) }
    fun addMultipleDocumentUris(uris: List<Uri>) { val current = complaintDataState.attachedDocumentUris.toMutableList(); uris.forEach { current.add(it.toString()) }; complaintDataState = complaintDataState.copy(attachedDocumentUris = current)}
    fun removeDocumentUri(uriString: String) { val uris = complaintDataState.attachedDocumentUris.toMutableList(); uris.remove(uriString); complaintDataState = complaintDataState.copy(attachedDocumentUris = uris) }


    // Helper function to upload a single file to Cloudinary
    private suspend fun uploadFileToCloudinary(context: Context, fileUri: Uri, originalFilenameProvided: String?): CloudinaryFileReference? {
        val deferred = CompletableDeferred<CloudinaryFileReference?>()
        Log.d("CloudinaryUpload", "Step 1: Processing URI for Cloudinary: $fileUri")
        val contentResolver = context.contentResolver
        if (contentResolver == null) {
            Log.e("CloudinaryUpload", "CRITICAL: ContentResolver is null! Cannot process file for URI: $fileUri")
            return null // Early exit if context or resolver is an issue
        }

        var originalFilename = originalFilenameProvided ?: "unknown_file"
        // Attempt to get filename if not provided or is default
        if (originalFilename == "unknown_file") {
            try {
                contentResolver.query(fileUri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    Log.d("CloudinaryUpload", "Step 2: Cursor obtained for filename query for URI: $fileUri")
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        Log.d("CloudinaryUpload", "Step 3: DisplayName column index: $nameIndex for URI: $fileUri")
                        if (nameIndex != -1) originalFilename = cursor.getString(nameIndex)
                        Log.d("CloudinaryUpload", "Step 4: Filename from cursor: '$originalFilename' for URI: $fileUri")
                    } else {
                        Log.w("CloudinaryUpload", "Step 2.1: Cursor for filename is empty for URI: $fileUri. Using fallback.")
                    }
                } ?: Log.w("CloudinaryUpload", "Step 2.2: Filename query cursor was null for URI: $fileUri. Using fallback.")
            } catch (e: Exception) {
                Log.e("CloudinaryUpload", "Step 2.E: Error getting filename from URI: $fileUri. Using fallback.", e)
            }
        }

        val mimeType = try { contentResolver.getType(fileUri) } catch (e: Exception) { Log.w("CloudinaryUpload", "Could not get MIME type for $fileUri", e); null }
        Log.d("CloudinaryUpload", "Step 5: MIME type: '$mimeType' for filename: '$originalFilename'")

        val resourceTypeForCloudinary = when {
            mimeType?.startsWith("image/") == true -> "image"
            mimeType?.startsWith("video/") == true -> "video"
            else -> "raw"
        }
        val appFileType = when (resourceTypeForCloudinary) {
            "image" -> CloudinaryFileType.IMAGE
            "video" -> CloudinaryFileType.VIDEO
            else -> if (mimeType == "application/pdf") CloudinaryFileType.DOCUMENT else CloudinaryFileType.RAW
        }
        Log.d("CloudinaryUpload", "Step 6: Determined Cloudinary ResourceType: '$resourceTypeForCloudinary', AppFileType: '${appFileType.name}' for filename: '$originalFilename'")

        // Check MediaManager initialization (optional defensive check)
        if (MediaManager.get().cloudinary == null) {
            Log.e("CloudinaryUpload", "CRITICAL: Cloudinary MediaManager not initialized! Cannot upload '$originalFilename'. Check MyApplication.kt.")
            deferred.complete(null) // Complete with null immediately
            return deferred.await() // Return the completed deferred
        }

        MediaManager.get().upload(fileUri)
            .unsigned("aaryana") // **CRITICAL: REPLACE WITH YOUR PRESET NAME**
            // Add other options as needed, e.g., folder:
            // .option("folder", "aranya_app_uploads/${auth.currentUser?.uid ?: "unknown_user"}")
            .callback(object : UploadCallback {
                override fun onStart(requestId: String?) { Log.d("CloudinaryUpload", "Upload Start: $originalFilename (ID: $requestId)") }
                override fun onProgress(requestId: String?, bytes: Long, totalBytes: Long) { /* Optional: Log progress */ }
                override fun onSuccess(requestId: String?, resultData: MutableMap<Any?, Any?>?) {
                    Log.i("CloudinaryUpload", "Upload Success: $originalFilename -> $resultData")
                    val ref = CloudinaryFileReference(
                        publicId = resultData?.get("public_id") as? String ?: "",
                        version = resultData?.get("version")?.toString(),
                        signature = resultData?.get("signature") as? String,
                        resourceType = resultData?.get("resource_type") as? String ?: resourceTypeForCloudinary,
                        secureUrl = resultData?.get("secure_url") as? String ?: "",
                        originalFilename = originalFilename,
                        format = resultData?.get("format") as? String ?: "",
                        bytes = (resultData?.get("bytes") as? Number)?.toLong() ?: 0L,
                        fileType = appFileType.name
                    )
                    if (ref.secureUrl.isBlank() || ref.publicId.isBlank()){
                        Log.e("CloudinaryUpload", "Upload Success for $originalFilename but critical data (URL/PublicID) missing from Cloudinary response: $resultData")
                        deferred.complete(null)
                    } else {
                        deferred.complete(ref)
                    }
                }
                override fun onError(requestId: String?, error: ErrorInfo?) {
                    val errorCode = error?.code ?: "Unknown Code"
                    val errorDescription = error?.description ?: "Unknown error during upload."
                    Log.e("CloudinaryUpload", "Upload Error: $originalFilename -> $errorDescription (Code: $errorCode)")
                    deferred.complete(null)
                }
                override fun onReschedule(requestId: String?, error: ErrorInfo?) {
                    Log.w("CloudinaryUpload", "Upload Rescheduled: $originalFilename -> ${error?.description}")
                    deferred.complete(null) // Treat reschedule as failure for this flow's simplicity
                }
            }).dispatch()

        return deferred.await()
    }


    fun submitComplaint(context: Context) {
        if (!validateAllFields()) {
            submissionState = SubmissionState.Error("Please correct the errors noted above.")
            return
        }

        submissionState = SubmissionState.Loading
        viewModelScope.launch {
            val currentUser = auth.currentUser
            if (currentUser == null) {
                submissionState = SubmissionState.Error("User not authenticated. Please log in again.")
                return@launch
            }

            val uploadedFileCloudinaryReferences = mutableListOf<CloudinaryFileReference>()
            // Create a list of URIs to upload
            val allUrisToUploadStrings = complaintDataState.attachedMediaUris + complaintDataState.attachedDocumentUris

            if (allUrisToUploadStrings.isEmpty()) {
                Log.i("ViewModelSubmit", "No files attached. Proceeding to save metadata only.")
                // If no files, proceed directly to Firestore save
                saveComplaintToFirestore(currentUser.uid, emptyList()) // Pass empty list for attachments
                return@launch
            }

            Log.i("ViewModelSubmit", "Starting submission. Files to upload to Cloudinary: ${allUrisToUploadStrings.size}")
            var allFileUploadsSuccessful = true

            // --- 1. Upload all files to Cloudinary ---
            for (uriString in allUrisToUploadStrings) {
                val fileUri = try { Uri.parse(uriString) } catch (e: Exception) { null }
                if (fileUri == null) {
                    Log.e("ViewModelSubmit", "Invalid URI string, skipping: $uriString")
                    allFileUploadsSuccessful = false; break // Abort on first invalid URI
                }

                // originalFilenameProvided can be null, uploadFileToCloudinary will try to determine it
                val cloudinaryRef = uploadFileToCloudinary(context, fileUri, null)
                if (cloudinaryRef != null) {
                    uploadedFileCloudinaryReferences.add(cloudinaryRef)
                } else {
                    allFileUploadsSuccessful = false
                    Log.e("ViewModelSubmit", "A file upload to Cloudinary failed for URI: $fileUri. Aborting submission.")
                    submissionState = SubmissionState.Error("File upload failed for '$uriString'. Please try again.")
                    return@launch // Exit if any upload fails (strict policy)
                }
            }

            // This check might be redundant if we return early on first failure
            if (!allFileUploadsSuccessful) {
                // submissionState already set if an error occurred
                return@launch
            }

            Log.i("ViewModelSubmit", "${uploadedFileCloudinaryReferences.size} files successfully uploaded to Cloudinary.")
            saveComplaintToFirestore(currentUser.uid, uploadedFileCloudinaryReferences)
        }
    }

    // Extracted Firestore saving logic into a separate function
    private suspend fun saveComplaintToFirestore(userId: String, cloudinaryRefs: List<CloudinaryFileReference>) {
        val complaintToSaveToFirestore = complaintDataState.copy(
            userId = userId,
            clientSubmissionTimestamp = System.currentTimeMillis(),
            attachedFiles = cloudinaryRefs,
            attachedMediaUris = emptyList(), // Clear local URIs
            attachedDocumentUris = emptyList()
        )

        try {
            val complaintDocRef = db.collection("complaints").document()
            complaintToSaveToFirestore.id = complaintDocRef.id

            Log.d("ViewModelSubmit", "Attempting to save complaint metadata to Firestore (ID: ${complaintToSaveToFirestore.id}) with ${complaintToSaveToFirestore.attachedFiles.size} file references.")
            complaintDocRef.set(complaintToSaveToFirestore).await()

            Log.i("ViewModelSubmit", "Complaint (ID: ${complaintDocRef.id}) and Cloudinary refs submitted successfully to Firestore.")
            submissionState = SubmissionState.Success
            complaintDataState = ComplaintData() // Reset form on successful submission
        } catch (e: Exception) {
            Log.e("ViewModelSubmit", "Error submitting complaint metadata to Firestore after Cloudinary uploads", e)
            submissionState = SubmissionState.Error(e.message ?: "Failed to save complaint data after successful file uploads.")
            // Consider cleanup of Cloudinary files if this step fails
        }
    }

    fun resetSubmissionState() {
        submissionState = SubmissionState.Idle
        titleError = null; descriptionError = null
        titleTouched = false; descriptionTouched = false
    }
}