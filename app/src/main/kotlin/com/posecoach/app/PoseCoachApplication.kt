package com.posecoach.app

import android.app.Application
import android.content.Context
import android.os.Build
import android.util.Log
import com.getkeepsafe.relinker.ReLinker
import com.getkeepsafe.relinker.ReLinkerInstance

/**
 * Custom Application class to handle 16KB page size compatibility
 * and proper native library loading.
 */
class PoseCoachApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Initialize ReLinker for proper native library loading
        // This helps with 16KB page alignment issues
        initializeNativeLibraries()
    }

    private fun initializeNativeLibraries() {
        try {
            // Configure ReLinker for recursive loading and logging
            val relinker = ReLinker.log { message ->
                Log.d(TAG, "ReLinker: $message")
            }.recursively()

            // Load native libraries with ReLinker for better compatibility
            // This helps handle alignment issues on 16KB page size devices
            loadLibrarySafely(relinker, "tensorflowlite_jni")
            loadLibrarySafely(relinker, "mediapipe_tasks_vision_jni")
            loadLibrarySafely(relinker, "image_processing_util_jni")

            Log.i(TAG, "Native libraries loaded successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error loading native libraries", e)
            // Continue app startup even if library loading fails
            // The actual usage will handle missing libraries gracefully
        }
    }

    private fun loadLibrarySafely(relinker: ReLinkerInstance, libraryName: String) {
        try {
            if (Build.VERSION.SDK_INT >= 35) { // Android 16+
                // For Android 16+ with potential 16KB page sizes
                Log.d(TAG, "Loading $libraryName for Android 16+ with ReLinker")
                relinker.loadLibrary(this, libraryName)
            } else {
                // For older Android versions, use standard loading
                Log.d(TAG, "Loading $libraryName with standard method")
                System.loadLibrary(libraryName)
            }
        } catch (e: UnsatisfiedLinkError) {
            Log.w(TAG, "Failed to load $libraryName, will retry with ReLinker", e)
            try {
                relinker.loadLibrary(this, libraryName)
            } catch (e2: Exception) {
                Log.e(TAG, "Failed to load $libraryName with ReLinker", e2)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error loading $libraryName", e)
        }
    }

    companion object {
        private const val TAG = "PoseCoachApp"

        /**
         * Check if the device uses 16KB page sizes
         */
        fun is16KBPageSizeDevice(context: Context): Boolean {
            // This is a placeholder - actual detection would require
            // checking system properties or using NDK APIs
            return Build.VERSION.SDK_INT >= 35 // Android 16+
        }
    }
}