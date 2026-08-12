package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import com.example.data.model.FileCategory
import com.example.data.model.StorageSpaceInfo
import kotlin.math.atan2

data class PieChartSlice(
    val category: FileCategory?,
    val label: String,
    val sizeBytes: Long,
    val color: Color,
    val startAngle: Float,
    val sweepAngle: Float
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StorageBreakdownPieChart(
    categorySizes: Map<FileCategory, Long>,
    storageInfo: StorageSpaceInfo,
    onCategoryClick: (FileCategory) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedSliceIndex by remember { mutableIntStateOf(-1) }

    // Prepare slices based on category sizes
    val activeCategories = listOf(
        FileCategory.IMAGES,
        FileCategory.VIDEOS,
        FileCategory.AUDIO,
        FileCategory.DOCUMENTS,
        FileCategory.DOWNLOADS,
        FileCategory.APPS,
        FileCategory.SCREENSHOTS,
        FileCategory.ARCHIVES,
        FileCategory.OTHER
    )

    val categorySliceData = activeCategories.mapNotNull { cat ->
        val size = categorySizes[cat] ?: 0L
        if (size > 0) {
            val (_, color) = getCategoryIconAndColor(cat)
            cat to Pair(size, color)
        } else null
    }

    val totalCategoryBytes = categorySliceData.sumOf { it.second.first }
    val safeTotalBytes = if (totalCategoryBytes > 0) totalCategoryBytes else 1L

    // Calculate angles
    var currentAngle = -90f
    val slices = remember(categorySliceData, totalCategoryBytes) {
        categorySliceData.map { (cat, sizeColor) ->
            val (size, color) = sizeColor
            val sweep = (size.toFloat() / safeTotalBytes.toFloat()) * 360f
            val slice = PieChartSlice(
                category = cat,
                label = cat.displayName,
                sizeBytes = size,
                color = color,
                startAngle = currentAngle,
                sweepAngle = sweep
            )
            currentAngle += sweep
            slice
        }
    }

    // Smooth animation on launch
    val animProgress = remember { Animatable(0f) }
    LaunchedEffect(slices) {
        animProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing)
        )
    }

    val selectedSlice = if (selectedSliceIndex in slices.indices) slices[selectedSliceIndex] else null

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("storage_breakdown_pie_chart"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Outlined.PieChart,
                                contentDescription = "Pie Chart",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(
                        modifier = Modifier.semantics { heading() }
                    ) {
                        Text(
                            text = "Storage Breakdown",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${formatFileSize(totalCategoryBytes)} used across categories",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (selectedSlice != null) {
                    FilterChip(
                        selected = true,
                        onClick = { selectedSliceIndex = -1 },
                        label = { Text("Reset", style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Pie/Donut Chart Center Canvas Display
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(
                    modifier = Modifier
                        .size(180.dp)
                        .semantics {
                            contentDescription = "Interactive storage breakdown pie chart displaying ${formatFileSize(totalCategoryBytes)} total used across ${slices.size} categories."
                        }
                        .pointerInput(slices) {
                            detectTapGestures { offset ->
                                val center = Offset(size.width / 2f, size.height / 2f)
                                val dx = offset.x - center.x
                                val dy = offset.y - center.y
                                var angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
                                if (angle < 0) angle += 360f

                                // Check which slice this angle falls into
                                val clickedIdx = slices.indexOfFirst { slice ->
                                    var start = slice.startAngle
                                    while (start < 0) start += 360f
                                    val end = start + slice.sweepAngle
                                    if (end <= 360f) {
                                        angle >= start && angle <= end
                                    } else {
                                        angle >= start || angle <= (end % 360f)
                                    }
                                }
                                if (clickedIdx >= 0) {
                                    selectedSliceIndex = if (selectedSliceIndex == clickedIdx) -1 else clickedIdx
                                }
                            }
                        }
                ) {
                    val canvasWidth = size.width
                    val canvasHeight = size.height
                    val outerRadius = canvasWidth / 2f
                    val strokeWidth = 32.dp.toPx()
                    val arcSize = Size(canvasWidth - strokeWidth, canvasHeight - strokeWidth)
                    val topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f)

                    if (slices.isEmpty()) {
                        drawArc(
                            color = Color.LightGray.copy(alpha = 0.3f),
                            startAngle = 0f,
                            sweepAngle = 360f,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = strokeWidth)
                        )
                    } else {
                        slices.forEachIndexed { index, slice ->
                            val isSelected = index == selectedSliceIndex
                            val currentSweep = slice.sweepAngle * animProgress.value
                            val currentStroke = if (isSelected) strokeWidth + 10f else strokeWidth

                            drawArc(
                                color = slice.color,
                                startAngle = slice.startAngle,
                                sweepAngle = currentSweep,
                                useCenter = false,
                                topLeft = topLeft,
                                size = arcSize,
                                style = Stroke(width = currentStroke)
                            )
                        }
                    }
                }

                // Center Donut hole details
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(24.dp)
                ) {
                    if (selectedSlice != null) {
                        Text(
                            text = selectedSlice.label,
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = selectedSlice.color,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = formatFileSize(selectedSlice.sizeBytes),
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        val pct = (selectedSlice.sizeBytes.toDouble() / safeTotalBytes.toDouble() * 100)
                        Text(
                            text = String.format("%.1f%%", pct),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            text = "Used Space",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = formatFileSize(totalCategoryBytes),
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "of ${String.format("%.1f GB", storageInfo.totalGb)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Category Legend Grid
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                slices.forEachIndexed { idx, slice ->
                    val isSelected = idx == selectedSliceIndex
                    val cat = slice.category ?: return@forEachIndexed
                    val pct = (slice.sizeBytes.toDouble() / safeTotalBytes.toDouble() * 100)

                    Surface(
                        onClick = {
                            if (isSelected) {
                                onCategoryClick(cat)
                            } else {
                                selectedSliceIndex = idx
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) slice.color.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surface,
                        tonalElevation = if (isSelected) 4.dp else 1.dp,
                        modifier = Modifier
                            .testTag("legend_chip_${cat.name.lowercase()}")
                            .semantics {
                                role = Role.Button
                                contentDescription = "${cat.displayName} category, ${formatFileSize(slice.sizeBytes)}, ${String.format("%.0f%%", pct)} of used storage. ${if (isSelected) "Selected. Tap again to open category." else "Tap to highlight on chart."}"
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(slice.color)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = cat.displayName,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "${formatFileSize(slice.sizeBytes)} (${String.format("%.0f%%", pct)})",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (isSelected) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Outlined.ChevronRight,
                                    contentDescription = "Open",
                                    tint = slice.color,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
