package com.project.aranya.navigation

sealed class Screen(val route: String) {
    object Splash: Screen("")
    object Login : Screen("login")
    object SignUp : Screen("signup")
    object Home : Screen("home") // User Dashboard
    object ReportList : Screen("report_list")
    object SubmitComplaint : Screen("submit_complaint")
    object Profile : Screen("profile")
    object MyReports : Screen("my_reports")
    object AdminDashboard : Screen("admin_dashboard") // Admin specific
    // Add other screens as needed
    object ReportDetail : Screen("report_detail/{reportId}") {
        fun createRoute(reportId: String) = "report_detail/$reportId" // Helper to build the route
}
}