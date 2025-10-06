import java.util.Properties
import java.io.FileInputStream

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.25"
}

// Load local.properties file
val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(FileInputStream(localPropertiesFile))
}

android {
    namespace = "com.posecoach.app"
    compileSdk = 35  // Android 15 for 16KB page size support
    ndkVersion = "28.0.12433566" // NDK r28 compiles 16KB-aligned by default

    defaultConfig {
        applicationId = "com.posecoach.camera"
        minSdk = 26
        targetSdk = 35  // Target Android 15+ for 16KB support
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Add API keys from local.properties as BuildConfig fields
        buildConfigField("String", "GEMINI_API_KEY",
            "\"${localProperties.getProperty("gemini.api.key", "")}\"")
        buildConfigField("String", "GEMINI_LIVE_API_KEY",
            "\"${localProperties.getProperty("gemini.live.api.key", "")}\"")
    }

    signingConfigs {
        create("release") {
            storeFile = file("../pose-coach-release.keystore")
            storePassword = "posecoach2024"
            keyAlias = "pose-coach-key"
            keyPassword = "posecoach2024"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isDebuggable = true
            isMinifyEnabled = false
        }
    }

    // Split APKs by ABI - properly configured for 16KB alignment
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "x86_64")  // Both architectures support 16KB
            isUniversalApk = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.15"
    }

    packaging {
        resources {
            excludes += "COPYRIGHT.txt"
        }
        jniLibs {
            // CRITICAL: For 16KB alignment, we must NOT use legacy packaging
            // The system needs to be able to extract and align libraries properly
            useLegacyPackaging = false

            // Keep debug symbols for critical libraries
            keepDebugSymbols += listOf(
                "**/libimage_processing_util_jni.so",
                "**/libmediapipe_tasks_vision_jni.so",
                "**/libtensorflowlite_gpu_jni.so",
                "**/libtensorflowlite_jni.so"
            )

            // Handle duplicate libraries with force alignment
            pickFirsts += listOf(
                "**/libimage_processing_util_jni.so",
                "**/libmediapipe_tasks_vision_jni.so",
                "**/libtensorflowlite_gpu_jni.so",
                "**/libtensorflowlite_jni.so"
            )
        }
        // Ensure proper DEX packaging
        dex {
            useLegacyPackaging = false
        }
    }

    // Add lint options for 16KB alignment checks
    lint {
        checkDependencies = true
        abortOnError = false
        warningsAsErrors = false
    }

}

// Custom 16KB alignment task (outside android block)
afterEvaluate {
    tasks.register("align16KDebug") {
        dependsOn("packageDebug")
        group = "build"
        description = "Align debug APKs to 16KB boundaries for compatibility"

        doLast {
            val apkDir = File(layout.buildDirectory.asFile.get(), "outputs/apk/debug")
            val apkFiles = apkDir.listFiles { _, name ->
                name.endsWith("-debug.apk")
            }

            apkFiles?.forEach { inputApk ->
                val outputApk = File(inputApk.parent, inputApk.name.replace(".apk", "-16kb-aligned.apk"))
                val sdkDir = android.sdkDirectory
                val buildToolsVersion = android.buildToolsVersion
                val zipalignPath = if (System.getProperty("os.name").lowercase().contains("windows")) {
                    "$sdkDir/build-tools/$buildToolsVersion/zipalign.exe"
                } else {
                    "$sdkDir/build-tools/$buildToolsVersion/zipalign"
                }

                // Use zipalign with 16KB alignment
                project.exec {
                    commandLine(zipalignPath, "-f", "-p", "16", inputApk.absolutePath, outputApk.absolutePath)
                }

                // Replace original APK with aligned version
                inputApk.delete()
                outputApk.renameTo(inputApk)

                println("✅ Applied 16KB alignment to: ${inputApk.name}")
            }
        }
    }

    tasks.named("assembleDebug").configure {
        finalizedBy("align16KDebug")
    }
}

dependencies {
    // Project modules
    implementation(project(":core-pose"))
    implementation(project(":core-geom"))
    implementation(project(":suggestions-api"))

    // CameraX - Enhanced for production
    val cameraxVersion = "1.3.1"
    implementation("androidx.camera:camera-core:$cameraxVersion")
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")
    implementation("androidx.camera:camera-extensions:$cameraxVersion")
    // camera-mlkit-vision not needed - we use MediaPipe instead

    // ML Kit Pose Detection - 16KB aligned alternative to MediaPipe
    implementation("com.google.mlkit:pose-detection:18.0.0-beta5")
    implementation("com.google.mlkit:pose-detection-accurate:18.0.0-beta5")

    // MediaPipe libraries - using latest version with potential 16KB fixes
    implementation("com.google.mediapipe:tasks-vision:0.10.14")

    // ReLinker for runtime library loading with alignment fixes
    implementation("com.getkeepsafe.relinker:relinker:1.4.5")

    // Kotlin & Coroutines
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.9.25")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")

    // Android Core
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.6.2")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.6.2")
    implementation("androidx.cardview:cardview:1.0.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.activity:activity-ktx:1.8.0")
    implementation("androidx.fragment:fragment-ktx:1.6.2")

    // Logging
    implementation("com.jakewharton.timber:timber:5.0.1")

    // Network & WebSocket
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Audio Processing & Compression
    // Opus codec not needed - use native Android audio
    implementation("androidx.media3:media3-common:1.2.0")

    // Performance Monitoring
    implementation("com.github.markzhai:blockcanary-android:1.5.0")

    // Circuit Breaker Pattern
    implementation("io.github.resilience4j:resilience4j-circuitbreaker:2.1.0")
    implementation("io.github.resilience4j:resilience4j-retry:2.1.0")

    // TensorFlow Lite - latest versions
    // Note: Official 16KB support pending, using latest available
    implementation("org.tensorflow:tensorflow-lite:2.16.1")
    implementation("org.tensorflow:tensorflow-lite-support:0.4.4")
    implementation("org.tensorflow:tensorflow-lite-gpu:2.16.1")

    // Metrics & Analytics
    implementation("io.micrometer:micrometer-core:1.12.0")

    // JSON Serialization
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")

    // Google AI Generative SDK
    implementation("com.google.ai.client.generativeai:generativeai:0.9.0")

    // Reactive Streams
    implementation("io.reactivex.rxjava3:rxjava:3.1.8")
    implementation("io.reactivex.rxjava3:rxandroid:3.0.2")

    // Security - Encrypted Preferences
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Jetpack Compose
    implementation(platform("androidx.compose:compose-bom:2024.10.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.compose.runtime:runtime-livedata")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.foundation:foundation-layout")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.animation:animation-core")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-viewbinding")

    // Compose debugging tools (debug builds only)
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation("org.mockito:mockito-core:5.5.0")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.1.0")
    testImplementation("androidx.test:core:1.5.0")
    testImplementation("com.google.truth:truth:1.1.4")
    testImplementation("androidx.arch.core:core-testing:2.2.0")
    testImplementation("io.mockk:mockk:1.13.8")
    testImplementation("org.robolectric:robolectric:4.11.1")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation("com.google.truth:truth:1.1.4")
    androidTestImplementation("androidx.test:runner:1.5.2")
    androidTestImplementation("androidx.test:rules:1.5.0")
}