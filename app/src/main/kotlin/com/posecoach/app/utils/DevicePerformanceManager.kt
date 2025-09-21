package com.posecoach.app.utils

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import androidx.camera.core.AspectRatio
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import android.util.Size
import timber.log.Timber

class DevicePerformanceManager(private val context: Context) {

    enum class PerformanceTier {
        HIGH,
        MEDIUM,
        LOW
    }

    data class PerformanceConfig(
        val tier: PerformanceTier,
        val targetResolution: Size,
        val targetFps: Int,
        val skipFrames: Int,
        val useGpuDelegate: Boolean,
        val maxPoses: Int,
        val enableOverlay: Boolean,
        val smoothingFactor: Float
    )

    companion object {
        private const val LOW_MEMORY_THRESHOLD = 1024L  // 1GB
        private const val MEDIUM_MEMORY_THRESHOLD = 2048L  // 2GB
        private const val HIGH_MEMORY_THRESHOLD = 3072L  // 3GB

        private val HIGH_PERF_CONFIG = PerformanceConfig(
            tier = PerformanceTier.HIGH,
            targetResolution = Size(1280, 720),
            targetFps = 30,
            skipFrames = 0,
            useGpuDelegate = true,
            maxPoses = 1,
            enableOverlay = true,
            smoothingFactor = 0.3f
        )

        private val MEDIUM_PERF_CONFIG = PerformanceConfig(
            tier = PerformanceTier.MEDIUM,
            targetResolution = Size(960, 540),
            targetFps = 24,
            skipFrames = 1,
            useGpuDelegate = true,
            maxPoses = 1,
            enableOverlay = true,
            smoothingFactor = 0.5f
        )

        private val LOW_PERF_CONFIG = PerformanceConfig(
            tier = PerformanceTier.LOW,
            targetResolution = Size(640, 480),
            targetFps = 15,
            skipFrames = 2,
            useGpuDelegate = false,
            maxPoses = 1,
            enableOverlay = false,
            smoothingFactor = 0.7f
        )
    }

    private var currentConfig: PerformanceConfig? = null
    private var frameCounter = 0

    fun detectPerformanceTier(): PerformanceTier {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)

        val totalMemoryMB = memInfo.totalMem / (1024 * 1024)
        val availableMemoryMB = memInfo.availMem / (1024 * 1024)
        val cpuCores = Runtime.getRuntime().availableProcessors()

        Timber.d("Device specs: RAM: ${totalMemoryMB}MB, Available: ${availableMemoryMB}MB, CPU cores: $cpuCores")

        val memoryScore = when {
            totalMemoryMB >= HIGH_MEMORY_THRESHOLD -> 3
            totalMemoryMB >= MEDIUM_MEMORY_THRESHOLD -> 2
            else -> 1
        }

        val cpuScore = when {
            cpuCores >= 8 -> 3
            cpuCores >= 4 -> 2
            else -> 1
        }

        val apiScore = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> 3  // Android 12+
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> 2  // Android 10+
            else -> 1
        }

        val totalScore = (memoryScore + cpuScore + apiScore) / 3.0f

        return when {
            totalScore >= 2.5f -> PerformanceTier.HIGH
            totalScore >= 1.5f -> PerformanceTier.MEDIUM
            else -> PerformanceTier.LOW
        }.also {
            Timber.i("Performance tier detected: $it (score: $totalScore)")
        }
    }

    fun getOptimalConfig(): PerformanceConfig {
        if (currentConfig == null) {
            val tier = detectPerformanceTier()
            currentConfig = when (tier) {
                PerformanceTier.HIGH -> HIGH_PERF_CONFIG
                PerformanceTier.MEDIUM -> MEDIUM_PERF_CONFIG
                PerformanceTier.LOW -> LOW_PERF_CONFIG
            }
        }
        return currentConfig!!
    }

    fun shouldProcessFrame(): Boolean {
        val config = getOptimalConfig()
        if (config.skipFrames == 0) return true

        val shouldProcess = frameCounter % (config.skipFrames + 1) == 0
        frameCounter++
        return shouldProcess
    }

    fun configureCameraAnalyzer(builder: ImageAnalysis.Builder): ImageAnalysis.Builder {
        val config = getOptimalConfig()

        val resolutionSelector = ResolutionSelector.Builder()
            .setResolutionStrategy(
                ResolutionStrategy(
                    config.targetResolution,
                    ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                )
            )
            .setAspectRatioStrategy(
                AspectRatioStrategy(
                    AspectRatio.RATIO_16_9,
                    AspectRatioStrategy.FALLBACK_RULE_AUTO
                )
            )
            .build()

        builder.setResolutionSelector(resolutionSelector)

        when (config.tier) {
            PerformanceTier.HIGH -> {
                builder.setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                builder.setImageQueueDepth(2)
            }
            PerformanceTier.MEDIUM -> {
                builder.setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                builder.setImageQueueDepth(1)
            }
            PerformanceTier.LOW -> {
                builder.setBackpressureStrategy(ImageAnalysis.STRATEGY_BLOCK_PRODUCER)
                builder.setImageQueueDepth(1)
            }
        }

        return builder
    }

    fun adaptToPerformance(
        inferenceTimeMs: Long,
        targetFrameTimeMs: Long = 33
    ) {
        val config = currentConfig ?: return

        if (inferenceTimeMs > targetFrameTimeMs * 1.5) {
            when (config.tier) {
                PerformanceTier.HIGH -> {
                    Timber.w("Performance degradation detected on high-end device")
                    currentConfig = MEDIUM_PERF_CONFIG.copy(tier = PerformanceTier.HIGH)
                }
                PerformanceTier.MEDIUM -> {
                    Timber.w("Downgrading to low performance mode")
                    currentConfig = LOW_PERF_CONFIG.copy(tier = PerformanceTier.MEDIUM)
                }
                PerformanceTier.LOW -> {
                    Timber.w("Already at minimum performance, increasing frame skip")
                    currentConfig = config.copy(skipFrames = config.skipFrames + 1)
                }
            }
        }
    }

    fun resetFrameCounter() {
        frameCounter = 0
    }

    fun logConfiguration() {
        val config = getOptimalConfig()
        Timber.i("""
            Performance Configuration:
            - Tier: ${config.tier}
            - Resolution: ${config.targetResolution}
            - Target FPS: ${config.targetFps}
            - Skip Frames: ${config.skipFrames}
            - GPU Delegate: ${config.useGpuDelegate}
            - Overlay: ${config.enableOverlay}
        """.trimIndent())
    }
}