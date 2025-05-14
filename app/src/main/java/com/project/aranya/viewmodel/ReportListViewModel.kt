package com.project.aranya.viewmodel // Use your package name

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query // For Query.Direction
import com.project.aranya.data.ComplaintData // Use your specific ComplaintData import path
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

// Sealed class to represent the state of fetching the list of reports
sealed class ReportListState {
    object Loading : ReportListState() // Indicates data is currently being fetched
    data class Success(val reports: List<ComplaintData>) : ReportListState() // Data fetched successfully
    object Empty : ReportListState() // Fetch was successful, but no reports were found for the user
    data class Error(val message: String) : ReportListState() // An error occurred during fetching
}

/**
 * ViewModel for the "My Reports" screen.
 * Fetches a list of complaints submitted by the currently authenticated user from Firestore.
 */
// Add @HiltViewModel if using Hilt
class ReportListViewModel : ViewModel() {

    // Firebase services instances
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    // StateFlow to hold and expose the list of reports and the current fetching state
    private val _reportListState = MutableStateFlow<ReportListState>(ReportListState.Loading)
    val reportListState: StateFlow<ReportListState> = _reportListState

    init {
        // Automatically fetch user reports when the ViewModel is created
        fetchUserReports()
    }

    /**
     * Fetches complaints from Firestore where the 'userId' matches the current authenticated user's UID.
     * Orders the reports by submission timestamp in descending order (newest first).
     * Updates the reportListState StateFlow with the result.
     */
    fun fetchUserReports() {
        viewModelScope.launch {
            _reportListState.value = ReportListState.Loading // Set loading state
            val currentUser = auth.currentUser

            // Ensure a user is authenticated
            if (currentUser == null) {
                Log.w("ReportListVM", "Cannot fetch reports: No authenticated user found.")
                _reportListState.value = ReportListState.Error("User not authenticated. Please log in.")
                return@launch
            }

            try {
                Log.d("ReportListVM", "Fetching reports from Firestore for user UID: ${currentUser.uid}")

                // Query Firestore for complaints belonging to the current user, ordered by submission time
                val querySnapshot = db.collection("complaints")
                    .whereEqualTo("userId", currentUser.uid) // Filter by the current user's ID
                    .orderBy("submissionTimestamp", Query.Direction.DESCENDING) // Newest reports first
                    .get()
                    .await() // Suspend until the task is complete

                if (querySnapshot.isEmpty) {
                    // No documents found matching the query
                    Log.d("ReportListVM", "No reports found for user: ${currentUser.uid}")
                    _reportListState.value = ReportListState.Empty
                } else {
                    // Map Firestore documents to ComplaintData objects, including the document ID
                    val reports = querySnapshot.documents.mapNotNull { document ->
                        // Attempt to convert the document's data to a ComplaintData object
                        val complaint = document.toObject(ComplaintData::class.java)
                        // Manually set the 'id' field of our ComplaintData object
                        // with the Firestore document's ID.
                        complaint?.apply { id = document.id }
                    }
                    Log.d("ReportListVM", "Successfully fetched ${reports.size} reports for user: ${currentUser.uid}")
                    _reportListState.value = ReportListState.Success(reports)
                }
            } catch (e: Exception) {
                // Handle any exceptions during the Firestore query (network issues, permission errors, etc.)
                Log.e("ReportListVM", "Error fetching reports from Firestore", e)
                _reportListState.value = ReportListState.Error(e.message ?: "An unexpected error occurred while fetching reports.")
            }
        }
    }

    /**
     * Public function to allow the UI to trigger a refresh of the reports list.
     */
    fun refreshReports() {
        Log.d("ReportListVM", "Manual refresh triggered.")
        fetchUserReports()
    }
}