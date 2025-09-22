package com.posecoach.app.performance.adaptive

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import timber.log.Timber
import kotlin.math.*

/**
 * Cloud-Edge Hybrid Optimization System
 * Intelligently distributes workload between device and cloud based on conditions
 */
class CloudEdgeOptimizer(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
) {

    data class ProcessingDecision(
        val timestamp: Long,
        val processingLocation: ProcessingLocation,
        val confidence: Float,
        val estimatedLatency: Float,
        val estimatedAccuracy: Float,
        val estimatedCost: Float, // Battery/compute cost
        val reasoning: String,
        val fallbackOption: ProcessingLocation?
    )

    enum class ProcessingLocation {
        LOCAL_CPU,      // Process on device CPU
        LOCAL_GPU,      // Process on device GPU
        LOCAL_NPU,      // Process on device Neural Processing Unit
        CLOUD_LIGHT,    // Light cloud processing (edge servers)
        CLOUD_HEAVY,    // Heavy cloud processing (full servers)
        HYBRID          // Split processing between local and cloud
    }

    data class NetworkConditions(
        val isConnected: Boolean,
        val connectionType: NetworkType,
        val bandwidth: Float, // Mbps
        val latency: Float,   // ms
        val reliability: Float, // 0.0 to 1.0
        val isMetered: Boolean,
        val signalStrength: Float // 0.0 to 1.0
    )

    enum class NetworkType {
        WIFI, CELLULAR_5G, CELLULAR_4G, CELLULAR_3G, ETHERNET, NONE
    }

    data class DeviceCapabilities(
        val cpuBenchmark: Float,
        val gpuBenchmark: Float,
        val npuAvailable: Boolean,
        val availableMemory: Long,
        val thermalState: Int,
        val batteryLevel: Float,
        val powerEfficiency: Float
    )

    data class WorkloadProfile(
        val complexity: WorkloadComplexity,
        val inputSize: Long, // bytes
        val outputSize: Long, // bytes
        val computeIntensity: Float, // 0.0 to 1.0
        val parallelizability: Float, // 0.0 to 1.0
        val privacySensitivity: PrivacyLevel,
        val latencyRequirement: LatencyRequirement
    )

    enum class WorkloadComplexity {
        MINIMAL,    // Simple operations
        LOW,        // Basic pose detection
        MEDIUM,     // Full pose analysis
        HIGH,       // Multi-person analysis
        EXTREME     // Research-level analysis
    }

    enum class PrivacyLevel {
        PUBLIC,     // No privacy concerns
        INTERNAL,   // Prefer local processing
        PRIVATE,    // Local processing only
        CONFIDENTIAL // Encrypted processing only
    }

    enum class LatencyRequirement {
        REAL_TIME,  // <33ms (30 FPS)
        INTERACTIVE, // <100ms
        RESPONSIVE,  // <500ms
        BATCH       // >500ms acceptable
    }

    data class CloudEndpoint(
        val id: String,
        val name: String,
        val endpoint: String,
        val capabilities: List<String>,
        val latency: Float,
        val cost: Float, // per request
        val reliability: Float,
        val maxConcurrentRequests: Int,
        val supportedFormats: List<String>
    )

    companion object {
        private const val DECISION_CACHE_SIZE = 100
        private const val NETWORK_MONITORING_INTERVAL = 5000L
        private const val PERFORMANCE_MONITORING_INTERVAL = 2000L
        private const val CLOUD_TIMEOUT_MS = 5000L

        // Performance thresholds
        private const val MIN_BANDWIDTH_FOR_CLOUD = 1.0f // Mbps
        private const val MAX_LATENCY_FOR_CLOUD = 200f // ms
        private const val MIN_BATTERY_FOR_CLOUD = 20f // %
        private const val MAX_THERMAL_FOR_LOCAL = 3 // thermal state
    }

    // Available cloud endpoints
    private val cloudEndpoints = mutableListOf<CloudEndpoint>(
        CloudEndpoint(
            id = "edge_1",
            name = "Edge Server US-East",
            endpoint = "https://edge-us-east.posecoach.ai",
            capabilities = listOf("pose_detection", "pose_analysis", "multi_person"),
            latency = 50f,
            cost = 0.001f,
            reliability = 0.95f,
            maxConcurrentRequests = 100,
            supportedFormats = listOf("jpeg", "png", "webp")
        ),
        CloudEndpoint(
            id = "cloud_1",
            name = "Main Cloud Processor",
            endpoint = "https://api.posecoach.ai",
            capabilities = listOf("pose_detection", "pose_analysis", "multi_person", "research"),
            latency = 150f,
            cost = 0.005f,
            reliability = 0.99f,
            maxConcurrentRequests = 1000,
            supportedFormats = listOf("jpeg", "png", "webp", "raw")
        )
    )

    // State management
    private val _networkConditions = MutableStateFlow(
        NetworkConditions(false, NetworkType.NONE, 0f, 0f, 0f, false, 0f)
    )
    val networkConditions: StateFlow<NetworkConditions> = _networkConditions.asStateFlow()

    private val _processingDecisions = MutableSharedFlow<ProcessingDecision>(
        replay = 10,
        extraBufferCapacity = 50
    )
    val processingDecisions: SharedFlow<ProcessingDecision> = _processingDecisions.asSharedFlow()

    // Performance tracking
    private val decisionHistory = mutableListOf<ProcessingDecision>()
    private val performanceHistory = mutableMapOf<ProcessingLocation, MutableList<Float>>()

    // Configuration
    private var enableCloudProcessing = true
    private var privacyMode = PrivacyLevel.INTERNAL
    private var bandwidthLimit = Float.MAX_VALUE
    private var costBudget = Float.MAX_VALUE

    init {
        startNetworkMonitoring()
        startPerformanceMonitoring()
    }

    private fun startNetworkMonitoring() {
        scope.launch {
            while (isActive) {
                try {
                    updateNetworkConditions()
                    delay(NETWORK_MONITORING_INTERVAL)
                } catch (e: Exception) {
                    Timber.e(e, "Error monitoring network conditions")
                    delay(NETWORK_MONITORING_INTERVAL * 2)
                }
            }
        }
    }

    private fun startPerformanceMonitoring() {
        scope.launch {
            while (isActive) {
                try {
                    updatePerformanceMetrics()
                    delay(PERFORMANCE_MONITORING_INTERVAL)
                } catch (e: Exception) {
                    Timber.e(e, "Error monitoring performance")
                    delay(PERFORMANCE_MONITORING_INTERVAL * 2)
                }
            }
        }
    }

    private suspend fun updateNetworkConditions() = withContext(Dispatchers.IO) {
        try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val activeNetwork = connectivityManager.activeNetwork
            val networkCapabilities = activeNetwork?.let {
                connectivityManager.getNetworkCapabilities(it)
            }

            val conditions = if (networkCapabilities != null) {
                val connectionType = when {
                    networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkType.WIFI
                    networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
                        // Simplified cellular type detection
                        NetworkType.CELLULAR_4G
                    }
                    networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkType.ETHERNET
                    else -> NetworkType.NONE
                }

                val bandwidth = estimateBandwidth(networkCapabilities)
                val latency = measureNetworkLatency()
                val reliability = estimateNetworkReliability(connectionType)
                val isMetered = !networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
                val signalStrength = estimateSignalStrength(networkCapabilities)

                NetworkConditions(
                    isConnected = true,
                    connectionType = connectionType,
                    bandwidth = bandwidth,
                    latency = latency,
                    reliability = reliability,
                    isMetered = isMetered,
                    signalStrength = signalStrength
                )
            } else {
                NetworkConditions(false, NetworkType.NONE, 0f, 0f, 0f, false, 0f)
            }

            _networkConditions.value = conditions

        } catch (e: Exception) {
            Timber.e(e, "Failed to update network conditions")
        }
    }

    private fun estimateBandwidth(capabilities: NetworkCapabilities): Float {
        // Simplified bandwidth estimation based on connection type
        return when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> 50f // Assume good WiFi
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> 20f // Assume 4G
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> 100f
            else -> 0f
        }
    }

    private suspend fun measureNetworkLatency(): Float = withContext(Dispatchers.IO) {
        // Simplified latency measurement
        // In a real implementation, this would ping a test endpoint
        try {
            val startTime = System.currentTimeMillis()
            // Simulate network check
            delay(10) // Simulated ping time
            val endTime = System.currentTimeMillis()
            (endTime - startTime).toFloat()
        } catch (e: Exception) {
            Float.MAX_VALUE
        }
    }

    private fun estimateNetworkReliability(type: NetworkType): Float {
        return when (type) {
            NetworkType.WIFI -> 0.9f
            NetworkType.CELLULAR_5G -> 0.85f
            NetworkType.CELLULAR_4G -> 0.8f
            NetworkType.CELLULAR_3G -> 0.7f
            NetworkType.ETHERNET -> 0.95f
            NetworkType.NONE -> 0f
        }
    }

    private fun estimateSignalStrength(capabilities: NetworkCapabilities): Float {
        // Simplified signal strength estimation
        return 0.8f // Placeholder
    }

    private fun updatePerformanceMetrics() {
        // Update performance history for each processing location
        // This would integrate with actual performance measurements
    }

    /**
     * Main decision engine - determines optimal processing location
     */
    suspend fun determineOptimalProcessing(
        workload: WorkloadProfile,
        deviceCapabilities: DeviceCapabilities,
        userPreferences: Map<String, Any> = emptyMap()
    ): ProcessingDecision {

        val timestamp = System.currentTimeMillis()
        val networkConditions = _networkConditions.value

        // Check privacy constraints first
        if (workload.privacySensitivity == PrivacyLevel.PRIVATE ||
            workload.privacySensitivity == PrivacyLevel.CONFIDENTIAL) {
            return createLocalProcessingDecision(
                timestamp, workload, deviceCapabilities, "Privacy constraints require local processing"
            )
        }

        // Check if cloud processing is enabled and available
        if (!enableCloudProcessing || !networkConditions.isConnected) {
            return createLocalProcessingDecision(
                timestamp, workload, deviceCapabilities, "Cloud processing disabled or no network"
            )
        }

        // Evaluate all processing options
        val options = evaluateProcessingOptions(workload, deviceCapabilities, networkConditions)

        // Select the best option
        val bestOption = selectBestOption(options, workload, userPreferences)

        // Create and cache decision
        val decision = ProcessingDecision(
            timestamp = timestamp,
            processingLocation = bestOption.location,
            confidence = bestOption.confidence,
            estimatedLatency = bestOption.estimatedLatency,
            estimatedAccuracy = bestOption.estimatedAccuracy,
            estimatedCost = bestOption.estimatedCost,
            reasoning = bestOption.reasoning,
            fallbackOption = bestOption.fallbackOption
        )

        recordDecision(decision)
        _processingDecisions.tryEmit(decision)

        return decision
    }

    private data class ProcessingOption(
        val location: ProcessingLocation,
        val estimatedLatency: Float,
        val estimatedAccuracy: Float,
        val estimatedCost: Float,
        val confidence: Float,
        val reasoning: String,
        val fallbackOption: ProcessingLocation? = null
    )

    private fun evaluateProcessingOptions(
        workload: WorkloadProfile,
        device: DeviceCapabilities,
        network: NetworkConditions
    ): List<ProcessingOption> {
        val options = mutableListOf<ProcessingOption>()

        // Evaluate local CPU processing
        options.add(evaluateLocalCpuProcessing(workload, device))

        // Evaluate local GPU processing
        if (device.gpuBenchmark > 0) {
            options.add(evaluateLocalGpuProcessing(workload, device))
        }

        // Evaluate local NPU processing
        if (device.npuAvailable) {
            options.add(evaluateLocalNpuProcessing(workload, device))
        }

        // Evaluate cloud processing options
        if (network.isConnected && isCloudViable(workload, network)) {
            options.addAll(evaluateCloudProcessing(workload, network))
        }

        // Evaluate hybrid processing
        if (network.isConnected && isHybridViable(workload, device, network)) {
            options.add(evaluateHybridProcessing(workload, device, network))
        }

        return options
    }

    private fun evaluateLocalCpuProcessing(
        workload: WorkloadProfile,
        device: DeviceCapabilities
    ): ProcessingOption {
        val baseLatency = when (workload.complexity) {
            WorkloadComplexity.MINIMAL -> 10f
            WorkloadComplexity.LOW -> 30f
            WorkloadComplexity.MEDIUM -> 80f
            WorkloadComplexity.HIGH -> 200f
            WorkloadComplexity.EXTREME -> 500f
        }

        val cpuFactor = device.cpuBenchmark / 1000f // Normalize
        val memoryFactor = (device.availableMemory / (1024L * 1024L * 1024L)).toFloat().coerceAtMost(1f) // GB, max 1
        val thermalFactor = if (device.thermalState >= MAX_THERMAL_FOR_LOCAL) 2f else 1f

        val estimatedLatency = baseLatency / (cpuFactor * memoryFactor) * thermalFactor
        val estimatedAccuracy = 0.9f - (device.thermalState * 0.05f).coerceAtMost(0.2f)
        val estimatedCost = calculateLocalProcessingCost(workload, device.batteryLevel)

        val confidence = calculateLocalProcessingConfidence(device, workload)

        return ProcessingOption(
            location = ProcessingLocation.LOCAL_CPU,
            estimatedLatency = estimatedLatency,
            estimatedAccuracy = estimatedAccuracy,
            estimatedCost = estimatedCost,
            confidence = confidence,
            reasoning = "Local CPU processing analysis",
            fallbackOption = null
        )
    }

    private fun evaluateLocalGpuProcessing(
        workload: WorkloadProfile,
        device: DeviceCapabilities
    ): ProcessingOption {
        val baseLatency = when (workload.complexity) {
            WorkloadComplexity.MINIMAL -> 5f
            WorkloadComplexity.LOW -> 15f
            WorkloadComplexity.MEDIUM -> 40f
            WorkloadComplexity.HIGH -> 100f
            WorkloadComplexity.EXTREME -> 250f
        }

        val gpuFactor = device.gpuBenchmark / 2000f // Normalize
        val thermalFactor = if (device.thermalState >= MAX_THERMAL_FOR_LOCAL) 1.5f else 1f

        val estimatedLatency = baseLatency / gpuFactor * thermalFactor
        val estimatedAccuracy = 0.92f - (device.thermalState * 0.03f).coerceAtMost(0.15f)
        val estimatedCost = calculateLocalProcessingCost(workload, device.batteryLevel) * 1.3f // GPU uses more power

        val confidence = calculateLocalProcessingConfidence(device, workload) + 0.1f

        return ProcessingOption(
            location = ProcessingLocation.LOCAL_GPU,
            estimatedLatency = estimatedLatency,
            estimatedAccuracy = estimatedAccuracy,
            estimatedCost = estimatedCost,
            confidence = confidence.coerceAtMost(1f),
            reasoning = "Local GPU processing analysis",
            fallbackOption = ProcessingLocation.LOCAL_CPU
        )
    }

    private fun evaluateLocalNpuProcessing(
        workload: WorkloadProfile,
        device: DeviceCapabilities
    ): ProcessingOption {
        // NPU is optimized for AI workloads
        val baseLatency = when (workload.complexity) {
            WorkloadComplexity.MINIMAL -> 3f
            WorkloadComplexity.LOW -> 8f
            WorkloadComplexity.MEDIUM -> 20f
            WorkloadComplexity.HIGH -> 50f
            WorkloadComplexity.EXTREME -> 120f
        }

        val estimatedLatency = baseLatency
        val estimatedAccuracy = 0.95f
        val estimatedCost = calculateLocalProcessingCost(workload, device.batteryLevel) * 0.7f // NPU is power efficient

        val confidence = 0.9f

        return ProcessingOption(
            location = ProcessingLocation.LOCAL_NPU,
            estimatedLatency = estimatedLatency,
            estimatedAccuracy = estimatedAccuracy,
            estimatedCost = estimatedCost,
            confidence = confidence,
            reasoning = "Local NPU processing analysis",
            fallbackOption = ProcessingLocation.LOCAL_GPU
        )
    }

    private fun evaluateCloudProcessing(
        workload: WorkloadProfile,
        network: NetworkConditions
    ): List<ProcessingOption> {
        val options = mutableListOf<ProcessingOption>()

        cloudEndpoints.forEach { endpoint ->
            if (endpoint.capabilities.any { capability ->
                isCapabilityCompatible(capability, workload.complexity)
            }) {
                val option = evaluateCloudEndpoint(endpoint, workload, network)
                options.add(option)
            }
        }

        return options
    }

    private fun evaluateCloudEndpoint(
        endpoint: CloudEndpoint,
        workload: WorkloadProfile,
        network: NetworkConditions
    ): ProcessingOption {
        val networkLatency = network.latency
        val uploadTime = (workload.inputSize * 8f) / (network.bandwidth * 1024 * 1024) * 1000f // Convert to ms
        val downloadTime = (workload.outputSize * 8f) / (network.bandwidth * 1024 * 1024) * 1000f
        val processingTime = endpoint.latency

        val totalLatency = networkLatency + uploadTime + processingTime + downloadTime
        val estimatedAccuracy = 0.95f // Cloud processing typically has high accuracy
        val estimatedCost = endpoint.cost * calculateCloudCostMultiplier(workload)

        val confidence = network.reliability * endpoint.reliability *
                        calculateNetworkConfidence(network, workload)

        val location = if (endpoint.id.startsWith("edge")) {
            ProcessingLocation.CLOUD_LIGHT
        } else {
            ProcessingLocation.CLOUD_HEAVY
        }

        return ProcessingOption(
            location = location,
            estimatedLatency = totalLatency,
            estimatedAccuracy = estimatedAccuracy,
            estimatedCost = estimatedCost,
            confidence = confidence,
            reasoning = "Cloud processing via ${endpoint.name}",
            fallbackOption = ProcessingLocation.LOCAL_GPU
        )
    }

    private fun evaluateHybridProcessing(
        workload: WorkloadProfile,
        device: DeviceCapabilities,
        network: NetworkConditions
    ): ProcessingOption {
        // Hybrid processing splits workload between local and cloud
        val localOption = evaluateLocalGpuProcessing(workload, device)
        val cloudOptions = evaluateCloudProcessing(workload, network)
        val bestCloudOption = cloudOptions.minByOrNull { it.estimatedLatency }

        if (bestCloudOption == null) {
            return localOption.copy(location = ProcessingLocation.HYBRID)
        }

        // Estimate hybrid performance (simplified)
        val hybridLatency = (localOption.estimatedLatency + bestCloudOption.estimatedLatency) / 2f
        val hybridAccuracy = max(localOption.estimatedAccuracy, bestCloudOption.estimatedAccuracy)
        val hybridCost = (localOption.estimatedCost + bestCloudOption.estimatedCost) / 2f
        val hybridConfidence = (localOption.confidence + bestCloudOption.confidence) / 2f

        return ProcessingOption(
            location = ProcessingLocation.HYBRID,
            estimatedLatency = hybridLatency,
            estimatedAccuracy = hybridAccuracy,
            estimatedCost = hybridCost,
            confidence = hybridConfidence,
            reasoning = "Hybrid local-cloud processing",
            fallbackOption = ProcessingLocation.LOCAL_GPU
        )
    }

    private fun selectBestOption(
        options: List<ProcessingOption>,
        workload: WorkloadProfile,
        userPreferences: Map<String, Any>
    ): ProcessingOption {
        if (options.isEmpty()) {
            // Fallback to local CPU
            return ProcessingOption(
                ProcessingLocation.LOCAL_CPU,
                100f, 0.8f, 0.5f, 0.5f,
                "Fallback to local CPU"
            )
        }

        // Calculate scores for each option based on requirements
        val scoredOptions = options.map { option ->
            val latencyScore = calculateLatencyScore(option.estimatedLatency, workload.latencyRequirement)
            val accuracyScore = option.estimatedAccuracy
            val costScore = 1f - (option.estimatedCost / 10f).coerceAtMost(1f)
            val confidenceScore = option.confidence

            // Weight factors based on workload and preferences
            val latencyWeight = when (workload.latencyRequirement) {
                LatencyRequirement.REAL_TIME -> 0.5f
                LatencyRequirement.INTERACTIVE -> 0.3f
                LatencyRequirement.RESPONSIVE -> 0.2f
                LatencyRequirement.BATCH -> 0.1f
            }

            val accuracyWeight = userPreferences["accuracy_priority"] as? Float ?: 0.3f
            val costWeight = userPreferences["cost_priority"] as? Float ?: 0.2f
            val confidenceWeight = 0.2f

            val totalScore = (latencyScore * latencyWeight +
                            accuracyScore * accuracyWeight +
                            costScore * costWeight +
                            confidenceScore * confidenceWeight)

            option to totalScore
        }

        return scoredOptions.maxByOrNull { it.second }?.first ?: options.first()
    }

    private fun calculateLatencyScore(latency: Float, requirement: LatencyRequirement): Float {
        val threshold = when (requirement) {
            LatencyRequirement.REAL_TIME -> 33f
            LatencyRequirement.INTERACTIVE -> 100f
            LatencyRequirement.RESPONSIVE -> 500f
            LatencyRequirement.BATCH -> 2000f
        }

        return if (latency <= threshold) {
            1f - (latency / threshold) * 0.5f
        } else {
            0.5f / (1f + (latency - threshold) / threshold)
        }.coerceIn(0f, 1f)
    }

    private fun createLocalProcessingDecision(
        timestamp: Long,
        workload: WorkloadProfile,
        device: DeviceCapabilities,
        reason: String
    ): ProcessingDecision {
        val location = when {
            device.npuAvailable -> ProcessingLocation.LOCAL_NPU
            device.gpuBenchmark > 0 -> ProcessingLocation.LOCAL_GPU
            else -> ProcessingLocation.LOCAL_CPU
        }

        val option = when (location) {
            ProcessingLocation.LOCAL_NPU -> evaluateLocalNpuProcessing(workload, device)
            ProcessingLocation.LOCAL_GPU -> evaluateLocalGpuProcessing(workload, device)
            else -> evaluateLocalCpuProcessing(workload, device)
        }

        return ProcessingDecision(
            timestamp = timestamp,
            processingLocation = location,
            confidence = option.confidence,
            estimatedLatency = option.estimatedLatency,
            estimatedAccuracy = option.estimatedAccuracy,
            estimatedCost = option.estimatedCost,
            reasoning = reason,
            fallbackOption = null
        )
    }

    // Helper methods
    private fun isCloudViable(workload: WorkloadProfile, network: NetworkConditions): Boolean {
        return network.bandwidth >= MIN_BANDWIDTH_FOR_CLOUD &&
               network.latency <= MAX_LATENCY_FOR_CLOUD &&
               (!network.isMetered || workload.inputSize < 1024 * 1024) // 1MB limit for metered
    }

    private fun isHybridViable(
        workload: WorkloadProfile,
        device: DeviceCapabilities,
        network: NetworkConditions
    ): Boolean {
        return workload.parallelizability > 0.5f &&
               isCloudViable(workload, network) &&
               device.availableMemory > 512 * 1024 * 1024 // 512MB
    }

    private fun isCapabilityCompatible(capability: String, complexity: WorkloadComplexity): Boolean {
        return when (complexity) {
            WorkloadComplexity.MINIMAL, WorkloadComplexity.LOW -> capability == "pose_detection"
            WorkloadComplexity.MEDIUM -> capability in listOf("pose_detection", "pose_analysis")
            WorkloadComplexity.HIGH -> capability in listOf("pose_analysis", "multi_person")
            WorkloadComplexity.EXTREME -> capability == "research"
        }
    }

    private fun calculateLocalProcessingCost(workload: WorkloadProfile, batteryLevel: Float): Float {
        val baseCost = when (workload.complexity) {
            WorkloadComplexity.MINIMAL -> 0.1f
            WorkloadComplexity.LOW -> 0.2f
            WorkloadComplexity.MEDIUM -> 0.4f
            WorkloadComplexity.HIGH -> 0.7f
            WorkloadComplexity.EXTREME -> 1.0f
        }

        val batteryFactor = if (batteryLevel < MIN_BATTERY_FOR_CLOUD) 2f else 1f
        return baseCost * batteryFactor
    }

    private fun calculateCloudCostMultiplier(workload: WorkloadProfile): Float {
        return when (workload.complexity) {
            WorkloadComplexity.MINIMAL -> 1f
            WorkloadComplexity.LOW -> 1.5f
            WorkloadComplexity.MEDIUM -> 2f
            WorkloadComplexity.HIGH -> 3f
            WorkloadComplexity.EXTREME -> 5f
        }
    }

    private fun calculateLocalProcessingConfidence(
        device: DeviceCapabilities,
        workload: WorkloadProfile
    ): Float {
        val thermalPenalty = device.thermalState * 0.1f
        val memoryScore = (device.availableMemory / (1024L * 1024L * 1024L)).toFloat().coerceAtMost(1f)
        val batteryScore = (device.batteryLevel / 100f).coerceAtMost(1f)

        return (0.8f + memoryScore * 0.1f + batteryScore * 0.1f - thermalPenalty).coerceIn(0f, 1f)
    }

    private fun calculateNetworkConfidence(
        network: NetworkConditions,
        workload: WorkloadProfile
    ): Float {
        val bandwidthScore = (network.bandwidth / 10f).coerceAtMost(1f) // Normalize to 10 Mbps
        val latencyScore = max(0f, 1f - network.latency / 200f) // 200ms baseline
        val sizeScore = if (workload.inputSize > 5 * 1024 * 1024) 0.7f else 1f // 5MB penalty

        return (bandwidthScore * 0.4f + latencyScore * 0.4f + sizeScore * 0.2f).coerceIn(0f, 1f)
    }

    private fun recordDecision(decision: ProcessingDecision) {
        decisionHistory.add(decision)
        if (decisionHistory.size > DECISION_CACHE_SIZE) {
            decisionHistory.removeAt(0)
        }
    }

    // Public API methods
    fun setCloudProcessingEnabled(enabled: Boolean) {
        enableCloudProcessing = enabled
        Timber.i("Cloud processing: ${if (enabled) "enabled" else "disabled"}")
    }

    fun setPrivacyMode(mode: PrivacyLevel) {
        privacyMode = mode
        Timber.i("Privacy mode set to: $mode")
    }

    fun setBandwidthLimit(limitMbps: Float) {
        bandwidthLimit = limitMbps
        Timber.i("Bandwidth limit set to: ${limitMbps}Mbps")
    }

    fun setCostBudget(budget: Float) {
        costBudget = budget
        Timber.i("Cost budget set to: $budget")
    }

    fun addCloudEndpoint(endpoint: CloudEndpoint) {
        cloudEndpoints.add(endpoint)
        Timber.i("Added cloud endpoint: ${endpoint.name}")
    }

    fun removeCloudEndpoint(endpointId: String) {
        cloudEndpoints.removeAll { it.id == endpointId }
        Timber.i("Removed cloud endpoint: $endpointId")
    }

    fun getDecisionHistory(): List<ProcessingDecision> {
        return decisionHistory.toList()
    }

    fun getPerformanceStats(): Map<String, Any> {
        val decisions = decisionHistory
        if (decisions.isEmpty()) {
            return mapOf("total_decisions" to 0)
        }

        val locationCounts = decisions.groupBy { it.processingLocation }
            .mapValues { it.value.size }

        val avgLatency = decisions.map { it.estimatedLatency }.average()
        val avgAccuracy = decisions.map { it.estimatedAccuracy }.average()
        val avgCost = decisions.map { it.estimatedCost }.average()

        return mapOf(
            "total_decisions" to decisions.size,
            "location_distribution" to locationCounts,
            "average_latency" to avgLatency,
            "average_accuracy" to avgAccuracy,
            "average_cost" to avgCost,
            "cloud_enabled" to enableCloudProcessing,
            "privacy_mode" to privacyMode.name,
            "network_connected" to _networkConditions.value.isConnected
        )
    }

    fun exportDecisionLog(): String {
        return buildString {
            appendLine("=== Cloud-Edge Processing Decision Log ===")
            appendLine("Total Decisions: ${decisionHistory.size}")
            appendLine()
            appendLine("Timestamp,Location,Latency,Accuracy,Cost,Confidence,Reasoning")

            decisionHistory.forEach { decision ->
                appendLine("${decision.timestamp},${decision.processingLocation},${decision.estimatedLatency},${decision.estimatedAccuracy},${decision.estimatedCost},${decision.confidence},\"${decision.reasoning}\"")
            }
        }
    }

    fun shutdown() {
        scope.cancel()
        Timber.i("Cloud-edge optimizer shutdown")
    }
}