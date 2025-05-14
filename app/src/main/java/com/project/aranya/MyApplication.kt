// Located in: app/src/main/java/com/project/aranya/
package com.project.aranya // Your package name

import android.app.Application
import android.util.Log
import com.cloudinary.android.MediaManager
import java.util.HashMap // Using java.util.HashMap

class MyApplication : Application() {

    companion object {
        private const val TAG = "AranyaApp" // Changed tag for clarity
    }

    override fun onCreate() {
        super.onCreate()

        // Initialize Cloudinary MediaManager
        initializeCloudinary()

        // Firebase usually initializes itself automatically via a ContentProvider
        // when google-services.json is present and Firebase dependencies are included.
        // Explicit initialization like FirebaseApp.initializeApp(this) is often not needed
        // unless you have specific multi-project needs or want to control initialization timing.
        Log.d(TAG, "MyApplication onCreate completed.")
    }

    private fun initializeCloudinary() {
        Log.i(TAG, "Attempting to initialize Cloudinary MediaManager...")

        // Configuration for Cloudinary.
        // For unsigned uploads, only the cloud_name is typically essential for MediaManager.init().
        // The specific unsigned upload preset name is provided during the upload request itself.
        val cloudinaryConfig = HashMap<String, String>()

        // **CRITICAL: Replace with your actual Cloudinary cloud name from your dashboard**
        val cloudName = "dgaj2x74q"

        if (cloudName == "YOUR_CLOUD_NAME" || cloudName.isBlank()) {
            Log.e(TAG, "Cloudinary cloud_name is not configured! Please replace 'YOUR_CLOUD_NAME' in MyApplication.kt.")
            // You might want to prevent further Cloudinary operations if this is not set,
            // or the SDK might throw an error later.
            // For now, we'll let it proceed, but uploads will fail.
        }
        cloudinaryConfig["cloud_name"] = cloudName

        // Optional: Enforce HTTPS for all SDK actions, generally a good idea.
        // cloudinaryConfig["secure"] = "true"

        // NOTE: For unsigned uploads (which is our current strategy),
        // API Key and API Secret SHOULD NOT be included here or in the client app.
        // They are not needed if you are using an unsigned upload preset.

        try {
            // Check if MediaManager has already been initialized to avoid crashes.
            // Accessing MediaManager.get() before init will throw an IllegalStateException.
            // So, we try to init, and catch if it was already done or if there's another issue.
            // A more robust check might involve a static flag, but this try-catch is common.
            MediaManager.init(this, cloudinaryConfig)
            Log.i(TAG, "Cloudinary MediaManager initialized successfully with cloud_name: $cloudName.")
        } catch (e: IllegalStateException) {
            // This exception is often thrown if MediaManager.init() is called more than once,
            // or if MediaManager.get() is called before init().
            // If it's just because it was already initialized, it's often okay.
            if (MediaManager.get().cloudinary != null && MediaManager.get().cloudinary.config.cloudName == cloudName) {
                Log.i(TAG, "Cloudinary MediaManager was already initialized with the correct cloud name.")
            } else {
                Log.e(TAG, "Cloudinary MediaManager initialization failed or was already initialized with a different config.", e)
            }
        } catch (e: Exception) {
            // Catch any other unexpected errors during Cloudinary initialization.
            Log.e(TAG, "A critical error occurred while initializing Cloudinary MediaManager.", e)
            // Consider how your app should behave if Cloudinary cannot be initialized.
            // Features relying on it (like file upload) will fail.
        }
    }
}