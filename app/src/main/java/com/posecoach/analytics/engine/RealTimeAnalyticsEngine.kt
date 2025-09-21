package com.posecoach.analytics.engine

import com.posecoach.analytics.interfaces.*
import com.posecoach.analytics.models.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.BufferOverflow
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Real-time analytics engine that processes and streams analytics data
 * with sub-100ms latency requirements
 */
@Singleton
class RealTimeAnalyticsEngine @Inject constructor(
    private val repository: AnalyticsRepository,
    private val privacyEngine: PrivacyEngine,
    private val businessIntelligence: BusinessIntelligenceEngine,
    private val pipelineManager: DataPipelineManager
) : AnalyticsEngine {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val eventChannel = Channel<AnalyticsEvent>(
        capacity = 10000,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    private val realtimeDataFlow = MutableSharedFlow<RealtimeAnalyticsData>(
        replay = 1,
        extraBufferCapacity = 100,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    private val sessionMetrics = ConcurrentHashMap<String, SessionMetrics>()
    private val alertManager = AlertManager()
    private val metricsProcessor = MetricsProcessor()
    private val insightGenerator = InsightGenerator()
    private val eventCounter = AtomicLong(0)

    private data class SessionMetrics(
        val sessionId: String,
        val startTime: Long,
        var lastActivity: Long,
        val events: MutableList<AnalyticsEvent> = mutableListOf(),
        val metrics: MutableMap<String, Float> = mutableMapOf()
    )

    init {
        startRealtimeProcessing()
    }

    override suspend fun trackEvent(event: AnalyticsEvent) {
        try {
            // Apply privacy filtering
            val filteredEvent = if (event.privacyLevel != PrivacyLevel.ANONYMIZED) {
                privacyEngine.anonymizeData(event) as AnalyticsEvent
            } else event

            // Send to channel for real-time processing
            eventChannel.trySend(filteredEvent)

            // Update session metrics
            updateSessionMetrics(filteredEvent)

            // Store for persistence (async)
            scope.launch {
                repository.storeEvent(filteredEvent)
            }

            eventCounter.incrementAndGet()
        } catch (e: Exception) {
            handleError("Failed to track event", e)
        }
    }

    override suspend fun trackUserPerformance(metrics: UserPerformanceMetrics) {
        val event = AnalyticsEvent(
            userId = metrics.userId,
            sessionId = metrics.sessionId,
            timestamp = metrics.timestamp,
            eventType = EventType.PERFORMANCE_UPDATE,
            category = EventCategory.WORKOUT,
            properties = mapOf(
                "poseAccuracy" to metrics.poseAccuracy,
                "energyExpenditure" to metrics.energyExpenditure,
                "duration" to metrics.duration,
                "intensityLevel" to metrics.intensityLevel.name,
                "improvementRate" to metrics.improvementRate
            ),
            privacyLevel = PrivacyLevel.PSEUDONYMIZED
        )

        trackEvent(event)

        // Generate real-time insights
        scope.launch {
            val insights = generatePerformanceInsights(metrics)
            insights.forEach { insight ->
                emitRealtimeUpdate(insight)
            }
        }
    }

    override suspend fun trackCoachingEffectiveness(metrics: CoachingEffectivenessMetrics) {
        val event = AnalyticsEvent(
            userId = metrics.userId,
            sessionId = metrics.coachingSessionId,
            timestamp = metrics.timestamp,
            eventType = EventType.COACHING_FEEDBACK,
            category = EventCategory.COACHING,
            properties = mapOf(
                "suggestionAccuracy" to metrics.suggestionAccuracy,
                "userCompliance" to metrics.userCompliance,
                "feedbackEffectiveness" to metrics.feedbackEffectiveness,
                "personalizationScore" to metrics.personalizationScore,
                "interventionSuccess" to metrics.interventionSuccess,
                "modalityUsed" to metrics.modalityUsed.name,
                "improvementImpact" to metrics.improvementImpact
            ),
            privacyLevel = PrivacyLevel.AGGREGATED
        )

        trackEvent(event)

        // Update coaching model effectiveness
        scope.launch {
            updateCoachingEffectivenessModel(metrics)
        }
    }

    override suspend fun trackSystemPerformance(metrics: SystemPerformanceMetrics) {
        val event = AnalyticsEvent(
            userId = null, // System metrics are not user-specific
            sessionId = metrics.sessionId,
            timestamp = metrics.timestamp,
            eventType = EventType.SYSTEM_METRIC,
            category = EventCategory.SYSTEM,
            properties = mapOf(
                "appLatency" to metrics.appLatency,
                "aiProcessingTime" to metrics.aiProcessingTime,
                "memoryUsage" to metrics.memoryUsage,
                "cpuUsage" to metrics.cpuUsage,
                "networkLatency" to metrics.networkLatency,
                "errorCount" to metrics.errorCount,
                "frameRate" to metrics.frameRate,
                "deviceModel" to metrics.deviceModel,
                "osVersion" to metrics.osVersion
            ),
            privacyLevel = PrivacyLevel.ANONYMIZED
        )

        trackEvent(event)

        // Check for performance alerts
        scope.launch {
            val alerts = alertManager.checkPerformanceAlerts(metrics)
            alerts.forEach { alert ->
                emitAlert(alert)
            }
        }
    }

    override fun getRealtimeStream(): Flow<RealtimeAnalyticsData> {
        return realtimeDataFlow.asSharedFlow()
    }

    override suspend fun generateInsights(userId: String): List<AnalyticsInsight> {
        return withContext(Dispatchers.Default) {
            insightGenerator.generateUserInsights(userId, sessionMetrics)
        }
    }

    private fun startRealtimeProcessing() {
        scope.launch {
            eventChannel.consumeAsFlow()
                .buffer(1000)
                .collect { event ->
                    processEventRealtime(event)
                }
        }

        // Periodic aggregation and streaming
        scope.launch {
            while (true) {
                delay(100) // 100ms intervals for real-time updates

                val currentTime = System.currentTimeMillis()
                val streamData = RealtimeAnalyticsData(
                    streamId = "rt-${currentTime}",
                    timestamp = currentTime,
                    metrics = aggregateCurrentMetrics(),
                    events = getRecentEvents(1000), // Last 1 second of events
                    alerts = alertManager.getActiveAlerts(),
                    latency = measureProcessingLatency()
                )

                realtimeDataFlow.tryEmit(streamData)
            }
        }
    }

    private suspend fun processEventRealtime(event: AnalyticsEvent) {
        try {
            // Update real-time metrics
            metricsProcessor.processEvent(event)

            // Check for anomalies
            val anomalies = detectAnomalies(event)
            anomalies.forEach { anomaly ->
                alertManager.addAlert(
                    AnalyticsAlert(
                        alertId = "anomaly-${System.currentTimeMillis()}",
                        type = AlertType.ANOMALY_DETECTED,
                        severity = AlertSeverity.WARNING,
                        message = "Anomaly detected: ${anomaly.description}",
                        actionRequired = true,
                        relatedMetrics = anomaly.affectedMetrics
                    )
                )
            }

            // Update session tracking
            updateSessionTracking(event)

        } catch (e: Exception) {
            handleError("Error processing real-time event", e)
        }
    }

    private fun updateSessionMetrics(event: AnalyticsEvent) {
        val sessionId = event.sessionId
        val currentTime = System.currentTimeMillis()

        sessionMetrics.compute(sessionId) { _, existing ->
            val session = existing ?: SessionMetrics(
                sessionId = sessionId,
                startTime = currentTime,
                lastActivity = currentTime
            )

            session.apply {
                lastActivity = currentTime
                events.add(event)

                // Update derived metrics
                event.properties.forEach { (key, value) ->
                    if (value is Number) {
                        metrics[key] = value.toFloat()
                    }
                }
            }
        }
    }

    private suspend fun generatePerformanceInsights(metrics: UserPerformanceMetrics): List<AnalyticsInsight> {
        return listOf(
            // Accuracy trend insight
            if (metrics.poseAccuracy > 0.9f) {
                AnalyticsInsight(
                    insightId = "perf-accuracy-${metrics.sessionId}",
                    userId = metrics.userId,
                    type = InsightType.ACHIEVEMENT,
                    title = "Excellent Pose Accuracy",
                    description = "You achieved ${(metrics.poseAccuracy * 100).toInt()}% pose accuracy!",
                    recommendation = "Maintain this level by focusing on controlled movements",
                    confidence = 0.95f,
                    impact = ImpactLevel.HIGH,
                    validUntil = System.currentTimeMillis() + 86400000, // 24 hours
                    actionable = true,
                    relatedMetrics = listOf("poseAccuracy", "improvementRate")
                )
            } else if (metrics.poseAccuracy < 0.7f) {
                AnalyticsInsight(
                    insightId = "perf-improvement-${metrics.sessionId}",
                    userId = metrics.userId,
                    type = InsightType.IMPROVEMENT_OPPORTUNITY,
                    title = "Pose Accuracy Needs Attention",
                    description = "Your pose accuracy is ${(metrics.poseAccuracy * 100).toInt()}%. Let's improve it!",
                    recommendation = "Focus on slower movements and proper form alignment",
                    confidence = 0.88f,
                    impact = ImpactLevel.HIGH,
                    validUntil = System.currentTimeMillis() + 86400000,
                    actionable = true,
                    relatedMetrics = listOf("poseAccuracy", "movementPatterns")
                )
            } else null,

            // Energy expenditure insight
            if (metrics.energyExpenditure > 500f) {
                AnalyticsInsight(
                    insightId = "energy-high-${metrics.sessionId}",
                    userId = metrics.userId,
                    type = InsightType.PERFORMANCE_TREND,
                    title = "High Energy Workout",
                    description = "You burned ${metrics.energyExpenditure.toInt()} calories this session!",
                    recommendation = "Consider hydration and recovery time",
                    confidence = 0.92f,
                    impact = ImpactLevel.MEDIUM,
                    validUntil = System.currentTimeMillis() + 43200000, // 12 hours
                    actionable = true,
                    relatedMetrics = listOf("energyExpenditure", "intensityLevel")
                )
            } else null
        ).filterNotNull()
    }

    private suspend fun updateCoachingEffectivenessModel(metrics: CoachingEffectivenessMetrics) {
        // Update ML model with coaching effectiveness data
        val updateData = mapOf(
            "modality" to metrics.modalityUsed.name,
            "accuracy" to metrics.suggestionAccuracy,
            "compliance" to metrics.userCompliance,
            "effectiveness" to metrics.feedbackEffectiveness,
            "success" to metrics.interventionSuccess
        )

        // This would integrate with the ML pipeline for continuous learning
        pipelineManager.ingestData(updateData)
    }

    private fun aggregateCurrentMetrics(): Map<String, Float> {
        val currentTime = System.currentTimeMillis()
        val activeThreshold = currentTime - 5000 // 5 seconds

        val activeSessions = sessionMetrics.values.filter {
            it.lastActivity > activeThreshold
        }

        return mapOf(
            "activeSessions" to activeSessions.size.toFloat(),
            "totalEvents" to eventCounter.get().toFloat(),
            "averageSessionDuration" to activeSessions.map {
                currentTime - it.startTime
            }.average().toFloat(),
            "eventRate" to calculateEventRate(),
            "memoryUsage" to getCurrentMemoryUsage(),
            "processingLatency" to measureProcessingLatency().toFloat()
        )
    }

    private fun getRecentEvents(windowMs: Long): List<AnalyticsEvent> {
        val cutoffTime = System.currentTimeMillis() - windowMs
        return sessionMetrics.values.flatMap { session ->
            session.events.filter { it.timestamp * 1000 > cutoffTime }
        }.sortedByDescending { it.timestamp }
    }

    private fun calculateEventRate(): Float {
        // Calculate events per second over the last minute
        val currentTime = System.currentTimeMillis()
        val oneMinuteAgo = currentTime - 60000

        val recentEvents = sessionMetrics.values.flatMap { session ->
            session.events.filter { it.timestamp * 1000 > oneMinuteAgo }
        }

        return recentEvents.size / 60.0f
    }

    private fun getCurrentMemoryUsage(): Float {
        val runtime = Runtime.getRuntime()
        val totalMemory = runtime.totalMemory()
        val freeMemory = runtime.freeMemory()
        return ((totalMemory - freeMemory) / (1024 * 1024)).toFloat() // MB
    }

    private fun measureProcessingLatency(): Long {
        // This would measure actual processing latency
        // For now, return a simulated value
        return 50L // 50ms average
    }

    private suspend fun detectAnomalies(event: AnalyticsEvent): List<AnomalyReport> {
        // Implement anomaly detection logic
        val anomalies = mutableListOf<AnomalyReport>()

        event.properties.forEach { (key, value) ->
            if (value is Number) {
                val numValue = value.toFloat()

                // Simple threshold-based anomaly detection
                when (key) {
                    "appLatency" -> {
                        if (numValue > 5000) { // > 5 seconds
                            anomalies.add(
                                AnomalyReport(
                                    anomalyId = "latency-spike-${System.currentTimeMillis()}",
                                    type = AnomalyType.SPIKE,
                                    severity = 0.8f,
                                    description = "App latency spike detected: ${numValue}ms",
                                    affectedMetrics = listOf("appLatency"),
                                    timestamp = System.currentTimeMillis(),
                                    possibleCauses = listOf("Network congestion", "Resource contention", "Memory pressure")
                                )
                            )
                        }
                    }
                    "errorCount" -> {
                        if (numValue > 10) {
                            anomalies.add(
                                AnomalyReport(
                                    anomalyId = "error-spike-${System.currentTimeMillis()}",
                                    type = AnomalyType.SPIKE,
                                    severity = 0.9f,
                                    description = "High error count detected: ${numValue}",
                                    affectedMetrics = listOf("errorCount"),
                                    timestamp = System.currentTimeMillis(),
                                    possibleCauses = listOf("System instability", "API issues", "Device compatibility")
                                )
                            )
                        }
                    }
                }
            }
        }

        return anomalies
    }

    private fun updateSessionTracking(event: AnalyticsEvent) {
        // Clean up old sessions (older than 1 hour)
        val cutoffTime = System.currentTimeMillis() - 3600000
        sessionMetrics.entries.removeAll { (_, session) ->
            session.lastActivity < cutoffTime
        }
    }

    private suspend fun emitRealtimeUpdate(insight: AnalyticsInsight) {
        val updateData = RealtimeAnalyticsData(
            streamId = "insight-${insight.insightId}",
            metrics = mapOf("insightConfidence" to insight.confidence),
            events = emptyList(),
            alerts = if (insight.impact == ImpactLevel.CRITICAL) {
                listOf(
                    AnalyticsAlert(
                        alertId = "insight-alert-${insight.insightId}",
                        type = AlertType.THRESHOLD_EXCEEDED,
                        severity = AlertSeverity.WARNING,
                        message = insight.title,
                        actionRequired = insight.actionable,
                        relatedMetrics = insight.relatedMetrics
                    )
                )
            } else emptyList(),
            latency = 50L
        )

        realtimeDataFlow.tryEmit(updateData)
    }

    private suspend fun emitAlert(alert: AnalyticsAlert) {
        val alertData = RealtimeAnalyticsData(
            streamId = "alert-${alert.alertId}",
            metrics = mapOf("alertSeverity" to alert.severity.ordinal.toFloat()),
            events = emptyList(),
            alerts = listOf(alert),
            latency = 30L
        )

        realtimeDataFlow.tryEmit(alertData)
    }

    private fun handleError(message: String, exception: Exception) {
        // Log error and emit alert
        scope.launch {
            val alert = AnalyticsAlert(
                alertId = "error-${System.currentTimeMillis()}",
                type = AlertType.ERROR_RATE_HIGH,
                severity = AlertSeverity.ERROR,
                message = "$message: ${exception.message}",
                actionRequired = true,
                relatedMetrics = listOf("errorRate", "systemHealth")
            )

            alertManager.addAlert(alert)
            emitAlert(alert)
        }
    }

    // Helper classes
    private class AlertManager {
        private val activeAlerts = mutableMapOf<String, AnalyticsAlert>()

        fun addAlert(alert: AnalyticsAlert) {
            activeAlerts[alert.alertId] = alert
        }

        fun getActiveAlerts(): List<AnalyticsAlert> {
            return activeAlerts.values.toList()
        }

        suspend fun checkPerformanceAlerts(metrics: SystemPerformanceMetrics): List<AnalyticsAlert> {
            val alerts = mutableListOf<AnalyticsAlert>()

            if (metrics.appLatency > 2000) {
                alerts.add(
                    AnalyticsAlert(
                        alertId = "perf-latency-${metrics.sessionId}",
                        type = AlertType.PERFORMANCE_DEGRADATION,
                        severity = AlertSeverity.WARNING,
                        message = "High app latency detected: ${metrics.appLatency}ms",
                        actionRequired = true,
                        relatedMetrics = listOf("appLatency")
                    )
                )
            }

            if (metrics.cpuUsage > 80f) {
                alerts.add(
                    AnalyticsAlert(
                        alertId = "perf-cpu-${metrics.sessionId}",
                        type = AlertType.SYSTEM_OVERLOAD,
                        severity = AlertSeverity.ERROR,
                        message = "High CPU usage: ${metrics.cpuUsage}%",
                        actionRequired = true,
                        relatedMetrics = listOf("cpuUsage")
                    )
                )
            }

            return alerts
        }
    }

    private class MetricsProcessor {
        private val metricAggregators = mutableMapOf<String, MutableList<Float>>()

        fun processEvent(event: AnalyticsEvent) {
            event.properties.forEach { (key, value) ->
                if (value is Number) {
                    metricAggregators.computeIfAbsent(key) { mutableListOf() }
                        .add(value.toFloat())
                }
            }

            // Keep only last 1000 values per metric
            metricAggregators.forEach { (_, values) ->
                if (values.size > 1000) {
                    values.removeAt(0)
                }
            }
        }

        fun getAggregatedMetrics(): Map<String, Float> {
            return metricAggregators.mapValues { (_, values) ->
                values.average().toFloat()
            }
        }
    }

    private class InsightGenerator {
        suspend fun generateUserInsights(
            userId: String,
            sessionMetrics: Map<String, SessionMetrics>
        ): List<AnalyticsInsight> {
            val userSessions = sessionMetrics.values.filter { session ->
                session.events.any { it.userId == userId }
            }

            if (userSessions.isEmpty()) return emptyList()

            val insights = mutableListOf<AnalyticsInsight>()

            // Session duration insight
            val avgDuration = userSessions.map {
                it.lastActivity - it.startTime
            }.average()

            if (avgDuration > 1800000) { // > 30 minutes
                insights.add(
                    AnalyticsInsight(
                        insightId = "duration-${userId}-${System.currentTimeMillis()}",
                        userId = userId,
                        type = InsightType.PERFORMANCE_TREND,
                        title = "Long Workout Sessions",
                        description = "Your average session is ${avgDuration / 60000} minutes",
                        recommendation = "Consider breaking into shorter, focused sessions",
                        confidence = 0.85f,
                        impact = ImpactLevel.MEDIUM,
                        validUntil = System.currentTimeMillis() + 86400000,
                        actionable = true,
                        relatedMetrics = listOf("sessionDuration")
                    )
                )
            }

            // Activity frequency insight
            val recentSessions = userSessions.filter {
                it.startTime > System.currentTimeMillis() - 604800000 // Last week
            }

            if (recentSessions.size >= 5) {
                insights.add(
                    AnalyticsInsight(
                        insightId = "frequency-${userId}-${System.currentTimeMillis()}",
                        userId = userId,
                        type = InsightType.ACHIEVEMENT,
                        title = "Consistent Training",
                        description = "You've completed ${recentSessions.size} sessions this week!",
                        recommendation = "Keep up the great work!",
                        confidence = 0.95f,
                        impact = ImpactLevel.HIGH,
                        validUntil = System.currentTimeMillis() + 86400000,
                        actionable = false,
                        relatedMetrics = listOf("sessionFrequency")
                    )
                )
            }

            return insights
        }
    }
}