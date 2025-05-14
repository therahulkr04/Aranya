package com.project.aranya.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.project.aranya.data.ComplaintData // Your ComplaintData import
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

// Using the ReportListState sealed class defined in ReportListViewModel.kt
// or you can redefine it here if you prefer to keep them separate.
// For simplicity, we are reusing it.
// sealed class ReportListState {
//     object Loading : ReportListState()
//     data class Success(val reports: List<ComplaintData>) : ReportListState()
//     object Empty : ReportListState()
//     data class Error(val message: String) : ReportListState()
// }

/**
 * ViewModel for the Admin Dashboard screen, specifically for listing ALL complaints.
 * Fetches all complaint documents from Firestore.
 */
// If using Hilt, annotate with @HiltViewModel
class AdminComplaintListViewModel : ViewModel() {

    private val db = FirebaseFirestore.getInstance()

    // StateFlow to hold and expose the list of all reports and the current fetching state.
    // We are reusing the ReportListState sealed class for simplicity.
    private val _allReportsState = MutableStateFlow<ReportListState>(ReportListState.Loading)
    val allReportsState: StateFlow<ReportListState> = _allReportsState

    init {
        // Automatically fetch all complaints when the ViewModel is created.
        fetchAllComplaints()
    }

    /**
     * Fetches all complaints from the "complaints" collection in Firestore.
     * Orders them by submission timestamp in descending order.
     * Updates the allReportsState StateFlow with the result.
     */
    fun fetchAllComplaints() {
        viewModelScope.launch {
            _allReportsState.value = ReportListState.Loading // Set loading state
            try {
                Log.d("AdminComplaintListVM", "Fetching all complaints from Firestore.")

                // Query Firestore for all documents in the "complaints" collection.
                val querySnapshot = db.collection("complaints")
                    .orderBy("submissionTimestamp", Query.Direction.DESCENDING) // Show newest first
                    // No .whereEqualTo("userId", ...) filter, as admins see all.
                    .get()
                    .await() // Suspend until the task is complete

                if (querySnapshot.isEmpty) {
                    // No documents found in the entire collection.
                    Log.d("AdminComplaintListVM", "No complaints found in the database.")
                    _allReportsState.value = ReportListState.Empty
                } else {
                    // Map Firestore documents to ComplaintData objects, including the document ID.
                    val reports = querySnapshot.documents.mapNotNull { document ->
                        val complaint = document.toObject(ComplaintData::class.java)
                        // Manually set the 'id' field of our ComplaintData object.
                        complaint?.apply { id = document.id }
                    }
                    Log.d("AdminComplaintListVM", "Successfully fetched ${reports.size} total complaints.")
                    _allReportsState.value = ReportListState.Success(reports)
                }
            } catch (e: Exception) {
                // Handle any exceptions during the Firestore query.
                // This could be a Firestore Security Rule permission error if admins
                // are not granted broad read access to the 'complaints' collection.
                Log.e("AdminComplaintListVM", "Error fetching all complaints from Firestore", e)
                _allReportsState.value = ReportListState.Error(e.message ?: "Failed to fetch all complaints.")
            }
        }
    }

    /**
     * Public function to allow the UI to trigger a refresh of the all complaints list.
     */
    fun refreshAllComplaints() {
        Log.d("AdminComplaintListVM", "Manual refresh of all complaints triggered.")
        fetchAllComplaints() // Re-run the fetching logic
    }
}