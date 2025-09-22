package com.posecoach.app

import android.content.Context
import android.util.Log
import com.getkeepsafe.relinker.ReLinker
import com.getkeepsafe.relinker.ReLinkerInstance

object NativeLibraryLoader {
    private const val TAG = "NativeLibLoader"
    private val loadedLibraries = mutableSetOf<String>()

    fun loadLibraries(context: Context) {
        val libraries = listOf(
            "image_processing_util_jni",
            "tensorflowlite_jni",
            "tensorflowlite_gpu_jni"
        )

        val relinker = ReLinker.recursively()
            .log { message -> Log.d(TAG, "ReLinker: $message") }

        libraries.forEach { library ->
            if (!loadedLibraries.contains(library)) {
                try {
                    relinker.loadLibrary(context, library)
                    loadedLibraries.add(library)
                    Log.i(TAG, "Successfully loaded: $library")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to load $library: ${e.message}")
                    // Fallback to System.loadLibrary
                    try {
                        System.loadLibrary(library)
                        loadedLibraries.add(library)
                        Log.i(TAG, "Loaded via System.loadLibrary: $library")
                    } catch (fallbackError: Exception) {
                        Log.e(TAG, "Failed fallback for $library: ${fallbackError.message}")
                    }
                }
            }
        }
    }

    fun isLibraryLoaded(libraryName: String): Boolean {
        return loadedLibraries.contains(libraryName)
    }
}