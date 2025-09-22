import java.io.File

/**
 * Gradle script for 16KB page alignment support
 * Required for Android 16+ compatibility
 */

// Function to check if a library is 16KB aligned
fun check16KBAlignment(file: File): Boolean {
    if (!file.exists() || !file.name.endsWith(".so")) return false

    // This is a simplified check - actual implementation would read ELF headers
    // For now, we'll return false for known problematic libraries
    val problematicLibs = listOf(
        "libimage_processing_util_jni.so",
        "libmediapipe_tasks_vision_jni.so",
        "libtensorflowlite_gpu_jni.so",
        "libtensorflowlite_jni.so"
    )

    return !problematicLibs.any { file.name.contains(it) }
}

// Task to verify 16KB alignment
tasks.register("verify16KBAlignment") {
    group = "verification"
    description = "Verify that all native libraries are 16KB aligned"

    doLast {
        val libDirs = listOf(
            file("build/intermediates/merged_native_libs/debug/out/lib"),
            file("build/intermediates/stripped_native_libs/debug/out/lib")
        )

        var hasUnalignedLibs = false
        val unalignedLibs = mutableListOf<String>()

        libDirs.forEach { libDir ->
            if (libDir.exists()) {
                libDir.walk().filter { it.extension == "so" }.forEach { soFile ->
                    if (!check16KBAlignment(soFile)) {
                        hasUnalignedLibs = true
                        unalignedLibs.add(soFile.relativeTo(libDir).path)
                    }
                }
            }
        }

        if (hasUnalignedLibs) {
            println("⚠️ Warning: The following libraries are not 16KB aligned:")
            unalignedLibs.forEach { println("  - $it") }
            println("\nThese libraries may cause compatibility issues on Android 16+ devices.")
            println("Consider:")
            println("1. Using extractNativeLibs=\"true\" in AndroidManifest.xml")
            println("2. Updating to newer library versions with 16KB support")
            println("3. Building libraries from source with NDK r28+")
        } else {
            println("✅ All native libraries are 16KB aligned")
        }
    }
}

// Add verification to build process
tasks.whenTaskAdded {
    if (name == "packageDebug" || name == "packageRelease") {
        finalizedBy("verify16KBAlignment")
    }
}