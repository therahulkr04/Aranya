package com.project.aranya.viewmodel

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.project.aranya.data.ComplaintData // Your ComplaintData import path
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

// Sealed class for the state of fetching a single report's details
sealed class ReportDetailState {
    object Loading : ReportDetailState()
    data class Success(val report: ComplaintData) : ReportDetailState()
    object NotFound : ReportDetailState() // Report with the given ID was not found
    data class Error(val message: String) : ReportDetailState()
}

// Sealed class for the state of updating a report (admin action)
sealed class UpdateReportState {
    object Idle : UpdateReportState()
    object Loading : UpdateReportState()
    object Success : UpdateReportState()
    data class Error(val message: String) : UpdateReportState()
}

/**
 * ViewModel for the Report Detail Screen.
 * Fetches details of a specific complaint from Firestore using its ID.
 * Allows admins (identified via Firestore 'users' collection) to update status and remarks.
 *
 * @param savedStateHandle Handle to the saved state containing navigation arguments.
 */
// If using Hilt, annotate with @HiltViewModel and @Inject constructor
class ReportDetailViewModel(
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    // State for fetching report details
    private val _reportDetailState = MutableStateFlow<ReportDetailState>(ReportDetailState.Loading)
    val reportDetailState: StateFlow<ReportDetailState> = _reportDetailState

    // State for the admin update operation
    private val _updateReportState = MutableStateFlow<UpdateReportState>(UpdateReportState.Idle)
    val updateReportState: StateFlow<UpdateReportState> = _updateReportState

    // State to indicate if the current viewer is an admin
    private val _isCurrentUserAdmin = MutableStateFlow(false)
    val isCurrentUserAdmin: StateFlow<Boolean> = _isCurrentUserAdmin

    // Report ID obtained from navigation arguments
    private val reportIdArgKey = "reportId" // Must match nav argument name
    private val reportId: String? = savedStateHandle[reportIdArgKey]

    init {
        if (reportId != null && reportId.isNotBlank()) {
            Log.d("ReportDetailVM", "ViewModel initialized for report ID: $reportId")
            fetchReportDetails(reportId)
            checkIfCurrentUserIsAdminFromFirestore() // Check admin status using Firestore
        } else {
            Log.e("ReportDetailVM", "Invalid or missing report ID from SavedStateHandle (key: '$reportIdArgKey').")
            _reportDetailState.value = ReportDetailState.Error("Report ID not provided.")
            _isCurrentUserAdmin.value = false
        }
    }

    /**
     * Checks if the currently authenticated user has admin privileges
     * by looking up their role in the Firestore 'users' collection.
     */
    private fun checkIfCurrentUserIsAdminFromFirestore() {
        val user = auth.currentUser
        if (user != null) {
            Log.d("ReportDetailVM_AdminCheck", "Checking Firestore admin status for user: ${user.uid}")
            db.collection("users").document(user.uid).get()
                .addOnCompleteListener { task ->
                    viewModelScope.launch { // Ensure StateFlow updates are on the main coroutine context
                        if (task.isSuccessful) {
                            val document = task.result
                            if (document != null && document.exists()) {
                                val isAdminRole = document.getBoolean("isAdminRole") == true
                                _isCurrentUserAdmin.value = isAdminRole
                                Log.i("ReportDetailVM_AdminCheck", "Admin status for ${user.email}: $isAdminRole (from Firestore)")
                            } else {
                                Log.w("ReportDetailVM_AdminCheck", "User document not found in Firestore for ${user.uid}. Assuming not admin.")
                                _isCurrentUserAdmin.value = false
                            }
                        } else {
                            Log.e("ReportDetailVM_AdminCheck", "Error fetching user role from Firestore.", task.exception)
                            _isCurrentUserAdmin.value = false // Default to false on error
                        }
                    }
                }
        } else {
            Log.d("ReportDetailVM_AdminCheck", "No current user to check admin status.")
            _isCurrentUserAdmin.value = false // No user, so not an admin
        }
    }

    /**
     * Fetches the details for a specific report ID from Firestore.
     */
    fun fetchReportDetails(id: String) {
        if (id.isBlank()) {
            _reportDetailState.value = ReportDetailState.Error("Report ID cannot be empty for fetching.")
            return
        }
        if (_reportDetailState.value !is ReportDetailState.Loading || (_reportDetailState.value as? ReportDetailState.Success)?.report?.id != id) {
            _reportDetailState.value = ReportDetailState.Loading
        }

        viewModelScope.launch {
            try {
                Log.d("ReportDetailVM", "Fetching report details from Firestore for ID: $id")
                val documentSnapshot = db.collection("complaints").document(id).get().await()

                if (documentSnapshot.exists()) {
                    val report = documentSnapshot.toObject(ComplaintData::class.java)
                    if (report != null) {
                        report.id = documentSnapshot.id // Populate the ID
                        Log.d("ReportDetailVM", "Report found: ID=${report.id}, Title='${report.title}'")
                        _reportDetailState.value = ReportDetailState.Success(report)
                    } else {
                        Log.e("ReportDetailVM", "Failed to parse Firestore document to ComplaintData for ID: $id.")
                        _reportDetailState.value = ReportDetailState.Error("Could not parse report data.")
                    }
                } else {
                    Log.w("ReportDetailVM", "No report found in Firestore with ID: $id")
                    _reportDetailState.value = ReportDetailState.NotFound
                }
            } catch (e: Exception) {
                Log.e("ReportDetailVM", "Error fetching report details from Firestore for ID: $id", e)
                _reportDetailState.value = ReportDetailState.Error(e.message ?: "Failed to fetch report details.")
            }
        }
    }

    /**
     * Refreshes the report details and re-checks the admin status of the current user.
     */
    fun refreshReportDetails() {
        reportId?.let {
            if (it.isNotBlank()) {
                Log.d("ReportDetailVM", "Refreshing report details for ID: $it")
                fetchReportDetails(it)
            }
        }
        checkIfCurrentUserIsAdminFromFirestore() // Re-check admin status
    }

    /**
     * Updates the status and admin remarks for a given complaint.
     * Only admins should be able to call this successfully.
     */
    fun updateComplaintStatusAndRemarks(newStatus: String, newRemarks: String) {
        val currentReportId = reportId
        val adminUid = auth.currentUser?.uid

        if (currentReportId.isNullOrBlank()) {
            _updateReportState.value = UpdateReportState.Error("Cannot update: Report ID is missing.")
            return
        }
        if (adminUid.isNullOrBlank()) { // Should be caught by auth check if UI is protected
            _updateReportState.value = UpdateReportState.Error("Cannot update: Admin user not identified.")
            return
        }
        if (!_isCurrentUserAdmin.value) { // Client-side check based on StateFlow
            Log.w("ReportDetailVM", "Update attempt by non-admin user (UID: $adminUid). Denied by ViewModel.")
            _updateReportState.value = UpdateReportState.Error("Unauthorized: This action requires admin privileges.")
            return
        }

        _updateReportState.value = UpdateReportState.Loading
        viewModelScope.launch {
            try {
                Log.i("ReportDetailVM", "Admin (UID: $adminUid) updating report ID: $currentReportId. New Status: '$newStatus', Remarks: '$newRemarks'")
                val reportRef = db.collection("complaints").document(currentReportId)

                val updates = hashMapOf<String, Any>(
                    "status" to newStatus,
                    "adminRemarks" to newRemarks,
                    "lastUpdatedByAdminUid" to adminUid,
                    "lastStatusUpdateTimestamp" to FieldValue.serverTimestamp()
                )

                reportRef.update(updates).await()
                Log.i("ReportDetailVM", "Report ID: $currentReportId updated successfully by admin: $adminUid.")
                _updateReportState.value = UpdateReportState.Success
                fetchReportDetails(currentReportId) // Refresh details to show changes
            } catch (e: Exception) {
                Log.e("ReportDetailVM", "Error updating report ID: $currentReportId", e)
                _updateReportState.value = UpdateReportState.Error(e.message ?: "Failed to update report.")
            }
        }
    }

    /**
     * Resets the update operation state to Idle.
     */
    fun resetUpdateState() {
        _updateReportState.value = UpdateReportState.Idle
    }
}