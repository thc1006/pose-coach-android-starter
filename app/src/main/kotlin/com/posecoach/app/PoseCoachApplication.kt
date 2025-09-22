package com.posecoach.app

import android.app.Application
import android.content.Context
import android.os.Build
import android.util.Log
import com.getkeepsafe.relinker.ReLinker
import com.getkeepsafe.relinker.ReLinkerInstance
import timber.log.Timber

/**
 * Custom Application class to handle 16KB page size compatibility
 * and proper native library loading.
 */
class PoseCoachApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Initialize Timber for logging
        initializeLogging()

        // Initialize ReLinker for proper native library loading
        // This helps with 16KB page alignment issues
        initializeNativeLibraries()
    }

    private fun initializeLogging() {
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
            Timber.i("Timber logging initialized in DEBUG mode")
        } else {
            // In release, filter out verbose and debug logs
            Timber.plant(object : Timber.Tree() {
                override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
                    if (priority >= Log.INFO) {
                        Log.println(priority, tag ?: "PoseCoach", message)
                    }
                }
            })
        }
        Timber.i("PoseCoachApplication started")
    }

    private fun initializeNativeLibraries() {
        // Try to load libraries regardless of 16KB alignment
        // The system's extractNativeLibs=true should help
        try {
            // Configure ReLinker for recursive loading and logging
            val relinker = ReLinker.log { message ->
                Timber.d("ReLinker: $message")
            }.recursively()

            // Try loading with ReLinker first
            var loadedWithReLinker = false
            try {
                relinker.loadLibrary(this, "tensorflowlite_jni")
                relinker.loadLibrary(this, "mediapipe_tasks_vision_jni")
                relinker.loadLibrary(this, "image_processing_util_jni")
                loadedWithReLinker = true
                Timber.i("Native libraries loaded successfully with ReLinker")
            } catch (e: Exception) {
                Timber.w(e, "ReLinker failed, trying standard loading")
            }

            // If ReLinker failed, try standard loading
            if (!loadedWithReLinker) {
                System.loadLibrary("tensorflowlite_jni")
                System.loadLibrary("mediapipe_tasks_vision_jni")
                System.loadLibrary("image_processing_util_jni")
                Timber.i("Native libraries loaded with System.loadLibrary")
            }

        } catch (e: Exception) {
            Timber.e(e, "Failed to load native libraries - MediaPipe may not work")
            // Don't block the app, let it try to run anyway
            // The pose detection will handle the error gracefully
        }
    }

    private fun loadLibrarySafely(relinker: ReLinkerInstance, libraryName: String) {
        try {
            if (Build.VERSION.SDK_INT >= 35) { // Android 16+
                // For Android 16+ with potential 16KB page sizes
                Timber.d("Loading $libraryName for Android 16+ with ReLinker")
                relinker.loadLibrary(this, libraryName)
            } else {
                // For older Android versions, use standard loading
                Timber.d("Loading $libraryName with standard method")
                System.loadLibrary(libraryName)
            }
        } catch (e: UnsatisfiedLinkError) {
            Timber.w(e, "Failed to load $libraryName, will retry with ReLinker")
            try {
                relinker.loadLibrary(this, libraryName)
            } catch (e2: Exception) {
                Timber.e(e2, "Failed to load $libraryName with ReLinker")
            }
        } catch (e: Exception) {
            Timber.e(e, "Unexpected error loading $libraryName")
        }
    }

    companion object {
        private const val TAG = "PoseCoachApp"

        /**
         * Check if the device uses 16KB page sizes
         */
        fun is16KBPageSizeDevice(@Suppress("UNUSED_PARAMETER") context: Context): Boolean {
            // SDK 36 (Android 15+ preview) may have 16KB page sizes
            // Pixel 7 with SDK 36 likely has this issue
            return Build.VERSION.SDK_INT >= 35 // Android 15+
        }

        /**
         * Check if running on problematic SDK version
         */
        fun isProblematicSDK(): Boolean {
            return Build.VERSION.SDK_INT == 36 // Specific issue with SDK 36
        }
    }
}