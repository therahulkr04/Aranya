# Aranya - Neighborhood Safety & Nuisance Reporting App

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
<!-- Add other badges if you have them: build status, code coverage, etc. -->

Aranya is a native Android application built with Kotlin and Jetpack Compose that empowers residents to easily report non-emergency safety concerns, minor nuisances, and bylaw infractions in their neighborhoods. The app facilitates a streamlined process for reporting issues and allows local authorities or community managers to track, manage, and respond to these reports, ultimately aiming to improve community well-being and safety.

<!-- Optional: Add a screenshot or a short GIF of the app in action here -->
<!-- <p align="center">
  <img src="path/to/your/screenshot.png" width="300">
</p> -->

## The Problem

Many minor neighborhood issues go unreported due to cumbersome processes, leading to slower response times and a disconnect between residents and local authorities. Aranya aims to bridge this gap by providing an accessible and efficient platform for civic engagement.

## Key Features

### User Features:
*   **User Registration & Authentication:** Secure sign-up and login using Firebase Authentication (Email/Password and Google Sign-In).
*   **Submit Report:**
    *   Categorize issues (e.g., Illegal Dumping, Potholes, Broken Streetlights, Noise Complaints).
    *   Provide a detailed description.
    *   Capture and attach photos/videos as evidence (uploaded to Cloudinary).
    *   Pinpoint exact location using GPS.
    *   Set an optional severity level.
*   **My Submitted Reports:** Users can view a list of their submitted reports and track their current status (Pending, In Progress, Resolved, etc.).
*   **Report Details:** View comprehensive details of a submitted report.
*   **Profile Management:** Basic user profile view and logout.
*   **Notifications (Planned):** Users will receive updates on the status of their reports.

### Admin Features (Conceptual - for local authorities/community managers):
*   **Secure Admin Login:** Differentiated from regular user login (e.g., via Firebase Custom Claims or Firestore-based roles).
*   **Admin Dashboard:** A central view to see all incoming reports from all users.
    *   Filter and sort reports (by status, category, date, location).
    *   (Planned: Map view of reports).
*   **Report Management:**
    *   View full details of any report.
    *   Update the status of a report (e.g., "Received," "Under Investigation," "Action Scheduled," "Resolved," "Rejected").
    *   Add internal admin remarks to reports.
*   **Analytics (Planned):** Basic insights into reported issues (e.g., common types, hotspots).

## Tech Stack & Architecture

*   **Language:** Kotlin
*   **UI:** Jetpack Compose (Android's modern declarative UI toolkit)
*   **Architecture:** MVVM (Model-View-ViewModel)
*   **Authentication:** Firebase Authentication
*   **Database (Metadata):** Cloud Firestore
*   **File Storage (Images/Videos/Documents):** Cloudinary (direct client-side upload using unsigned presets)
*   **Navigation:** Jetpack Navigation Compose
*   **Asynchronous Operations:** Kotlin Coroutines & Flows
*   **Image Loading (Client):** Coil
*   **(No Custom Backend API for submission):** The app communicates directly with Firebase services and Cloudinary for its core functionality.

## Project Structure (Brief Overview)

*   `app/src/main/java/com/project/aranya/`: Main application code.
    *   `data/`: Data models (e.g., `ComplaintData.kt`, `CloudinaryFileReference.kt`), Enums.
    *   `navigation/`: Navigation routes and graph setup (`Screen.kt`, `AppNavigationHost` in `MainActivity.kt`).
    *   `ui/screen/`: Composable screens (e.g., `LoginScreen.kt`, `HomeScreen.kt`, `SubmitComplaintScreen.kt`, etc.).
    *   `ui/theme/`: App theme (`AranyaTheme`, `Color.kt`, `Type.kt`).
    *   `ui/components/`: Reusable UI components (e.g., `ReportListItem.kt`).
    *   `viewmodel/`: ViewModels for each screen or feature (e.g., `AuthViewModel.kt`, `SubmitComplaintViewModel.kt`).
    *   `MyApplication.kt`: Application class for SDK initializations (e.g., Cloudinary).
*   `app/src/main/res/`: Android resources (drawables, mipmap, values).
*   `app/google-services.json`: Firebase project configuration file.

## Setup & Configuration

To run this project locally, you will need to:

1.  **Clone the repository:**
    ```bash
    git clone https://github.com/YOUR_USERNAME/Aranya.git # Replace with your repo URL
    cd Aranya
    ```
2.  **Set up Firebase:**
    *   Create a new Firebase project at [https://console.firebase.google.com/](https://console.firebase.google.com/).
    *   Add an Android app to your Firebase project with the package name `com.project.aranya`.
    *   Download the `google-services.json` file from your Firebase project settings and place it in the `app/` directory of this Android project.
    *   Enable **Firebase Authentication:**
        *   Enable "Email/Password" sign-in method.
        *   Enable "Google" sign-in method. Ensure you add your Android app's SHA-1 certificate fingerprints (debug and release) to both Firebase and the linked Google Cloud Platform project's OAuth 2.0 credentials for the Android client.
    *   Set up **Cloud Firestore:**
        *   Create a Firestore database in your Firebase project.
        *   Start in "Test mode" for initial development (allows open read/write) OR set up the security rules provided in `firestore.rules` (you should create this file).
    *   **(For Admin Functionality - Firestore Roles):**
        *   Create a `users` collection in Firestore.
        *   For users who should be admins, create a document with their Firebase Auth UID as the Document ID, and add a boolean field `isAdminRole: true`.
        *   Ensure your Firestore security rules (see `firestore.rules` example) protect who can set/modify this `isAdminRole` field.
    *   **(For Admin Functionality - Custom Claims - Alternative):**
        *   Alternatively, set custom claims (`admin: true`) for your admin users using the Firebase Admin SDK (via a backend script or Cloud Function). Your `AuthViewModel` would need to be configured to check claims instead of Firestore for roles if you choose this.

3.  **Set up Cloudinary:**
    *   Create a Cloudinary account at [https.cloudinary.com](https://cloudinary.com/).
    *   In your Cloudinary Dashboard, find your **Cloud Name**.
    *   Create an **Unsigned Upload Preset** (Settings > Upload > Upload presets > Add upload preset > Set Signing Mode to "Unsigned"). Note down the **name** of this preset.
    *   Update `MyApplication.kt` with your `YOUR_CLOUD_NAME`.
    *   Update `SubmitComplaintViewModel.kt` with `YOUR_UNSIGNED_UPLOAD_PRESET` name.

4.  **Google Sign-In Web Client ID (for Android):**
    *   In your Google Cloud Platform Console (linked to your Firebase project) -> APIs & Services -> Credentials.
    *   Find the OAuth 2.0 Client ID of type "Web application" (often auto-created by Firebase for Google Sign-In).
    *   Copy this **Web client ID**.
    *   In `AuthViewModel.kt`, replace `"YOUR_WEB_CLIENT_ID.apps.googleusercontent.com"` with this ID.

5.  **Build and Run:**
    *   Open the project in Android Studio.
    *   Let Gradle sync dependencies.
    *   Build and run on an emulator or physical device.

## Example Firestore Rules (`firestore.rules`)

```firestore
// (Paste the latest Firestore rules we developed here)
rules_version = '2';

service cloud.firestore {
  match /databases/{database}/documents {

    function isUserAdmin() {
      return request.auth != null &&
             exists(/databases/$(database)/documents/users/$(request.auth.uid)) &&
             get(/databases/$(database)/documents/users/$(request.auth.uid)).data.isAdminRole == true;
    }
    // ... rest of the rules ...
  }
}
```

(You would create a file named firestore.rules in your project root and paste the full rules into it for reference).

Contributing

Contributions are welcome! If you'd like to contribute, please follow these steps:

Fork the Project.

Create your Feature Branch (git checkout -b feature/AmazingFeature).

Commit your Changes (git commit -m 'Add some AmazingFeature').

Push to the Branch (git push origin feature/AmazingFeature).

Open a PullRequest.

Please ensure your code adheres to Kotlin coding conventions and includes tests where appropriate.

Future Enhancements / To-Do

Implement actual admin role assignment mechanism (beyond manual Firestore edits).

Notifications for report status updates.

Map view for admins to see report locations.

More detailed analytics for admins.

Enhanced UI/UX refinements.

Offline support for report submission.

User ability to edit/withdraw pending reports (if desired).

Direct camera capture for attachments.

License

Distributed under the MIT License. See LICENSE file for more information.
(Create a LICENSE file in your project root and add the MIT license text, or choose another license).

Contact

Rahul Kumar Singh - www.linkedin.com/in/therahulkr04 - therahulkr04@gmail.com
