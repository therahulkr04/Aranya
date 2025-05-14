package com.project.aranya.data

import android.util.Log
import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.ServerTimestamp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

enum class ComplaintCategory {
    POACHING,
    INJURED_ANIMAL,
    ILLEGAL_LOGGING,
    HABITAT_DESTRUCTION,
    ILLEGAL_FISHING,
    POLLUTION,
    ENCROACHMENT,
    OTHER
}

// Enum for Severity Levels
enum class SeverityLevel {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}

// Enum for User Contact Preferences
enum class ContactPreference {
    EMAIL,
    PHONE,
    ANONYMOUS
}

// Enum for distinguishing Cloudinary file types if needed for display logic
enum class CloudinaryFileType {
    IMAGE,
    VIDEO,
    DOCUMENT,
    RAW     // For other types like TXT or unrecognized
}

// Data class to hold information about each file uploaded to Cloudinary
data class CloudinaryFileReference(
    val publicId: String = "",         // Cloudinary's public ID (for transformations, deletion)
    val version: String? = null,       // Cloudinary version (optional, for caching)
    val signature: String? = null,     // Cloudinary signature (if from a signed upload, optional for storage)
    val resourceType: String = "auto", // e.g., "image", "video", "raw" (as determined by Cloudinary or set by app)
    val secureUrl: String = "",        // HTTPS URL of the uploaded file (primary URL for access)
    val originalFilename: String = "", // Original filename from the user's device
    val format: String = "",           // File format, e.g., "jpg", "mp4", "pdf" (as determined by Cloudinary)
    val bytes: Long = 0,               // File size in bytes
    val fileType: String = CloudinaryFileType.RAW.name // App-defined type: IMAGE, VIDEO, DOCUMENT, RAW
) {
    // No-argument constructor for Firestore deserialization
    constructor() : this(
        publicId = "",
        version = null,
        signature = null,
        resourceType = "auto",
        secureUrl = "",
        originalFilename = "",
        format = "",
        bytes = 0L,
        fileType = CloudinaryFileType.RAW.name
    )
}

data class ComplaintData(
    // --- Identifiers ---
    var id: String = "", // Firestore document ID / ID from your API (if it were used)
    var userId: String = "", // UID of the user submitting the complaint

    // --- Core Complaint Details ---
    val title: String = "",
    val description: String = "",
    val incidentDate: Long? = null, // Milliseconds since epoch (UTC) for the date of incident
    val incidentTime: Long? = null, // Milliseconds offset from midnight UTC on 1/1/1970, representing time of day

    // --- Location Details ---
    val latitude: Double? = null,
    val longitude: Double? = null,
    val landmark: String = "",

    // --- Categorization & Specifics (Enums stored as their .name string) ---
    val category: String? = null, // Stores ComplaintCategory.name
    val speciesInvolved: String = "",
    val severity: String? = null, // Stores SeverityLevel.name

    // --- Attachments ---
    // Local URIs (content://) for processing during submission - NOT saved to Firestore.
    @get:Exclude @set:Exclude var attachedMediaUris: List<String> = emptyList(),
    @get:Exclude @set:Exclude var attachedDocumentUris: List<String> = emptyList(),

    // List of references to files uploaded to Cloudinary - THIS IS SAVED TO FIRESTORE.
    var attachedFiles: List<CloudinaryFileReference> = emptyList(),

    // --- Witness Information (Optional) ---
    val witnessName: String = "",
    val witnessContact: String = "",

    // --- User Preferences & Notes ---
    val contactPreference: String = ContactPreference.ANONYMOUS.name, // Stores ContactPreference.name
    val additionalNotes: String = "",

    // --- Status & Admin Tracking (Admin modifiable fields are 'var') ---
    var status: String = "Pending", // e.g., "Pending", "In Progress", "Resolved", "Rejected", "Needs More Info"
    var adminRemarks: String = "",
    var lastUpdatedByAdminUid: String? = null, // UID of the admin who last updated
    @ServerTimestamp var lastStatusUpdateTimestamp: Date? = null, // Firestore Timestamp for admin updates

    // --- Timestamps ---
    @ServerTimestamp var submissionTimestamp: Date? = null, // Firestore Timestamp for original submission
    val clientSubmissionTimestamp: Long = System.currentTimeMillis() // Client-side timestamp of submission
) {
    // Default no-argument constructor required by Firestore for deserialization.
    constructor() : this(
        id = "",
        userId = "",
        title = "",
        description = "",
        incidentDate = null,
        incidentTime = null,
        latitude = null,
        longitude = null,
        landmark = "",
        category = null,
        speciesInvolved = "",
        severity = null,
        attachedMediaUris = emptyList(), // Excluded, but good for default state
        attachedDocumentUris = emptyList(), // Excluded
        attachedFiles = emptyList(), // Initialize Cloudinary file list
        witnessName = "",
        witnessContact = "",
        contactPreference = ContactPreference.ANONYMOUS.name,
        additionalNotes = "",
        status = "Pending",
        adminRemarks = "",
        lastUpdatedByAdminUid = null,
        lastStatusUpdateTimestamp = null,
        submissionTimestamp = null,
        clientSubmissionTimestamp = 0L
    )

    /**
     * Helper function to get a formatted string representation of the incident date and time.
     * This is intended for display purposes in the UI.
     * Marked with @Exclude so Firestore doesn't try to serialize/deserialize it.
     */
    @Exclude
    fun getFormattedIncidentDateTime(): String {
        if (incidentDate == null) return "Date Not Set"

        val incidentDateCalendar = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            timeInMillis = incidentDate
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        incidentTime?.let { timeOffsetMillis ->
            val timeCalendar = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                timeInMillis = timeOffsetMillis
            }
            incidentDateCalendar.set(Calendar.HOUR_OF_DAY, timeCalendar.get(Calendar.HOUR_OF_DAY))
            incidentDateCalendar.set(Calendar.MINUTE, timeCalendar.get(Calendar.MINUTE))
        }

        val displayFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())

        return try {
            displayFormat.format(incidentDateCalendar.time)
        } catch (e: Exception) {
            Log.e("ComplaintData", "Error formatting incident date/time for display", e)
            "Invalid Date/Time"
        }
    }
}