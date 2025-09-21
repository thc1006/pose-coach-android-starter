package com.posecoach.analytics.dashboard

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.compose.ui.viewinterop.AndroidView
import com.posecoach.analytics.interfaces.*
import com.posecoach.analytics.models.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Interactive dashboard framework built with Jetpack Compose
 * Provides real-time data visualization with customizable layouts
 */
@Singleton
class InteractiveDashboard @Inject constructor(
    private val analyticsEngine: AnalyticsEngine,
    private val visualizationEngine: VisualizationEngine,
    private val privacyEngine: PrivacyEngine
) : DashboardRenderer {

    private val _dashboardUpdates = MutableSharedFlow<DashboardUpdate>(replay = 1)

    override suspend fun renderDashboard(config: DashboardConfiguration): DashboardView {
        val widgets = config.widgets.map { widgetConfig ->
            val data = fetchWidgetData(widgetConfig)
            val visualization = when (widgetConfig.type) {
                WidgetType.LINE_CHART -> visualizationEngine.renderChart(widgetConfig.type, data)
                WidgetType.PIE_CHART -> visualizationEngine.renderChart(widgetConfig.type, data)
                WidgetType.HEATMAP -> visualizationEngine.generateHeatmap(data as Map<String, Float>)
                WidgetType.POSE_3D_VIEWER -> visualizationEngine.render3DPose(data as PoseData)
                else -> null
            }

            RenderedWidget(
                widgetId = widgetConfig.widgetId,
                content = visualization ?: data,
                lastUpdated = System.currentTimeMillis(),
                nextUpdate = System.currentTimeMillis() + (widgetConfig.refreshRate * 1000)
            )
        }

        return DashboardView(
            widgets = widgets,
            layout = config.layout,
            metadata = mapOf(
                "renderTime" to System.currentTimeMillis(),
                "widgetCount" to widgets.size,
                "userId" to config.userId
            )
        )
    }

    override suspend fun updateWidget(widgetId: String, data: Any) {
        val update = DashboardUpdate(
            widgetId = widgetId,
            data = data,
            timestamp = System.currentTimeMillis()
        )
        _dashboardUpdates.emit(update)
    }

    override fun subscribeToRealtimeUpdates(): Flow<DashboardUpdate> {
        return _dashboardUpdates.asSharedFlow()
    }

    override suspend fun customizeDashboard(
        userId: String,
        customization: Map<String, Any>
    ) {
        // Implementation would save customization preferences
        // and trigger dashboard re-render
    }

    private suspend fun fetchWidgetData(config: WidgetConfiguration): Any {
        // Fetch data based on widget configuration
        return when (config.dataSource) {
            "realtime_metrics" -> fetchRealtimeMetrics()
            "user_performance" -> fetchUserPerformance(config)
            "coaching_effectiveness" -> fetchCoachingData(config)
            "system_health" -> fetchSystemMetrics()
            else -> emptyMap<String, Any>()
        }
    }

    private suspend fun fetchRealtimeMetrics(): Map<String, Float> {
        return analyticsEngine.getRealtimeStream().first().metrics
    }

    private suspend fun fetchUserPerformance(config: WidgetConfiguration): List<UserPerformanceMetrics> {
        // This would integrate with the analytics repository
        return emptyList()
    }

    private suspend fun fetchCoachingData(config: WidgetConfiguration): List<CoachingEffectivenessMetrics> {
        return emptyList()
    }

    private suspend fun fetchSystemMetrics(): Map<String, Float> {
        return mapOf(
            "cpu_usage" to 45.2f,
            "memory_usage" to 67.8f,
            "response_time" to 120.0f,
            "error_rate" to 0.5f
        )
    }
}

/**
 * Composable dashboard with responsive layout and real-time updates
 */
@Composable
fun AnalyticsDashboard(
    config: DashboardConfiguration,
    dashboard: InteractiveDashboard,
    modifier: Modifier = Modifier
) {
    var dashboardView by remember { mutableStateOf<DashboardView?>(null) }
    val scope = rememberCoroutineScope()

    // Load dashboard data
    LaunchedEffect(config) {
        dashboardView = dashboard.renderDashboard(config)
    }

    // Subscribe to real-time updates
    LaunchedEffect(dashboard) {
        dashboard.subscribeToRealtimeUpdates().collect { update ->
            // Update specific widget
            dashboardView?.let { currentView ->
                val updatedWidgets = currentView.widgets.map { widget ->
                    if (widget.widgetId == update.widgetId) {
                        widget.copy(
                            content = update.data,
                            lastUpdated = update.timestamp
                        )
                    } else widget
                }
                dashboardView = currentView.copy(widgets = updatedWidgets)
            }
        }
    }

    dashboardView?.let { view ->
        DashboardLayout(
            dashboardView = view,
            config = config,
            modifier = modifier
        )
    } ?: run {
        DashboardLoadingIndicator()
    }
}

@Composable
fun DashboardLayout(
    dashboardView: DashboardView,
    config: DashboardConfiguration,
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val isTablet = configuration.screenWidthDp >= 600

    when (config.layout.type) {
        LayoutType.GRID -> GridDashboardLayout(
            widgets = dashboardView.widgets,
            config = config,
            isTablet = isTablet,
            modifier = modifier
        )
        LayoutType.FLEXIBLE -> FlexibleDashboardLayout(
            widgets = dashboardView.widgets,
            config = config,
            modifier = modifier
        )
        LayoutType.MASONRY -> MasonryDashboardLayout(
            widgets = dashboardView.widgets,
            config = config,
            modifier = modifier
        )
        LayoutType.CARD_STACK -> CardStackDashboardLayout(
            widgets = dashboardView.widgets,
            config = config,
            modifier = modifier
        )
    }
}

@Composable
fun GridDashboardLayout(
    widgets: List<RenderedWidget>,
    config: DashboardConfiguration,
    isTablet: Boolean,
    modifier: Modifier = Modifier
) {
    val columns = if (isTablet) config.layout.columns * 2 else config.layout.columns

    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
    ) {
        items(widgets) { widget ->
            DashboardWidget(
                widget = widget,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun FlexibleDashboardLayout(
    widgets: List<RenderedWidget>,
    config: DashboardConfiguration,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
    ) {
        // Group widgets by rows
        val groupedWidgets = widgets.chunked(config.layout.columns)

        items(groupedWidgets) { rowWidgets ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                rowWidgets.forEach { widget ->
                    DashboardWidget(
                        widget = widget,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Add empty space if row is not full
                repeat(config.layout.columns - rowWidgets.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
fun MasonryDashboardLayout(
    widgets: List<RenderedWidget>,
    config: DashboardConfiguration,
    modifier: Modifier = Modifier
) {
    // Masonry layout implementation
    // This is a simplified version - a full implementation would use a custom layout
    LazyVerticalGrid(
        columns = GridCells.Fixed(config.layout.columns),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
    ) {
        items(widgets) { widget ->
            DashboardWidget(
                widget = widget,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun CardStackDashboardLayout(
    widgets: List<RenderedWidget>,
    config: DashboardConfiguration,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
    ) {
        items(widgets) { widget ->
            DashboardWidget(
                widget = widget,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun DashboardWidget(
    widget: RenderedWidget,
    modifier: Modifier = Modifier
) {
    val isStale = System.currentTimeMillis() - widget.lastUpdated > 30000 // 30 seconds

    Card(
        modifier = modifier
            .shadow(
                elevation = 4.dp,
                shape = RoundedCornerShape(12.dp)
            )
            .clip(RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(
            containerColor = if (isStale)
                MaterialTheme.colorScheme.errorContainer
            else
                MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            WidgetHeader(
                widgetId = widget.widgetId,
                lastUpdated = widget.lastUpdated,
                isStale = isStale
            )

            Spacer(modifier = Modifier.height(8.dp))

            WidgetContent(
                content = widget.content,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun WidgetHeader(
    widgetId: String,
    lastUpdated: Long,
    isStale: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = formatWidgetTitle(widgetId),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isStale) {
                Icon(
                    imageVector = androidx.compose.material.icons.Icons.Default.Warning,
                    contentDescription = "Stale data",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
            }

            Text(
                text = formatLastUpdated(lastUpdated),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun WidgetContent(
    content: Any,
    modifier: Modifier = Modifier
) {
    when (content) {
        is ChartVisualization -> ChartWidget(content, modifier)
        is Map<*, *> -> MetricsWidget(content as Map<String, Any>, modifier)
        is List<*> -> ListWidget(content, modifier)
        is Pose3DVisualization -> Pose3DWidget(content, modifier)
        is HeatmapVisualization -> HeatmapWidget(content, modifier)
        else -> {
            Text(
                text = "Unsupported content type: ${content::class.simpleName}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = modifier
            )
        }
    }
}

@Composable
fun ChartWidget(
    chart: ChartVisualization,
    modifier: Modifier = Modifier
) {
    when (chart.type) {
        WidgetType.LINE_CHART -> LineChartComponent(chart, modifier)
        WidgetType.BAR_CHART -> BarChartComponent(chart, modifier)
        WidgetType.PIE_CHART -> PieChartComponent(chart, modifier)
        else -> {
            Text(
                text = "Chart type not implemented: ${chart.type}",
                modifier = modifier
            )
        }
    }
}

@Composable
fun LineChartComponent(
    chart: ChartVisualization,
    modifier: Modifier = Modifier
) {
    // This would integrate with a charting library like MPAndroidChart or custom Canvas
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp)
    ) {
        drawLineChart(chart, size)
    }
}

@Composable
fun BarChartComponent(
    chart: ChartVisualization,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp)
    ) {
        drawBarChart(chart, size)
    }
}

@Composable
fun PieChartComponent(
    chart: ChartVisualization,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp)
    ) {
        drawPieChart(chart, size)
    }
}

@Composable
fun MetricsWidget(
    metrics: Map<String, Any>,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(metrics.toList()) { (key, value) ->
            MetricRow(
                label = formatMetricLabel(key),
                value = formatMetricValue(value),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun MetricRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun ListWidget(
    items: List<*>,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(items.take(5)) { item -> // Show only first 5 items
            Text(
                text = item.toString(),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(vertical = 2.dp)
            )
        }

        if (items.size > 5) {
            item {
                Text(
                    text = "... and ${items.size - 5} more",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }
        }
    }
}

@Composable
fun Pose3DWidget(
    pose: Pose3DVisualization,
    modifier: Modifier = Modifier
) {
    // This would integrate with a 3D rendering library
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "3D Pose Viewer\nConfidence: ${(pose.confidence * 100).toInt()}%",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun HeatmapWidget(
    heatmap: HeatmapVisualization,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp)
    ) {
        drawHeatmap(heatmap, size)
    }
}

@Composable
fun DashboardLoadingIndicator() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CircularProgressIndicator()
            Text(
                text = "Loading dashboard...",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

// Helper functions for drawing charts
fun DrawScope.drawLineChart(chart: ChartVisualization, size: androidx.compose.ui.geometry.Size) {
    // Simplified line chart drawing
    val data = chart.data as? List<Float> ?: return
    if (data.isEmpty()) return

    val maxValue = data.maxOrNull() ?: 1f
    val minValue = data.minOrNull() ?: 0f
    val range = maxValue - minValue

    val path = androidx.compose.ui.graphics.Path()
    data.forEachIndexed { index, value ->
        val x = (index.toFloat() / (data.size - 1)) * size.width
        val y = size.height - ((value - minValue) / range) * size.height

        if (index == 0) {
            path.moveTo(x, y)
        } else {
            path.lineTo(x, y)
        }
    }

    drawPath(
        path = path,
        color = Color.Blue,
        style = Stroke(width = 3.dp.toPx())
    )
}

fun DrawScope.drawBarChart(chart: ChartVisualization, size: androidx.compose.ui.geometry.Size) {
    val data = chart.data as? List<Float> ?: return
    if (data.isEmpty()) return

    val maxValue = data.maxOrNull() ?: 1f
    val barWidth = size.width / data.size

    data.forEachIndexed { index, value ->
        val barHeight = (value / maxValue) * size.height
        val x = index * barWidth
        val y = size.height - barHeight

        drawRect(
            color = Color.Green,
            topLeft = Offset(x, y),
            size = androidx.compose.ui.geometry.Size(barWidth * 0.8f, barHeight)
        )
    }
}

fun DrawScope.drawPieChart(chart: ChartVisualization, size: androidx.compose.ui.geometry.Size) {
    val data = chart.data as? List<Float> ?: return
    if (data.isEmpty()) return

    val total = data.sum()
    val center = Offset(size.width / 2, size.height / 2)
    val radius = minOf(size.width, size.height) / 2 * 0.8f

    var startAngle = 0f
    val colors = listOf(Color.Red, Color.Green, Color.Blue, Color.Yellow, Color.Magenta)

    data.forEachIndexed { index, value ->
        val sweepAngle = (value / total) * 360f

        drawArc(
            color = colors[index % colors.size],
            startAngle = startAngle,
            sweepAngle = sweepAngle,
            useCenter = true,
            topLeft = Offset(center.x - radius, center.y - radius),
            size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2)
        )

        startAngle += sweepAngle
    }
}

fun DrawScope.drawHeatmap(heatmap: HeatmapVisualization, size: androidx.compose.ui.geometry.Size) {
    val data = heatmap.data
    val rows = data.size
    val cols = if (rows > 0) data[0].size else 0

    if (rows == 0 || cols == 0) return

    val cellWidth = size.width / cols
    val cellHeight = size.height / rows

    val maxValue = data.flatten().maxOrNull() ?: 1f
    val minValue = data.flatten().minOrNull() ?: 0f
    val range = maxValue - minValue

    for (row in 0 until rows) {
        for (col in 0 until cols) {
            val value = data[row][col]
            val intensity = if (range > 0) (value - minValue) / range else 0f

            val color = Color(
                red = intensity,
                green = 0f,
                blue = 1f - intensity,
                alpha = 0.8f
            )

            drawRect(
                color = color,
                topLeft = Offset(col * cellWidth, row * cellHeight),
                size = androidx.compose.ui.geometry.Size(cellWidth, cellHeight)
            )
        }
    }
}

// Utility functions
fun formatWidgetTitle(widgetId: String): String {
    return widgetId.replace("_", " ").split(" ").joinToString(" ") { word ->
        word.replaceFirstChar { it.uppercaseChar() }
    }
}

fun formatLastUpdated(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    return when {
        diff < 60000 -> "Now"
        diff < 3600000 -> "${diff / 60000}m ago"
        diff < 86400000 -> "${diff / 3600000}h ago"
        else -> "${diff / 86400000}d ago"
    }
}

fun formatMetricLabel(key: String): String {
    return key.replace("_", " ").split(" ").joinToString(" ") { word ->
        word.replaceFirstChar { it.uppercaseChar() }
    }
}

fun formatMetricValue(value: Any): String {
    return when (value) {
        is Float -> if (value < 10) "%.2f".format(value) else "%.0f".format(value)
        is Double -> if (value < 10) "%.2f".format(value) else "%.0f".format(value)
        is Int -> value.toString()
        is Long -> value.toString()
        else -> value.toString()
    }
}