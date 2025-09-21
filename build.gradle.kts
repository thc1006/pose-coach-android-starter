// Top-level build file with quality tools and CI/CD support
plugins {
    id("com.android.application") version "8.1.4" apply false
    id("com.android.library") version "8.1.4" apply false
    id("org.jetbrains.kotlin.android") version "1.9.20" apply false

    // Code quality plugins
    id("org.jlleitschuh.gradle.ktlint") version "11.6.1" apply false
    id("io.gitlab.arturbosch.detekt") version "1.23.4" apply false

    // Coverage reporting
    id("org.jacoco") version "0.8.10" apply false

    // Dependency analysis
    id("com.github.ben-manes.versions") version "0.50.0"
    id("com.github.hierynomus.license") version "0.16.1" apply false

    // Performance benchmarking
    id("androidx.benchmark") version "1.2.2" apply false
}

// Apply to all projects
allprojects {
    repositories {
        google()
        mavenCentral()
    }
}

// Dependency version management
extra.apply {
    set("compileSdk", 34)
    set("minSdk", 24)
    set("targetSdk", 34)
    set("javaVersion", JavaVersion.VERSION_17)
    set("kotlinCompilerExtensionVersion", "1.5.8")

    // Dependency versions
    set("cameraxVersion", "1.3.1")
    set("coroutinesVersion", "1.7.3")
    set("lifecycleVersion", "2.6.2")
    set("materialVersion", "1.10.0")
    set("mediapipeVersion", "0.10.9")
}

// Quality gates configuration
subprojects {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")
    apply(plugin = "io.gitlab.arturbosch.detekt")
    apply(plugin = "jacoco")
    apply(plugin = "com.github.hierynomus.license")

    // Configure ktlint
    configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
        android.set(true)
        ignoreFailures.set(false)
        reporters {
            reporter(org.jlleitschuh.gradle.ktlint.reporter.ReporterType.PLAIN)
            reporter(org.jlleitschuh.gradle.ktlint.reporter.ReporterType.CHECKSTYLE)
            reporter(org.jlleitschuh.gradle.ktlint.reporter.ReporterType.HTML)
        }
        filter {
            exclude("**/generated/**")
            exclude("**/build/**")
        }
    }

    // Configure detekt
    configure<io.gitlab.arturbosch.detekt.extensions.DetektExtension> {
        config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
        buildUponDefaultConfig = true
        autoCorrect = false
        ignoreFailures = false

        reports {
            html.required.set(true)
            xml.required.set(true)
            txt.required.set(false)
            sarif.required.set(true)
        }
    }

    // Configure JaCoCo
    configure<JacocoPluginExtension> {
        toolVersion = "0.8.10"
    }

    // License checking
    configure<com.hierynomus.gradle.license.LicenseExtension> {
        header = file("$rootDir/config/license/HEADER")
        strictCheck = true
        exclude("**/*.json")
        exclude("**/*.xml")
        exclude("**/*.properties")
        exclude("**/build/**")
        exclude("**/generated/**")
    }

    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions {
            jvmTarget = "17"
            freeCompilerArgs += listOf(
                "-opt-in=kotlin.RequiresOptIn",
                "-Xjvm-default=all"
            )
        }
    }
}

// Custom tasks for CI/CD
tasks.register("qualityCheck") {
    group = "verification"
    description = "Run all quality checks"
    dependsOn(":ktlintCheck", ":detekt", ":lint")
}

tasks.register("securityCheck") {
    group = "verification"
    description = "Run security analysis"
    doLast {
        println("Running security checks...")
        // Add custom security validation here
    }
}

// Coverage aggregation task
tasks.register<JacocoReport>("jacocoMergedReport") {
    group = "verification"
    description = "Generate merged coverage report for all modules"

    dependsOn(subprojects.map { "${it.path}:testDebugUnitTest" })

    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
    }

    val fileFilter = listOf(
        "**/R.class",
        "**/R$*.class",
        "**/BuildConfig.*",
        "**/Manifest*.*",
        "**/*Test*.*",
        "**/databinding/**/*.*",
        "**/generated/**/*.*"
    )

    val kotlinDebugTree = fileTree("dir" to "${project.buildDir}/tmp/kotlin-classes/debug", "excludes" to fileFilter)
    val javaDebugTree = fileTree("dir" to "${project.buildDir}/intermediates/javac/debug/classes", "excludes" to fileFilter)

    classDirectories.setFrom(files(kotlinDebugTree, javaDebugTree))

    val sourceSets = subprojects.map { project ->
        "${project.projectDir}/src/main/kotlin"
    }
    sourceDirectories.setFrom(files(sourceSets))

    val executionDataFiles = fileTree("dir" to project.buildDir, "includes" to listOf("**/*.exec", "**/*.ec"))
    executionData.setFrom(executionDataFiles)
}

// Coverage verification
tasks.register<JacocoCoverageVerification>("jacocoCoverageVerification") {
    group = "verification"
    description = "Verify code coverage meets minimum threshold"

    dependsOn("jacocoMergedReport")

    violationRules {
        rule {
            limit {
                minimum = "0.80".toBigDecimal()  // 80% minimum coverage
            }
        }

        rule {
            element = "CLASS"
            limit {
                counter = "BRANCH"
                value = "COVEREDRATIO"
                minimum = "0.75".toBigDecimal()  // 75% branch coverage
            }
        }
    }
}

// Performance baseline tracking
tasks.register("updatePerformanceBaseline") {
    group = "benchmarking"
    description = "Update performance baseline from latest benchmark results"
    doLast {
        val baselineFile = file("performance_baselines.json")
        if (!baselineFile.exists()) {
            baselineFile.writeText("""
                {
                    "pose_detection_target_fps": 30,
                    "max_apk_size_mb": 100,
                    "max_memory_usage_mb": 256,
                    "max_api_response_time_ms": 2000,
                    "max_ui_frame_time_ms": 16
                }
            """.trimIndent())
            println("Created performance baseline file")
        }
    }
}

// CI/CD helper tasks
tasks.register("ciPreparation") {
    group = "ci"
    description = "Prepare environment for CI/CD"
    doLast {
        mkdir("${project.buildDir}/reports")
        mkdir("${project.buildDir}/test-results")
        println("CI environment prepared")
    }
}

tasks.register("generateBuildInfo") {
    group = "ci"
    description = "Generate build information for release"
    doLast {
        val buildInfo = mapOf(
            "buildTime" to System.currentTimeMillis(),
            "gitCommit" to System.getenv("GITHUB_SHA") ?: "unknown",
            "buildNumber" to System.getenv("GITHUB_RUN_NUMBER") ?: "local",
            "branch" to System.getenv("GITHUB_REF_NAME") ?: "unknown"
        )

        val buildInfoFile = file("${project.buildDir}/build-info.json")
        buildInfoFile.writeText(com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(buildInfo))
        println("Build info generated: ${buildInfoFile.absolutePath}")
    }
}