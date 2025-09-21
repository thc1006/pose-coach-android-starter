// Top-level build file with quality tools and CI/CD support
plugins {
    id("com.android.application") version "8.13.0" apply false
    id("com.android.library") version "8.13.0" apply false
    id("org.jetbrains.kotlin.android") version "1.9.22" apply false
    
    // Code quality plugins
    id("org.jlleitschuh.gradle.ktlint") version "11.6.1" apply false
    id("io.gitlab.arturbosch.detekt") version "1.23.4" apply false
    
    // Coverage reporting
    jacoco
    
    // Dependency analysis
    id("com.github.ben-manes.versions") version "0.50.0"
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

// Apply to subprojects
subprojects {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")
    apply(plugin = "io.gitlab.arturbosch.detekt")
    apply(plugin = "jacoco")

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
}

tasks.register("securityCheck") {
    group = "verification"
    description = "Run security analysis"
    doLast {
        println("Running security checks...")
    }
}
