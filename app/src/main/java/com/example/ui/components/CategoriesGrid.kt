package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.FileCategory

@Composable
fun CategoriesGrid(
    categorySizes: Map<FileCategory, Long>,
    onCategoryClick: (FileCategory) -> Unit,
    modifier: Modifier = Modifier
) {
    val categories = listOf(
        FileCategory.DOWNLOADS,
        FileCategory.IMAGES,
        FileCategory.VIDEOS,
        FileCategory.AUDIO,
        FileCategory.DOCUMENTS,
        FileCategory.APPS,
        FileCategory.SCREENSHOTS,
        FileCategory.ARCHIVES
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "Categories",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 2.dp)
        )

        for (chunk in categories.chunked(2)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                for (category in chunk) {
                    val size = categorySizes[category] ?: 0L
                    CategoryCard(
                        category = category,
                        sizeBytes = size,
                        onClick = { onCategoryClick(category) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("category_card_${category.name.lowercase()}")
                    )
                }
            }
        }
    }
}
