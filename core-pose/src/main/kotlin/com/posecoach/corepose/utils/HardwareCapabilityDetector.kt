package com.posecoach.corepose.utils

import android.app.ActivityManager
import android.content.Context
import android.opengl.GLES20
import android.os.Build
import timber.log.Timber
import javax.microedition.khronos.egl.EGL10
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.egl.EGLContext
import javax.microedition.khronos.egl.EGLDisplay

/**
 * Hardware capability detector for optimizing MediaPipe performance.
 * Analyzes device capabilities to make intelligent configuration decisions.
 */
class HardwareCapabilityDetector {

    private var lastDetectedCapabilities: HardwareCapabilities? = null
    private var lastContext: Context? = null

    data class HardwareCapabilities(
        val deviceName: String,
        val androidVersion: Int,
        val totalRamMb: Int,
        val availableRamMb: Int,
        val cpuCores: Int,
        val maxCpuFrequencyMhz: Long,
        val gpuRenderer: String,
        val gpuVendor: String,
        val supportsOpenGLES3: Boolean,
        val supportedGLExtensions: List<String>,
        val hasNeuralProcessingUnit: Boolean,
        val thermalState: Int,
        val batteryLevel: Int,
        val isCharging: Boolean,
        val performanceClass: PerformanceClass,
        val isHighEnd: Boolean,
        val isMidRange: Boolean,
        val isLowEnd: Boolean,
        val recommendedSettings: RecommendedSettings
    ) {
        enum class PerformanceClass {
            LOW_END,
            MID_RANGE,
            HIGH_END,
            FLAGSHIP
        }

        data class RecommendedSettings(
            val targetFps: Int,
            val maxPersonCount: Int,
            val useGpuAcceleration: Boolean,
            val enableObjectPooling: Boolean,
            val maxLatencyMs: Long,
            val adaptiveQuality: Boolean
        )
    }

    /**
     * Detect comprehensive hardware capabilities.
     */
    fun detectCapabilities(context: Context): HardwareCapabilities {
        lastContext = context

        val startTime = System.currentTimeMillis()

        try {
            val deviceInfo = getDeviceInfo()
            val memoryInfo = getMemoryInfo(context)
            val cpuInfo = getCpuInfo()
            val gpuInfo = getGpuInfo()
            val systemInfo = getSystemInfo(context)

            val performanceClass = determinePerformanceClass(
                memoryInfo.totalRamMb,
                cpuInfo.cores,
                cpuInfo.maxFrequencyMhz,
                gpuInfo.supportsOpenGLES3,
                deviceInfo.androidVersion
            )

            val capabilities = HardwareCapabilities(
                deviceName = deviceInfo.deviceName,
                androidVersion = deviceInfo.androidVersion,
                totalRamMb = memoryInfo.totalRamMb,
                availableRamMb = memoryInfo.availableRamMb,
                cpuCores = cpuInfo.cores,
                maxCpuFrequencyMhz = cpuInfo.maxFrequencyMhz,
                gpuRenderer = gpuInfo.renderer,
                gpuVendor = gpuInfo.vendor,
                supportsOpenGLES3 = gpuInfo.supportsOpenGLES3,
                supportedGLExtensions = gpuInfo.extensions,
                hasNeuralProcessingUnit = systemInfo.hasNPU,
                thermalState = systemInfo.thermalState,
                batteryLevel = systemInfo.batteryLevel,
                isCharging = systemInfo.isCharging,
                performanceClass = performanceClass,
                isHighEnd = performanceClass in listOf(
                    HardwareCapabilities.PerformanceClass.HIGH_END,
                    HardwareCapabilities.PerformanceClass.FLAGSHIP
                ),
                isMidRange = performanceClass == HardwareCapabilities.PerformanceClass.MID_RANGE,
                isLowEnd = performanceClass == HardwareCapabilities.PerformanceClass.LOW_END,
                recommendedSettings = generateRecommendedSettings(performanceClass, memoryInfo, systemInfo)
            )

            lastDetectedCapabilities = capabilities

            val detectionTime = System.currentTimeMillis() - startTime
            Timber.i("Hardware capability detection completed in ${detectionTime}ms")
            logCapabilities(capabilities)

            return capabilities

        } catch (e: Exception) {
            Timber.e(e, "Error detecting hardware capabilities")

            // Return safe fallback capabilities
            val fallbackCapabilities = createFallbackCapabilities()
            lastDetectedCapabilities = fallbackCapabilities
            return fallbackCapabilities
        }
    }

    private fun getDeviceInfo(): DeviceInfo {
        return DeviceInfo(
            deviceName = "${Build.MANUFACTURER} ${Build.MODEL}",
            androidVersion = Build.VERSION.SDK_INT
        )
    }

    private fun getMemoryInfo(context: Context): MemoryInfo {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)

        val totalRamMb = (memInfo.totalMem / (1024 * 1024)).toInt()
        val availableRamMb = (memInfo.availMem / (1024 * 1024)).toInt()

        return MemoryInfo(
            totalRamMb = totalRamMb,
            availableRamMb = availableRamMb,
            isLowMemory = memInfo.lowMemory,
            memoryThreshold = (memInfo.threshold / (1024 * 1024)).toInt()
        )
    }

    private fun getCpuInfo(): CpuInfo {
        val cores = Runtime.getRuntime().availableProcessors()
        val maxFrequency = try {
            // Try to read CPU frequency from /proc/cpuinfo or /sys/devices/system/cpu/
            readMaxCpuFrequency()
        } catch (e: Exception) {
            Timber.w(e, "Could not read CPU frequency")
            0L // Default to unknown
        }

        return CpuInfo(
            cores = cores,
            maxFrequencyMhz = maxFrequency
        )
    }

    private fun getGpuInfo(): GpuInfo {
        return try {
            val egl = EGLContext.getEGL() as EGL10
            val display = egl.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY)
            egl.eglInitialize(display, null)

            val configs = arrayOfNulls<EGLConfig>(1)
            val numConfig = IntArray(1)
            egl.eglChooseConfig(display, intArrayOf(EGL10.EGL_NONE), configs, 1, numConfig)

            val context = egl.eglCreateContext(display, configs[0], EGL10.EGL_NO_CONTEXT, null)

            val renderer = GLES20.glGetString(GLES20.GL_RENDERER) ?: "Unknown"
            val vendor = GLES20.glGetString(GLES20.GL_VENDOR) ?: "Unknown"
            val version = GLES20.glGetString(GLES20.GL_VERSION) ?: "Unknown"
            val extensions = GLES20.glGetString(GLES20.GL_EXTENSIONS)?.split(" ") ?: emptyList()

            val supportsOpenGLES3 = version.contains("OpenGL ES 3.") ||
                                   extensions.any { it.contains("GL_OES_vertex_array_object") }

            egl.eglDestroyContext(display, context)
            egl.eglTerminate(display)

            GpuInfo(
                renderer = renderer,
                vendor = vendor,
                version = version,
                extensions = extensions,
                supportsOpenGLES3 = supportsOpenGLES3
            )
        } catch (e: Exception) {
            Timber.w(e, "Could not detect GPU info")
            GpuInfo(
                renderer = "Unknown",
                vendor = "Unknown",
                version = "Unknown",
                extensions = emptyList(),
                supportsOpenGLES3 = false
            )
        }
    }

    private fun getSystemInfo(context: Context): SystemInfo {
        val hasNPU = detectNeuralProcessingUnit()
        val thermalState = detectThermalState()
        val batteryInfo = detectBatteryInfo(context)

        return SystemInfo(
            hasNPU = hasNPU,
            thermalState = thermalState,
            batteryLevel = batteryInfo.level,
            isCharging = batteryInfo.isCharging
        )
    }

    private fun readMaxCpuFrequency(): Long {
        return try {
            val cores = Runtime.getRuntime().availableProcessors()
            var maxFreq = 0L

            for (i in 0 until cores) {
                val freqFile = java.io.File("/sys/devices/system/cpu/cpu$i/cpufreq/cpuinfo_max_freq")
                if (freqFile.exists()) {
                    val freq = freqFile.readText().trim().toLongOrNull()
                    if (freq != null && freq > maxFreq) {
                        maxFreq = freq / 1000 // Convert to MHz
                    }
                }
            }

            maxFreq
        } catch (e: Exception) {
            0L
        }
    }

    private fun detectNeuralProcessingUnit(): Boolean {
        return try {
            // Check for common NPU indicators
            Build.HARDWARE.lowercase().contains("npu") ||
            Build.HARDWARE.lowercase().contains("hexagon") ||
            Build.SOC_MANUFACTURER.lowercase().contains("qualcomm") && Build.VERSION.SDK_INT >= 28
        } catch (e: Exception) {
            false
        }
    }

    private fun detectThermalState(): Int {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Use PowerManager.getThermalState() if available
                0 // Normal state
            } else {
                0 // Normal state fallback
            }
        } catch (e: Exception) {
            0
        }
    }

    private fun detectBatteryInfo(context: Context): BatteryInfo {
        return try {
            val intent = context.registerReceiver(null,
                android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))

            val level = intent?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = intent?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1) ?: -1
            val status = intent?.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1) ?: -1

            val batteryPct = if (level >= 0 && scale > 0) {
                (level * 100 / scale)
            } else {
                100 // Default to full if unknown
            }

            val isCharging = status == android.os.BatteryManager.BATTERY_STATUS_CHARGING ||
                           status == android.os.BatteryManager.BATTERY_STATUS_FULL

            BatteryInfo(level = batteryPct, isCharging = isCharging)
        } catch (e: Exception) {
            Timber.w(e, "Could not detect battery info")
            BatteryInfo(level = 100, isCharging = false)
        }
    }

    private fun determinePerformanceClass(
        totalRamMb: Int,
        cpuCores: Int,
        maxCpuFrequencyMhz: Long,
        supportsOpenGLES3: Boolean,
        androidVersion: Int
    ): HardwareCapabilities.PerformanceClass {
        val score = calculatePerformanceScore(totalRamMb, cpuCores, maxCpuFrequencyMhz, supportsOpenGLES3, androidVersion)

        return when {
            score >= 80 -> HardwareCapabilities.PerformanceClass.FLAGSHIP
            score >= 60 -> HardwareCapabilities.PerformanceClass.HIGH_END
            score >= 40 -> HardwareCapabilities.PerformanceClass.MID_RANGE
            else -> HardwareCapabilities.PerformanceClass.LOW_END
        }
    }

    private fun calculatePerformanceScore(
        totalRamMb: Int,
        cpuCores: Int,
        maxCpuFrequencyMhz: Long,
        supportsOpenGLES3: Boolean,
        androidVersion: Int
    ): Int {
        var score = 0

        // RAM score (0-30 points)
        score += when {
            totalRamMb >= 8192 -> 30
            totalRamMb >= 6144 -> 25
            totalRamMb >= 4096 -> 20
            totalRamMb >= 3072 -> 15
            totalRamMb >= 2048 -> 10
            else -> 5
        }

        // CPU cores score (0-20 points)
        score += when {
            cpuCores >= 8 -> 20
            cpuCores >= 6 -> 15
            cpuCores >= 4 -> 10
            else -> 5
        }

        // CPU frequency score (0-20 points)
        score += when {
            maxCpuFrequencyMhz >= 2800 -> 20
            maxCpuFrequencyMhz >= 2400 -> 15
            maxCpuFrequencyMhz >= 2000 -> 10
            maxCpuFrequencyMhz >= 1600 -> 5
            else -> 0
        }

        // GPU score (0-15 points)
        score += if (supportsOpenGLES3) 15 else 5

        // Android version score (0-15 points)
        score += when {
            androidVersion >= 31 -> 15
            androidVersion >= 29 -> 12
            androidVersion >= 26 -> 8
            else -> 3
        }

        return score.coerceIn(0, 100)
    }

    private fun generateRecommendedSettings(
        performanceClass: HardwareCapabilities.PerformanceClass,
        memoryInfo: MemoryInfo,
        systemInfo: SystemInfo
    ): HardwareCapabilities.RecommendedSettings {
        return when (performanceClass) {
            HardwareCapabilities.PerformanceClass.FLAGSHIP -> {
                HardwareCapabilities.RecommendedSettings(
                    targetFps = if (systemInfo.batteryLevel > 30 || systemInfo.isCharging) 30 else 24,
                    maxPersonCount = 3,
                    useGpuAcceleration = true,
                    enableObjectPooling = true,
                    maxLatencyMs = 25,
                    adaptiveQuality = true
                )
            }
            HardwareCapabilities.PerformanceClass.HIGH_END -> {
                HardwareCapabilities.RecommendedSettings(
                    targetFps = 24,
                    maxPersonCount = 2,
                    useGpuAcceleration = true,
                    enableObjectPooling = true,
                    maxLatencyMs = 30,
                    adaptiveQuality = true
                )
            }
            HardwareCapabilities.PerformanceClass.MID_RANGE -> {
                HardwareCapabilities.RecommendedSettings(
                    targetFps = 20,
                    maxPersonCount = 1,
                    useGpuAcceleration = memoryInfo.availableRamMb > 1024,
                    enableObjectPooling = true,
                    maxLatencyMs = 40,
                    adaptiveQuality = true
                )
            }
            HardwareCapabilities.PerformanceClass.LOW_END -> {
                HardwareCapabilities.RecommendedSettings(
                    targetFps = 15,
                    maxPersonCount = 1,
                    useGpuAcceleration = false,
                    enableObjectPooling = memoryInfo.availableRamMb > 512,
                    maxLatencyMs = 60,
                    adaptiveQuality = true
                )
            }
        }
    }

    private fun createFallbackCapabilities(): HardwareCapabilities {
        return HardwareCapabilities(
            deviceName = "Unknown Device",
            androidVersion = Build.VERSION.SDK_INT,
            totalRamMb = 2048,
            availableRamMb = 1024,
            cpuCores = 4,
            maxCpuFrequencyMhz = 1800,
            gpuRenderer = "Unknown",
            gpuVendor = "Unknown",
            supportsOpenGLES3 = false,
            supportedGLExtensions = emptyList(),
            hasNeuralProcessingUnit = false,
            thermalState = 0,
            batteryLevel = 100,
            isCharging = false,
            performanceClass = HardwareCapabilities.PerformanceClass.MID_RANGE,
            isHighEnd = false,
            isMidRange = true,
            isLowEnd = false,
            recommendedSettings = HardwareCapabilities.RecommendedSettings(
                targetFps = 20,
                maxPersonCount = 1,
                useGpuAcceleration = false,
                enableObjectPooling = true,
                maxLatencyMs = 40,
                adaptiveQuality = true
            )
        )
    }

    private fun logCapabilities(capabilities: HardwareCapabilities) {
        Timber.i("""
            === Hardware Capabilities ===
            Device: ${capabilities.deviceName}
            Android: ${capabilities.androidVersion}
            RAM: ${capabilities.totalRamMb}MB (${capabilities.availableRamMb}MB available)
            CPU: ${capabilities.cpuCores} cores @ ${capabilities.maxCpuFrequencyMhz}MHz
            GPU: ${capabilities.gpuVendor} ${capabilities.gpuRenderer}
            OpenGL ES 3.0: ${capabilities.supportsOpenGLES3}
            NPU: ${capabilities.hasNeuralProcessingUnit}
            Battery: ${capabilities.batteryLevel}% (charging: ${capabilities.isCharging})
            Performance Class: ${capabilities.performanceClass}
            Recommended FPS: ${capabilities.recommendedSettings.targetFps}
            GPU Acceleration: ${capabilities.recommendedSettings.useGpuAcceleration}
            ============================
        """.trimIndent())
    }

    fun getLastDetectedCapabilities(): HardwareCapabilities? = lastDetectedCapabilities
    fun getLastContext(): Context? = lastContext

    // Data classes for internal use
    private data class DeviceInfo(val deviceName: String, val androidVersion: Int)
    private data class MemoryInfo(val totalRamMb: Int, val availableRamMb: Int, val isLowMemory: Boolean, val memoryThreshold: Int)
    private data class CpuInfo(val cores: Int, val maxFrequencyMhz: Long)
    private data class GpuInfo(val renderer: String, val vendor: String, val version: String, val extensions: List<String>, val supportsOpenGLES3: Boolean)
    private data class SystemInfo(val hasNPU: Boolean, val thermalState: Int, val batteryLevel: Int, val isCharging: Boolean)
    private data class BatteryInfo(val level: Int, val isCharging: Boolean)
}