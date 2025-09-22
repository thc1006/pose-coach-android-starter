package com.posecoach.analytics.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.Contextual
import java.time.Instant
import java.util.*

/**
 * Core analytics data models for the Pose Coach system
 */

@Serializable
data class UserPerformanceMetrics(
    val userId: String,
    val sessionId: String,
    val timestamp: Long,
    val workoutType: String,
    val duration: Long, // seconds
    val poseAccuracy: Float, // 0.0 - 1.0
    val energyExpenditure: Float, // calories
    val intensityLevel: IntensityLevel,
    val movementPatterns: List<MovementPattern>,
    val personalBests: List<PersonalBest>,
    val improvementRate: Float // percentage
)

@Serializable
data class MovementPattern(
    val type: String,
    val accuracy: Float,
    val consistency: Float,
    val improvement: Float,
    val frequency: Int,
    val timestamps: List<Long>
)

@Serializable
data class PersonalBest(
    val metric: String,
    val value: Float,
    val timestamp: Long,
    val sessionId: String
)

enum class IntensityLevel {
    LOW, MODERATE, HIGH, EXTREME
}

@Serializable
data class CoachingEffectivenessMetrics(
    val coachingSessionId: String,
    val userId: String,
    val timestamp: Long,
    val suggestionAccuracy: Float,
    val userCompliance: Float,
    val feedbackEffectiveness: Float,
    val personalizationScore: Float,
    val interventionSuccess: Boolean,
    val modalityUsed: CoachingModality,
    val improvementImpact: Float
)

enum class CoachingModality {
    VISUAL, AUDIO, HAPTIC, MULTIMODAL
}

@Serializable
data class SystemPerformanceMetrics(
    val sessionId: String,
    val timestamp: Long,
    val appLatency: Long, // milliseconds
    val aiProcessingTime: Long, // milliseconds
    val memoryUsage: Long, // bytes
    val cpuUsage: Float, // percentage
    val networkLatency: Long, // milliseconds
    val errorCount: Int,
    val frameRate: Float, // fps
    val deviceModel: String,
    val osVersion: String
)

@Serializable
data class BusinessIntelligenceMetrics(
    val aggregationId: String,
    val timestamp: Long,
    val activeUsers: Int,
    val sessionCount: Int,
    val averageSessionDuration: Float,
    val featureUsage: Map<String, Int>,
    val retentionRate: Float,
    val churnPrediction: Float,
    val demographicInsights: DemographicInsights,
    val privacyLevel: PrivacyLevel
)

@Serializable
data class DemographicInsights(
    val ageGroups: Map<String, Int>,
    val experienceLevels: Map<String, Int>,
    val geographicDistribution: Map<String, Int>,
    val deviceTypes: Map<String, Int>
)

enum class PrivacyLevel {
    ANONYMIZED, PSEUDONYMIZED, AGGREGATED, DIFFERENTIAL_PRIVATE
}

@Serializable
data class AnalyticsEvent(
    val eventId: String,
    val userId: String?,
    val sessionId: String,
    val timestamp: Long,
    val eventType: EventType,
    val category: EventCategory,
    val properties: Map<String, @Contextual Any>,
    val privacyLevel: PrivacyLevel,
    val retentionDays: Int = 30
)

enum class EventType {
    USER_ACTION, SYSTEM_METRIC, COACHING_FEEDBACK, PERFORMANCE_UPDATE,
    ERROR_EVENT, BUSINESS_METRIC, PRIVACY_EVENT
}

enum class EventCategory {
    WORKOUT, COACHING, SYSTEM, USER_ENGAGEMENT, PERFORMANCE,
    BUSINESS_INTELLIGENCE, PRIVACY_COMPLIANCE
}

@Serializable
data class DashboardConfiguration(
    val userId: String,
    val dashboardId: String,
    val layout: DashboardLayout,
    val widgets: List<WidgetConfiguration>,
    val refreshInterval: Long, // seconds
    val privacySettings: PrivacySettings,
    val customization: Map<String, @Contextual Any>
)

@Serializable
data class DashboardLayout(
    val type: LayoutType,
    val columns: Int,
    val rows: Int,
    val responsiveBreakpoints: Map<String, Int>
)

enum class LayoutType {
    GRID, FLEXIBLE, MASONRY, CARD_STACK
}

@Serializable
data class WidgetConfiguration(
    val widgetId: String,
    val type: WidgetType,
    val position: Position,
    val size: Size,
    val dataSource: String,
    val refreshRate: Long,
    val customProperties: Map<String, @Contextual Any>
)

@Serializable
data class Position(
    val x: Int,
    val y: Int,
    val z: Int = 0
)

@Serializable
data class Size(
    val width: Int,
    val height: Int
)

enum class WidgetType {
    LINE_CHART, BAR_CHART, PIE_CHART, HEATMAP, METRIC_CARD,
    POSE_3D_VIEWER, PROGRESS_RING, TABLE, LIVE_FEED, ALERT_PANEL
}

@Serializable
data class PrivacySettings(
    val dataCollection: Boolean,
    val personalizedAnalytics: Boolean,
    val anonymizedSharing: Boolean,
    val retentionPeriod: Int, // days
    val consentTimestamp: Long,
    val optOutOptions: List<String>
)

@Serializable
data class AnalyticsInsight(
    val insightId: String,
    val userId: String?,
    val type: InsightType,
    val title: String,
    val description: String,
    val recommendation: String,
    val confidence: Float,
    val impact: ImpactLevel,
    val timestamp: Long,
    val validUntil: Long,
    val actionable: Boolean,
    val relatedMetrics: List<String>
)

enum class InsightType {
    PERFORMANCE_TREND, IMPROVEMENT_OPPORTUNITY, ANOMALY_DETECTION,
    PREDICTION, RECOMMENDATION, ACHIEVEMENT, WARNING
}

enum class ImpactLevel {
    LOW, MEDIUM, HIGH, CRITICAL
}

@Serializable
data class RealtimeAnalyticsData(
    val streamId: String,
    val timestamp: Long,
    val metrics: Map<String, Float>,
    val events: List<AnalyticsEvent>,
    val alerts: List<AnalyticsAlert>,
    val latency: Long // milliseconds
)

@Serializable
data class AnalyticsAlert(
    val alertId: String,
    val type: AlertType,
    val severity: AlertSeverity,
    val message: String,
    val timestamp: Long,
    val acknowledged: Boolean = false,
    val actionRequired: Boolean,
    val relatedMetrics: List<String>
)

enum class AlertType {
    PERFORMANCE_DEGRADATION, ANOMALY_DETECTED, THRESHOLD_EXCEEDED,
    ERROR_RATE_HIGH, USER_CHURN_RISK, SYSTEM_OVERLOAD
}

enum class AlertSeverity {
    INFO, WARNING, ERROR, CRITICAL
}

