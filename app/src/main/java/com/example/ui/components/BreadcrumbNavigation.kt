package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.SdCard
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

data class BreadcrumbSegment(
    val name: String,
    val path: String,
    val isRoot: Boolean = false
)

@Composable
fun BreadcrumbNavigation(
    currentPath: String,
    rootPath: String,
    onNavigateToPath: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val segments = remember(currentPath, rootPath) {
        val list = mutableListOf<BreadcrumbSegment>()
        list.add(BreadcrumbSegment(name = "Internal Storage", path = rootPath, isRoot = true))
        
        if (currentPath != rootPath && currentPath.startsWith(rootPath)) {
            val relative = currentPath.substringAfter(rootPath).trim('/')
            if (relative.isNotEmpty()) {
                val parts = relative.split('/')
                var accumulated = rootPath
                for (part in parts) {
                    if (part.isNotEmpty()) {
                        accumulated = "$accumulated/$part"
                        list.add(BreadcrumbSegment(name = part, path = accumulated))
                    }
                }
            }
        }
        list
    }

    val listState = rememberLazyListState()

    // Auto-scroll to end when path changes
    LaunchedEffect(segments.size) {
        if (segments.isNotEmpty()) {
            listState.animateScrollToItem(segments.size - 1)
        }
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(12.dp)
    ) {
        LazyRow(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp)
                .testTag("breadcrumb_navigation_row"),
            verticalAlignment = Alignment.CenterVertically
        ) {
            itemsIndexed(segments) { index, segment ->
                val isLast = index == segments.size - 1

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.testTag("breadcrumb_item_$index")
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isLast) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                        } else {
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                        },
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(enabled = !isLast) {
                                onNavigateToPath(segment.path)
                            }
                            .semantics(mergeDescendants = true) {
                                if (!isLast) {
                                    role = Role.Button
                                    contentDescription = "Navigate back to ${segment.name}"
                                } else {
                                    contentDescription = "Current directory: ${segment.name}"
                                }
                            }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            if (segment.isRoot) {
                                Icon(
                                    imageVector = Icons.Outlined.Home,
                                    contentDescription = "Home Root",
                                    modifier = Modifier.size(16.dp),
                                    tint = if (isLast) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                            }

                            Text(
                                text = segment.name,
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = if (isLast) FontWeight.Bold else FontWeight.Medium
                                ),
                                color = if (isLast) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }

                    if (!isLast) {
                        Icon(
                            imageVector = Icons.Outlined.ChevronRight,
                            contentDescription = null,
                            modifier = Modifier
                                .padding(horizontal = 4.dp)
                                .size(16.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        }
    }
}
