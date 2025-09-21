package com.posecoach.analytics.visualization

import com.posecoach.analytics.interfaces.*
import com.posecoach.analytics.models.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Comprehensive visualization engine for analytics data
 * Supports 2D charts, 3D pose rendering, heatmaps, and interactive timelines
 */
@Singleton
class VisualizationEngine @Inject constructor() : com.posecoach.analytics.interfaces.VisualizationEngine {

    override suspend fun renderChart(type: WidgetType, data: Any): ChartVisualization = withContext(Dispatchers.Default) {
        when (type) {
            WidgetType.LINE_CHART -> renderLineChart(data)
            WidgetType.BAR_CHART -> renderBarChart(data)
            WidgetType.PIE_CHART -> renderPieChart(data)
            else -> createEmptyChart(type)
        }
    }

    override suspend fun render3DPose(poseData: PoseData): Pose3DVisualization = withContext(Dispatchers.Default) {
        val skeleton = create3DSkeleton(poseData)
        val annotations = generatePoseAnnotations(poseData)

        Pose3DVisualization(
            poseId = poseData.frameId,
            skeleton = skeleton,
            confidence = poseData.confidence,
            annotations = annotations
        )
    }

    override suspend fun generateHeatmap(data: Map<String, Float>): HeatmapVisualization = withContext(Dispatchers.Default) {
        val gridSize = calculateOptimalGridSize(data.size)
        val heatmapData = convertToHeatmapGrid(data, gridSize)
        val colorScale = generateColorScale()
        val labels = generateHeatmapLabels(data.keys.toList(), gridSize)

        HeatmapVisualization(
            heatmapId = "heatmap_${System.currentTimeMillis()}",
            data = heatmapData,
            colorScale = colorScale,
            labels = labels
        )
    }

    override suspend fun createInteractiveTimeline(events: List<AnalyticsEvent>): TimelineVisualization = withContext(Dispatchers.Default) {
        val timelineEvents = events.map { event ->
            TimelineEvent(
                id = event.eventId,
                timestamp = event.timestamp,
                title = formatEventTitle(event),
                description = formatEventDescription(event),
                category = event.category.name,
                importance = determineEventImportance(event)
            )
        }.sortedBy { it.timestamp }

        val timeRange = if (events.isNotEmpty()) {
            TimeRange(
                start = events.minOf { it.timestamp },
                end = events.maxOf { it.timestamp }
            )
        } else {
            TimeRange(0, 0)
        }

        TimelineVisualization(
            timelineId = "timeline_${System.currentTimeMillis()}",
            events = timelineEvents,
            timeRange = timeRange,
            interactivity = TimelineInteractivity(
                zoomEnabled = true,
                filterEnabled = true,
                detailViewEnabled = true
            )
        )
    }

    private fun renderLineChart(data: Any): ChartVisualization {
        val chartData = when (data) {
            is List<*> -> data.filterIsInstance<Number>().map { it.toFloat() }
            is Map<*, *> -> data.values.filterIsInstance<Number>().map { it.toFloat() }
            is UserPerformanceMetrics -> listOf(
                data.poseAccuracy,
                data.energyExpenditure / 10, // Normalize
                data.improvementRate
            )
            else -> listOf(0f)
        }

        return ChartVisualization(
            chartId = "line_chart_${System.currentTimeMillis()}",
            type = WidgetType.LINE_CHART,
            data = chartData,
            configuration = ChartConfiguration(
                colors = listOf("#2196F3", "#4CAF50", "#FF9800"),
                axes = AxesConfiguration(
                    xAxis = AxisConfig("Time", ScaleType.LINEAR, null),
                    yAxis = AxisConfig("Value", ScaleType.LINEAR, null)
                ),
                legend = LegendConfiguration(LegendPosition.TOP, true),
                animations = AnimationConfiguration(true, 1000, EasingType.EASE_IN_OUT)
            ),
            interactivity = InteractivityConfig(
                zoomEnabled = true,
                panEnabled = true,
                selectionEnabled = false,
                tooltipsEnabled = true
            )
        )
    }

    private fun renderBarChart(data: Any): ChartVisualization {
        val chartData = when (data) {
            is Map<*, *> -> data.entries.associate {
                it.key.toString() to (it.value as? Number)?.toFloat() ?: 0f
            }
            is List<*> -> data.filterIsInstance<Number>()
                .mapIndexed { index, value -> "Item $index" to value.toFloat() }
                .toMap()
            else -> mapOf("No Data" to 0f)
        }

        return ChartVisualization(
            chartId = "bar_chart_${System.currentTimeMillis()}",
            type = WidgetType.BAR_CHART,
            data = chartData,
            configuration = ChartConfiguration(
                colors = generateColorPalette(chartData.size),
                axes = AxesConfiguration(
                    xAxis = AxisConfig("Categories", ScaleType.LINEAR, null),
                    yAxis = AxisConfig("Values", ScaleType.LINEAR, null)
                ),
                legend = LegendConfiguration(LegendPosition.RIGHT, true),
                animations = AnimationConfiguration(true, 800, EasingType.BOUNCE)
            ),
            interactivity = InteractivityConfig(
                zoomEnabled = false,
                panEnabled = false,
                selectionEnabled = true,
                tooltipsEnabled = true
            )
        )
    }

    private fun renderPieChart(data: Any): ChartVisualization {
        val chartData = when (data) {
            is Map<*, *> -> data.entries.associate {
                it.key.toString() to (it.value as? Number)?.toFloat() ?: 0f
            }
            is CoachingEffectivenessMetrics -> mapOf(
                "Accuracy" to data.suggestionAccuracy,
                "Compliance" to data.userCompliance,
                "Effectiveness" to data.feedbackEffectiveness
            )
            else -> mapOf("No Data" to 1f)
        }

        return ChartVisualization(
            chartId = "pie_chart_${System.currentTimeMillis()}",
            type = WidgetType.PIE_CHART,
            data = chartData,
            configuration = ChartConfiguration(
                colors = generateColorPalette(chartData.size),
                axes = AxesConfiguration(
                    xAxis = AxisConfig("", ScaleType.LINEAR, null),
                    yAxis = AxisConfig("", ScaleType.LINEAR, null)
                ),
                legend = LegendConfiguration(LegendPosition.RIGHT, true),
                animations = AnimationConfiguration(true, 1200, EasingType.EASE_OUT)
            ),
            interactivity = InteractivityConfig(
                zoomEnabled = false,
                panEnabled = false,
                selectionEnabled = true,
                tooltipsEnabled = true
            )
        )
    }

    private fun createEmptyChart(type: WidgetType): ChartVisualization {
        return ChartVisualization(
            chartId = "empty_chart_${System.currentTimeMillis()}",
            type = type,
            data = emptyList<Float>(),
            configuration = ChartConfiguration(
                colors = listOf("#CCCCCC"),
                axes = AxesConfiguration(
                    xAxis = AxisConfig("", ScaleType.LINEAR, null),
                    yAxis = AxisConfig("", ScaleType.LINEAR, null)
                ),
                legend = LegendConfiguration(LegendPosition.NONE, false),
                animations = AnimationConfiguration(false, 0, EasingType.LINEAR)
            ),
            interactivity = InteractivityConfig(
                zoomEnabled = false,
                panEnabled = false,
                selectionEnabled = false,
                tooltipsEnabled = false
            )
        )
    }

    private fun create3DSkeleton(poseData: PoseData): Skeleton3D {
        val joints = poseData.joints.map { joint ->
            Joint3D(
                id = joint.id,
                position = joint.position,
                confidence = joint.confidence,
                visible = joint.visible && joint.confidence > 0.5f
            )
        }

        val connections = generateSkeletonConnections(joints)
        val bounds = calculateBoundingBox(joints)

        return Skeleton3D(
            joints = joints,
            connections = connections,
            bounds = bounds
        )
    }

    private fun generateSkeletonConnections(joints: List<Joint3D>): List<Connection3D> {
        // Define standard human skeleton connections
        val connectionPairs = listOf(
            // Head to torso
            "head" to "neck",
            "neck" to "chest",
            "chest" to "waist",

            // Arms
            "neck" to "left_shoulder",
            "left_shoulder" to "left_elbow",
            "left_elbow" to "left_wrist",
            "neck" to "right_shoulder",
            "right_shoulder" to "right_elbow",
            "right_elbow" to "right_wrist",

            // Legs
            "waist" to "left_hip",
            "left_hip" to "left_knee",
            "left_knee" to "left_ankle",
            "waist" to "right_hip",
            "right_hip" to "right_knee",
            "right_knee" to "right_ankle"
        )

        val jointMap = joints.associateBy { it.id }

        return connectionPairs.mapNotNull { (from, to) ->
            val fromJoint = jointMap[from]
            val toJoint = jointMap[to]

            if (fromJoint != null && toJoint != null && fromJoint.visible && toJoint.visible) {
                Connection3D(
                    from = from,
                    to = to,
                    confidence = minOf(fromJoint.confidence, toJoint.confidence)
                )
            } else null
        }
    }

    private fun calculateBoundingBox(joints: List<Joint3D>): BoundingBox3D {
        val visibleJoints = joints.filter { it.visible }

        if (visibleJoints.isEmpty()) {
            return BoundingBox3D(
                min = Vector3D(0f, 0f, 0f),
                max = Vector3D(0f, 0f, 0f)
            )
        }

        val minX = visibleJoints.minOf { it.position.x }
        val maxX = visibleJoints.maxOf { it.position.x }
        val minY = visibleJoints.minOf { it.position.y }
        val maxY = visibleJoints.maxOf { it.position.y }
        val minZ = visibleJoints.minOf { it.position.z }
        val maxZ = visibleJoints.maxOf { it.position.z }

        return BoundingBox3D(
            min = Vector3D(minX, minY, minZ),
            max = Vector3D(maxX, maxY, maxZ)
        )
    }

    private fun generatePoseAnnotations(poseData: PoseData): List<PoseAnnotation> {
        val annotations = mutableListOf<PoseAnnotation>()

        // Add annotations for low confidence joints
        poseData.joints.forEach { joint ->
            when {
                joint.confidence < 0.3f -> {
                    annotations.add(
                        PoseAnnotation(
                            type = AnnotationType.WARNING,
                            position = joint.position,
                            text = "Low confidence: ${joint.id}",
                            color = "#FF5722"
                        )
                    )
                }
                joint.confidence > 0.9f -> {
                    annotations.add(
                        PoseAnnotation(
                            type = AnnotationType.ACHIEVEMENT,
                            position = joint.position,
                            text = "Excellent: ${joint.id}",
                            color = "#4CAF50"
                        )
                    )
                }
            }
        }

        // Add correction suggestions based on pose analysis
        val corrections = analyzePoseForCorrections(poseData)
        annotations.addAll(corrections)

        return annotations
    }

    private fun analyzePoseForCorrections(poseData: PoseData): List<PoseAnnotation> {
        val corrections = mutableListOf<PoseAnnotation>()
        val jointMap = poseData.joints.associateBy { it.id }

        // Check shoulder alignment
        val leftShoulder = jointMap["left_shoulder"]
        val rightShoulder = jointMap["right_shoulder"]

        if (leftShoulder != null && rightShoulder != null) {
            val heightDiff = abs(leftShoulder.position.y - rightShoulder.position.y)
            if (heightDiff > 0.1f) { // Threshold for shoulder misalignment
                val correctionPoint = Vector3D(
                    (leftShoulder.position.x + rightShoulder.position.x) / 2,
                    (leftShoulder.position.y + rightShoulder.position.y) / 2,
                    (leftShoulder.position.z + rightShoulder.position.z) / 2
                )

                corrections.add(
                    PoseAnnotation(
                        type = AnnotationType.CORRECTION,
                        position = correctionPoint,
                        text = "Level your shoulders",
                        color = "#FFC107"
                    )
                )
            }
        }

        // Check knee alignment
        val leftKnee = jointMap["left_knee"]
        val rightKnee = jointMap["right_knee"]
        val leftAnkle = jointMap["left_ankle"]
        val rightAnkle = jointMap["right_ankle"]

        if (leftKnee != null && leftAnkle != null) {
            val kneeAnkleAlignment = abs(leftKnee.position.x - leftAnkle.position.x)
            if (kneeAnkleAlignment > 0.05f) {
                corrections.add(
                    PoseAnnotation(
                        type = AnnotationType.CORRECTION,
                        position = leftKnee.position,
                        text = "Align left knee over ankle",
                        color = "#FF9800"
                    )
                )
            }
        }

        return corrections
    }

    private fun calculateOptimalGridSize(dataSize: Int): Int {
        return when {
            dataSize <= 4 -> 2
            dataSize <= 9 -> 3
            dataSize <= 16 -> 4
            dataSize <= 25 -> 5
            dataSize <= 36 -> 6
            else -> ceil(sqrt(dataSize.toDouble())).toInt()
        }
    }

    private fun convertToHeatmapGrid(data: Map<String, Float>, gridSize: Int): Array<Array<Float>> {
        val grid = Array(gridSize) { Array(gridSize) { 0f } }
        val dataList = data.values.toList()

        for (i in 0 until minOf(dataList.size, gridSize * gridSize)) {
            val row = i / gridSize
            val col = i % gridSize
            grid[row][col] = dataList[i]
        }

        return grid
    }

    private fun generateColorScale(): ColorScale {
        return ColorScale(
            min = "#0000FF", // Blue for minimum
            max = "#FF0000", // Red for maximum
            steps = listOf(
                "#0000FF", "#0080FF", "#00FFFF", "#00FF80",
                "#00FF00", "#80FF00", "#FFFF00", "#FF8000", "#FF0000"
            )
        )
    }

    private fun generateHeatmapLabels(keys: List<String>, gridSize: Int): HeatmapLabels {
        val xLabels = mutableListOf<String>()
        val yLabels = mutableListOf<String>()

        for (i in 0 until gridSize) {
            xLabels.add("Col $i")
            yLabels.add("Row $i")
        }

        // Use actual data keys if available
        keys.take(gridSize * gridSize).forEachIndexed { index, key ->
            val row = index / gridSize
            val col = index % gridSize
            if (row < yLabels.size && col < xLabels.size) {
                xLabels[col] = key.take(8) // Truncate long labels
                yLabels[row] = if (row == 0) key.take(8) else yLabels[row]
            }
        }

        return HeatmapLabels(
            xLabels = xLabels,
            yLabels = yLabels
        )
    }

    private fun formatEventTitle(event: AnalyticsEvent): String {
        return when (event.eventType) {
            EventType.USER_ACTION -> "User Action"
            EventType.SYSTEM_METRIC -> "System Update"
            EventType.COACHING_FEEDBACK -> "Coaching Session"
            EventType.PERFORMANCE_UPDATE -> "Performance Milestone"
            EventType.ERROR_EVENT -> "System Error"
            EventType.BUSINESS_METRIC -> "Business Update"
            EventType.PRIVACY_EVENT -> "Privacy Event"
        }
    }

    private fun formatEventDescription(event: AnalyticsEvent): String {
        val properties = event.properties.entries.take(3).joinToString(", ") { (key, value) ->
            "$key: $value"
        }
        return if (properties.isNotEmpty()) properties else "No additional details"
    }

    private fun determineEventImportance(event: AnalyticsEvent): ImportanceLevel {
        return when (event.eventType) {
            EventType.ERROR_EVENT -> ImportanceLevel.CRITICAL
            EventType.PERFORMANCE_UPDATE -> ImportanceLevel.HIGH
            EventType.COACHING_FEEDBACK -> ImportanceLevel.MEDIUM
            EventType.USER_ACTION -> ImportanceLevel.MEDIUM
            EventType.SYSTEM_METRIC -> ImportanceLevel.LOW
            EventType.BUSINESS_METRIC -> ImportanceLevel.LOW
            EventType.PRIVACY_EVENT -> ImportanceLevel.HIGH
        }
    }

    private fun generateColorPalette(size: Int): List<String> {
        val baseColors = listOf(
            "#2196F3", "#4CAF50", "#FF9800", "#9C27B0", "#F44336",
            "#00BCD4", "#8BC34A", "#FFC107", "#E91E63", "#607D8B"
        )

        return if (size <= baseColors.size) {
            baseColors.take(size)
        } else {
            // Generate additional colors by interpolating
            val result = baseColors.toMutableList()
            while (result.size < size) {
                result.addAll(baseColors.map { adjustColorBrightness(it, 0.7f) })
            }
            result.take(size)
        }
    }

    private fun adjustColorBrightness(color: String, factor: Float): String {
        // Simple color brightness adjustment
        val hex = color.removePrefix("#")
        val r = (hex.substring(0, 2).toInt(16) * factor).toInt().coerceIn(0, 255)
        val g = (hex.substring(2, 4).toInt(16) * factor).toInt().coerceIn(0, 255)
        val b = (hex.substring(4, 6).toInt(16) * factor).toInt().coerceIn(0, 255)

        return "#%02X%02X%02X".format(r, g, b)
    }
}

/**
 * Advanced 3D pose analysis utilities
 */
class PoseAnalyzer {

    fun calculatePoseScore(pose: Pose3DVisualization): Float {
        val jointConfidences = pose.skeleton.joints.map { it.confidence }
        val averageConfidence = jointConfidences.average().toFloat()

        val connectionQuality = pose.skeleton.connections.map { it.confidence }.average().toFloat()

        val completeness = pose.skeleton.joints.count { it.visible }.toFloat() / pose.skeleton.joints.size

        return (averageConfidence * 0.4f + connectionQuality * 0.3f + completeness * 0.3f)
    }

    fun detectPoseAnomalies(pose: Pose3DVisualization): List<PoseAnomaly> {
        val anomalies = mutableListOf<PoseAnomaly>()
        val joints = pose.skeleton.joints.associateBy { it.id }

        // Check for anatomically impossible positions
        val leftShoulder = joints["left_shoulder"]
        val leftElbow = joints["left_elbow"]
        val leftWrist = joints["left_wrist"]

        if (leftShoulder != null && leftElbow != null && leftWrist != null) {
            val upperArmLength = distance3D(leftShoulder.position, leftElbow.position)
            val forearmLength = distance3D(leftElbow.position, leftWrist.position)

            // Check if arm is unnaturally stretched
            val totalArmSpan = distance3D(leftShoulder.position, leftWrist.position)
            val sumOfSegments = upperArmLength + forearmLength

            if (totalArmSpan > sumOfSegments * 1.1f) { // 10% tolerance
                anomalies.add(
                    PoseAnomaly(
                        type = "UNNATURAL_STRETCH",
                        severity = 0.8f,
                        description = "Left arm appears unnaturally stretched",
                        affectedJoints = listOf("left_shoulder", "left_elbow", "left_wrist")
                    )
                )
            }
        }

        return anomalies
    }

    private fun distance3D(p1: Vector3D, p2: Vector3D): Float {
        val dx = p1.x - p2.x
        val dy = p1.y - p2.y
        val dz = p1.z - p2.z
        return sqrt(dx * dx + dy * dy + dz * dz)
    }
}

data class PoseAnomaly(
    val type: String,
    val severity: Float,
    val description: String,
    val affectedJoints: List<String>
)

/**
 * Real-time visualization performance optimizer
 */
class VisualizationOptimizer {

    private val performanceMetrics = mutableMapOf<String, PerformanceMetric>()

    data class PerformanceMetric(
        val renderTime: Long,
        val memoryUsage: Long,
        val frameRate: Float,
        val complexity: Int
    )

    fun optimizeVisualization(visualization: Any): Any {
        return when (visualization) {
            is ChartVisualization -> optimizeChart(visualization)
            is Pose3DVisualization -> optimize3DPose(visualization)
            is HeatmapVisualization -> optimizeHeatmap(visualization)
            else -> visualization
        }
    }

    private fun optimizeChart(chart: ChartVisualization): ChartVisualization {
        val dataSize = when (val data = chart.data) {
            is List<*> -> data.size
            is Map<*, *> -> data.size
            else -> 0
        }

        // Reduce data points if too many for smooth rendering
        val optimizedData = if (dataSize > 1000) {
            downsampleData(chart.data, 500)
        } else {
            chart.data
        }

        // Disable expensive animations for large datasets
        val optimizedConfig = if (dataSize > 200) {
            chart.configuration.copy(
                animations = chart.configuration.animations.copy(
                    enabled = false
                )
            )
        } else {
            chart.configuration
        }

        return chart.copy(
            data = optimizedData,
            configuration = optimizedConfig
        )
    }

    private fun optimize3DPose(pose: Pose3DVisualization): Pose3DVisualization {
        // Remove low-confidence joints to improve performance
        val optimizedJoints = pose.skeleton.joints.filter { it.confidence > 0.3f }

        // Reduce connections based on joint visibility
        val visibleJointIds = optimizedJoints.map { it.id }.toSet()
        val optimizedConnections = pose.skeleton.connections.filter {
            it.from in visibleJointIds && it.to in visibleJointIds
        }

        val optimizedSkeleton = pose.skeleton.copy(
            joints = optimizedJoints,
            connections = optimizedConnections
        )

        return pose.copy(skeleton = optimizedSkeleton)
    }

    private fun optimizeHeatmap(heatmap: HeatmapVisualization): HeatmapVisualization {
        val dataSize = heatmap.data.size * heatmap.data.firstOrNull()?.size ?: 1

        // Reduce resolution for large heatmaps
        val optimizedData = if (dataSize > 2500) { // 50x50
            downsampleHeatmapData(heatmap.data, 25) // Reduce to 25x25
        } else {
            heatmap.data
        }

        return heatmap.copy(data = optimizedData)
    }

    private fun downsampleData(data: Any, targetSize: Int): Any {
        return when (data) {
            is List<*> -> {
                if (data.size <= targetSize) data
                else {
                    val step = data.size.toDouble() / targetSize
                    (0 until targetSize).map { i ->
                        data[(i * step).toInt()]
                    }
                }
            }
            is Map<*, *> -> {
                if (data.size <= targetSize) data
                else {
                    data.entries.take(targetSize).associate { it.key to it.value }
                }
            }
            else -> data
        }
    }

    private fun downsampleHeatmapData(data: Array<Array<Float>>, targetSize: Int): Array<Array<Float>> {
        val originalSize = data.size
        if (originalSize <= targetSize) return data

        val result = Array(targetSize) { Array(targetSize) { 0f } }
        val scale = originalSize.toDouble() / targetSize

        for (i in 0 until targetSize) {
            for (j in 0 until targetSize) {
                val sourceI = (i * scale).toInt().coerceIn(0, originalSize - 1)
                val sourceJ = (j * scale).toInt().coerceIn(0, originalSize - 1)
                result[i][j] = data[sourceI][sourceJ]
            }
        }

        return result
    }

    fun recordPerformance(visualizationId: String, metric: PerformanceMetric) {
        performanceMetrics[visualizationId] = metric
    }

    fun getPerformanceReport(): Map<String, PerformanceMetric> {
        return performanceMetrics.toMap()
    }
}