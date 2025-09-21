plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("kotlin-kapt")
    id("jacoco")
}

android {
    namespace = "com.posecoach.testing"
    compileSdk = 34

    defaultConfig {
        minSdk = 24
        targetSdk = 34

        testInstrumentationRunner = "com.posecoach.testing.PoseCoachTestRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        debug {
            enableUnitTestCoverage = true
            enableAndroidTestCoverage = true
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/gradle/incremental.annotation.processors"
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }

        execution = "ANDROIDX_TEST_ORCHESTRATOR"

        managedDevices {
            devices {
                create("pixel2api30") {
                    device = "Pixel 2"
                    apiLevel = 30
                    systemImageSource = "aosp"
                }
                create("pixel4api31") {
                    device = "Pixel 4"
                    apiLevel = 31
                    systemImageSource = "aosp"
                }
                create("pixel6api33") {
                    device = "Pixel 6"
                    apiLevel = 33
                    systemImageSource = "aosp"
                }
            }
            groups {
                create("phone") {
                    targetDevices.add(devices["pixel2api30"])
                    targetDevices.add(devices["pixel4api31"])
                    targetDevices.add(devices["pixel6api33"])
                }
            }
        }
    }
}

jacoco {
    toolVersion = "0.8.10"
}

dependencies {
    // Project modules
    implementation(project(":app"))
    implementation(project(":core-pose"))
    implementation(project(":core-geom"))
    implementation(project(":suggestions-api"))

    // Android Core
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")

    // Kotlin & Coroutines
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.9.20")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")

    // Testing Framework Core
    api("junit:junit:4.13.2")
    api("org.jetbrains.kotlin:kotlin-test-junit:1.9.20")
    api("androidx.test:core:1.5.0")
    api("androidx.test:core-ktx:1.5.0")
    api("androidx.test:runner:1.5.2")
    api("androidx.test:rules:1.5.0")
    api("androidx.test.ext:junit:1.1.5")
    api("androidx.test.ext:junit-ktx:1.1.5")

    // Espresso & UI Testing
    api("androidx.test.espresso:espresso-core:3.5.1")
    api("androidx.test.espresso:espresso-contrib:3.5.1")
    api("androidx.test.espresso:espresso-intents:3.5.1")
    api("androidx.test.espresso:espresso-accessibility:3.5.1")
    api("androidx.test.espresso:espresso-web:3.5.1")
    api("androidx.test.espresso:espresso-idling-resource:3.5.1")

    // Compose Testing
    api("androidx.compose.ui:ui-test-junit4:1.5.4")
    api("androidx.compose.ui:ui-test-manifest:1.5.4")

    // Mockito & Mocking
    api("org.mockito:mockito-core:5.6.0")
    api("org.mockito:mockito-android:5.6.0")
    api("org.mockito.kotlin:mockito-kotlin:5.1.0")
    api("io.mockk:mockk:1.13.8")
    api("io.mockk:mockk-android:1.13.8")

    // Truth Assertions
    api("com.google.truth:truth:1.1.5")

    // Robolectric for Unit Tests
    api("org.robolectric:robolectric:4.11.1")

    // Turbine for Flow Testing
    api("app.cash.turbine:turbine:1.0.0")

    // Test Orchestrator
    androidTestUtil("androidx.test:orchestrator:1.4.2")

    // Performance Testing
    api("androidx.benchmark:benchmark-junit4:1.2.0")
    api("androidx.benchmark:benchmark-macro-junit4:1.2.0")
    api("androidx.test.uiautomator:uiautomator:2.2.0")

    // Memory Leak Detection
    api("com.squareup.leakcanary:leakcanary-android-instrumentation:2.12")

    // Network Testing
    api("com.squareup.okhttp3:mockwebserver:4.12.0")
    api("com.github.tomakehurst:wiremock-jre8:2.35.0")

    // Database Testing
    api("androidx.room:room-testing:2.6.0")

    // Security Testing
    api("org.owasp:dependency-check-gradle:8.4.3")

    // Property-based Testing
    api("io.kotest:kotest-property:5.7.2")
    api("io.kotest:kotest-framework-api:5.7.2")

    // JSON Testing
    api("org.skyscreamer:jsonassert:1.5.1")

    // Accessibility Testing
    api("com.google.android.apps.common.testing.accessibility.framework:accessibility-test-framework:4.0.0")

    // Camera Testing
    api("androidx.camera:camera-testing:1.3.0")

    // Image Testing
    api("androidx.test:screenshot:1.0.0-alpha01")

    // Logging
    implementation("com.jakewharton.timber:timber:5.0.1")

    // JSON
    implementation("com.google.code.gson:gson:2.10.1")

    // Advanced Testing
    api("com.linkedin.testbutler:test-butler-library:2.2.1")
    api("tools.fastlane.screengrab:screengrab:2.1.1")
}

tasks.register<JacocoReport>("jacocoTestReport") {
    dependsOn("testDebugUnitTest", "createDebugCoverageReport")

    reports {
        xml.required.set(true)
        html.required.set(true)
    }

    val fileFilter = listOf(
        "**/R.class", "**/R\$*.class", "**/BuildConfig.*", "**/Manifest*.*",
        "**/*Test*.*", "android/**/*.*"
    )

    val debugTree = fileTree("${buildDir}/tmp/kotlin-classes/debug") {
        exclude(fileFilter)
    }

    val mainSrc = "${project.projectDir}/src/main/kotlin"

    sourceDirectories.setFrom(files(mainSrc))
    classDirectories.setFrom(files(debugTree))
    executionData.setFrom(fileTree(buildDir) {
        include("jacoco/testDebugUnitTest.exec", "outputs/code_coverage/debugAndroidTest/connected/**/*.ec")
    })
}

tasks.withType<Test> {
    configure<JacocoTaskExtension> {
        isIncludeNoLocationClasses = true
        excludes = listOf("jdk.internal.*")
    }
}