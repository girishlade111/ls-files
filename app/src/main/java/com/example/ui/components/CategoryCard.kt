package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.example.data.model.FileCategory
import com.example.ui.theme.*

fun getCategoryIconAndColor(category: FileCategory): Pair<ImageVector, Color> {
    return when (category) {
        FileCategory.DOWNLOADS -> Icons.Outlined.FileDownload to CategoryDownloadsColor
        FileCategory.IMAGES -> Icons.Outlined.Image to CategoryImagesColor
        FileCategory.VIDEOS -> Icons.Outlined.Videocam to CategoryVideosColor
        FileCategory.AUDIO -> Icons.Outlined.Audiotrack to CategoryAudioColor
        FileCategory.DOCUMENTS -> Icons.Outlined.Description to CategoryDocumentsColor
        FileCategory.APPS -> Icons.Outlined.Apps to CategoryAppsColor
        FileCategory.SCREENSHOTS -> Icons.Outlined.CropOriginal to CategoryScreenshotsColor
        FileCategory.ARCHIVES -> Icons.Outlined.FolderZip to CategoryArchivesColor
        FileCategory.OTHER -> Icons.Outlined.Folder to Color.Gray
    }
}

fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val k = 1024.0
    val sizes = arrayOf("B", "KB", "MB", "GB", "TB")
    val i = (Math.log(bytes.toDouble()) / Math.log(k)).toInt().coerceIn(0, sizes.size - 1)
    val value = bytes / Math.pow(k, i.toDouble())
    return String.format("%.1f %s", value, sizes[i])
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryCard(
    category: FileCategory,
    sizeBytes: Long,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (icon, color) = getCategoryIconAndColor(category)

    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(84.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = "Category ${category.displayName}, ${formatFileSize(sizeBytes)}"
            },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                AnimatedCategoryIcon(
                    imageVector = icon,
                    contentDescription = category.displayName,
                    tint = color,
                    iconSize = 24.dp
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = category.displayName,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = formatFileSize(sizeBytes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
