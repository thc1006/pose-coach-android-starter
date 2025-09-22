package com.posecoach.analytics.interfaces

import com.posecoach.analytics.models.*
import kotlinx.coroutines.flow.Flow

/**
 * Core analytics interfaces for the Pose Coach system
 */


interface AnalyticsEngine {
    suspend fun trackEvent(event: AnalyticsEvent)
    suspend fun trackUserPerformance(metrics: UserPerformanceMetrics)
    suspend fun trackCoachingEffectiveness(metrics: CoachingEffectivenessMetrics)
    suspend fun trackSystemPerformance(metrics: SystemPerformanceMetrics)
    fun getRealtimeStream(): Flow<RealtimeAnalyticsData>
    suspend fun generateInsights(userId: String): List<AnalyticsInsight>
}

interface DashboardRenderer {
    suspend fun renderDashboard(config: DashboardConfiguration): DashboardView
    suspend fun updateWidget(widgetId: String, data: Any)
    fun subscribeToRealtimeUpdates(): Flow<DashboardUpdate>
    suspend fun customizeDashboard(userId: String, customization: Map<String, Any>)
}

interface PrivacyEngine {
    suspend fun anonymizeData(data: Any): Any
    suspend fun applyDifferentialPrivacy(data: List<Any>, epsilon: Double): List<Any>
    suspend fun checkConsentRequirements(userId: String, dataType: String): Boolean
    suspend fun processDataDeletion(userId: String, dataTypes: List<String>)
    suspend fun auditPrivacyCompliance(): PrivacyAuditReport
}

interface BusinessIntelligenceEngine {
    suspend fun generateBusinessMetrics(): BusinessIntelligenceMetrics
    suspend fun predictChurnRisk(timeframe: Long): List<ChurnPrediction>
    suspend fun analyzeFeatureUsage(): FeatureUsageReport
    suspend fun generateRetentionAnalysis(): RetentionAnalysis
    suspend fun detectAnomalies(): List<AnomalyReport>
}

interface VisualizationEngine {
    suspend fun renderChart(type: WidgetType, data: Any): ChartVisualization
    suspend fun render3DPose(poseData: PoseData): Pose3DVisualization
    suspend fun generateHeatmap(data: Map<String, Float>): HeatmapVisualization
    suspend fun createInteractiveTimeline(events: List<AnalyticsEvent>): TimelineVisualization
}

interface ReportingEngine {
    suspend fun generateAutomatedReport(type: ReportType, parameters: ReportParameters): AnalyticsReport
    suspend fun scheduleReport(config: ReportScheduleConfig)
    suspend fun detectAnomaliesAndAlert(): List<AnalyticsAlert>
    suspend fun exportReport(reportId: String, format: ExportFormat): ByteArray
}

interface DataPipelineManager {
    suspend fun ingestData(data: Any)
    fun startRealtimeProcessing(): Flow<ProcessedData>
    suspend fun aggregateMetrics(timeWindow: Long): AggregatedMetrics
    suspend fun optimizePipeline(): PipelineOptimizationResult
}

interface AnalyticsRepository {
    suspend fun storeEvent(event: AnalyticsEvent)
    suspend fun storeMetrics(metrics: Any)
    suspend fun storeUserMetrics(metrics: UserPerformanceMetrics)
    suspend fun queryMetrics(query: AnalyticsQuery): List<Any>
    suspend fun getTimeSeriesData(metric: String, timeRange: TimeRange): List<TimeSeriesPoint>
    suspend fun deleteUserData(userId: String)
}

// Data classes for interface implementations

data class DashboardView(
    val widgets: List<RenderedWidget>,
    val layout: DashboardLayout,
    val metadata: Map<String, Any>
)

data class RenderedWidget(
    val widgetId: String,
    val content: Any,
    val lastUpdated: Long,
    val nextUpdate: Long
)

data class DashboardUpdate(
    val widgetId: String,
    val data: Any,
    val timestamp: Long
)

data class PrivacyAuditReport(
    val timestamp: Long,
    val complianceScore: Float,
    val violations: List<PrivacyViolation>,
    val recommendations: List<String>
)

data class PrivacyViolation(
    val type: String,
    val severity: String,
    val description: String,
    val affectedRecords: Int
)

data class ChurnPrediction(
    val userId: String,
    val riskScore: Float,
    val factors: List<String>,
    val recommendedActions: List<String>
)

data class FeatureUsageReport(
    val features: Map<String, FeatureUsageMetrics>,
    val trends: Map<String, TrendAnalysis>,
    val recommendations: List<String>
)

data class FeatureUsageMetrics(
    val usageCount: Int,
    val uniqueUsers: Int,
    val averageSessionTime: Float,
    val retentionRate: Float
)

data class TrendAnalysis(
    val direction: TrendDirection,
    val magnitude: Float,
    val confidence: Float,
    val timeframe: String
)

enum class TrendDirection {
    INCREASING, DECREASING, STABLE, VOLATILE
}

data class RetentionAnalysis(
    val cohorts: Map<String, CohortAnalysis>,
    val overallRetention: Float,
    val trends: List<TrendAnalysis>
)

data class CohortAnalysis(
    val cohortId: String,
    val size: Int,
    val retentionRates: Map<Int, Float>, // day -> retention rate
    val churnFactors: List<String>
)

data class AnomalyReport(
    val anomalyId: String,
    val type: AnomalyType,
    val severity: Float,
    val description: String,
    val affectedMetrics: List<String>,
    val timestamp: Long,
    val possibleCauses: List<String>
)

enum class AnomalyType {
    SPIKE, DROP, PATTERN_CHANGE, OUTLIER, CORRELATION_BREAK
}

data class ChartVisualization(
    val chartId: String,
    val type: WidgetType,
    val data: Any,
    val configuration: ChartConfiguration,
    val interactivity: InteractivityConfig
)

data class ChartConfiguration(
    val colors: List<String>,
    val axes: AxesConfiguration,
    val legend: LegendConfiguration,
    val animations: AnimationConfiguration
)

data class AxesConfiguration(
    val xAxis: AxisConfig,
    val yAxis: AxisConfig
)

data class AxisConfig(
    val label: String,
    val scale: ScaleType,
    val range: Pair<Float, Float>?
)

enum class ScaleType {
    LINEAR, LOGARITHMIC, TIME
}

data class LegendConfiguration(
    val position: LegendPosition,
    val visible: Boolean
)

enum class LegendPosition {
    TOP, BOTTOM, LEFT, RIGHT, NONE
}

data class AnimationConfiguration(
    val enabled: Boolean,
    val duration: Long,
    val easing: EasingType
)

enum class EasingType {
    LINEAR, EASE_IN, EASE_OUT, EASE_IN_OUT, BOUNCE
}

data class InteractivityConfig(
    val zoomEnabled: Boolean,
    val panEnabled: Boolean,
    val selectionEnabled: Boolean,
    val tooltipsEnabled: Boolean
)

data class Pose3DVisualization(
    val poseId: String,
    val skeleton: Skeleton3D,
    val confidence: Float,
    val annotations: List<PoseAnnotation>
)

data class Skeleton3D(
    val joints: List<Joint3D>,
    val connections: List<Connection3D>,
    val bounds: BoundingBox3D
)

data class Joint3D(
    val id: String,
    val position: Vector3D,
    val confidence: Float,
    val visible: Boolean
)

data class Vector3D(
    val x: Float,
    val y: Float,
    val z: Float
)

data class Connection3D(
    val from: String,
    val to: String,
    val confidence: Float
)

data class BoundingBox3D(
    val min: Vector3D,
    val max: Vector3D
)

data class PoseAnnotation(
    val type: AnnotationType,
    val position: Vector3D,
    val text: String,
    val color: String
)

enum class AnnotationType {
    CORRECTION, ACHIEVEMENT, WARNING, INFO
}

data class HeatmapVisualization(
    val heatmapId: String,
    val data: Array<Array<Float>>,
    val colorScale: ColorScale,
    val labels: HeatmapLabels
)

data class ColorScale(
    val min: String,
    val max: String,
    val steps: List<String>
)

data class HeatmapLabels(
    val xLabels: List<String>,
    val yLabels: List<String>
)

data class TimelineVisualization(
    val timelineId: String,
    val events: List<TimelineEvent>,
    val timeRange: TimeRange,
    val interactivity: TimelineInteractivity
)

data class TimelineEvent(
    val id: String,
    val timestamp: Long,
    val title: String,
    val description: String,
    val category: String,
    val importance: ImportanceLevel
)

enum class ImportanceLevel {
    LOW, MEDIUM, HIGH, CRITICAL
}

data class TimelineInteractivity(
    val zoomEnabled: Boolean,
    val filterEnabled: Boolean,
    val detailViewEnabled: Boolean
)

data class AnalyticsReport(
    val reportId: String,
    val type: ReportType,
    val generatedAt: Long,
    val data: Any,
    val summary: ReportSummary,
    val recommendations: List<String>
)

enum class ReportType {
    PERFORMANCE, USER_ENGAGEMENT, BUSINESS_METRICS, SYSTEM_HEALTH,
    PRIVACY_COMPLIANCE, COACHING_EFFECTIVENESS, CUSTOM
}

// Constants for coaching effectiveness metrics
object CoachingConstants {
    const val COACHING_EFFECTIVENESS = "coaching_effectiveness"
    const val MIN_COACHING_EFFECTIVENESS = 0.6f
    const val OPTIMAL_COACHING_EFFECTIVENESS = 0.85f
    const val EFFECTIVENESS_WEIGHT = 0.7f
}

data class ReportParameters(
    val timeRange: TimeRange,
    val metrics: List<String>,
    val filters: Map<String, Any>,
    val granularity: TimeGranularity
)

data class TimeRange(
    val start: Long,
    val end: Long
)

enum class TimeGranularity {
    MINUTE, HOUR, DAY, WEEK, MONTH, YEAR
}

data class ReportSummary(
    val keyMetrics: Map<String, Float>,
    val trends: List<TrendAnalysis>,
    val insights: List<AnalyticsInsight>
)

data class ReportScheduleConfig(
    val reportType: ReportType,
    val frequency: ReportFrequency,
    val recipients: List<String>,
    val parameters: ReportParameters
)

enum class ReportFrequency {
    HOURLY, DAILY, WEEKLY, MONTHLY, QUARTERLY
}

enum class ExportFormat {
    PDF, CSV, JSON, EXCEL, PNG
}

data class ProcessedData(
    val dataId: String,
    val processedAt: Long,
    val data: Any,
    val processingTime: Long,
    val quality: DataQuality
)

data class DataQuality(
    val completeness: Float,
    val accuracy: Float,
    val consistency: Float,
    val timeliness: Float
)

data class AggregatedMetrics(
    val timeWindow: Long,
    val metrics: Map<String, Float>,
    val counts: Map<String, Int>,
    val aggregationMethod: AggregationMethod
)

enum class AggregationMethod {
    SUM, AVERAGE, MEDIAN, MIN, MAX, COUNT, DISTINCT_COUNT
}

data class PipelineOptimizationResult(
    val optimizationId: String,
    val improvementPercentage: Float,
    val optimizations: List<OptimizationAction>,
    val estimatedSavings: ResourceSavings
)

data class OptimizationAction(
    val type: OptimizationType,
    val description: String,
    val impact: Float
)

enum class OptimizationType {
    CACHING, INDEXING, BATCHING, COMPRESSION, PARALLELIZATION
}

data class ResourceSavings(
    val cpuSavings: Float,
    val memorySavings: Float,
    val networkSavings: Float,
    val storageSavings: Float
)

data class AnalyticsQuery(
    val select: List<String>,
    val where: Map<String, Any>,
    val groupBy: List<String>,
    val orderBy: List<OrderBy>,
    val limit: Int?,
    val offset: Int?
)

data class OrderBy(
    val field: String,
    val direction: SortDirection
)

enum class SortDirection {
    ASC, DESC
}

data class TimeSeriesPoint(
    val timestamp: Long,
    val value: Float,
    val metadata: Map<String, Any>?
)

data class PoseData(
    val joints: List<Joint3D>,
    val timestamp: Long,
    val confidence: Float,
    val frameId: String
)