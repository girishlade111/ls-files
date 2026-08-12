package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.db.*
import com.example.data.model.FileCategory
import com.example.data.model.FileItem
import com.example.ui.MainViewModel
import com.example.ui.components.*

enum class DateFilter(val displayName: String) {
    ANY_TIME("Any time"),
    TODAY("Today"),
    LAST_7_DAYS("Past 7 days"),
    LAST_30_DAYS("Past 30 days"),
    THIS_YEAR("This year")
}

fun FileItem.matchesDateFilter(filter: DateFilter): Boolean {
    if (filter == DateFilter.ANY_TIME) return true
    val now = System.currentTimeMillis()
    val millisPerDay = 86400000L
    val diff = now - this.lastModified
    return when (filter) {
        DateFilter.ANY_TIME -> true
        DateFilter.TODAY -> diff in 0..millisPerDay
        DateFilter.LAST_7_DAYS -> diff in 0..(7 * millisPerDay)
        DateFilter.LAST_30_DAYS -> diff in 0..(30 * millisPerDay)
        DateFilter.THIS_YEAR -> diff in 0..(365 * millisPerDay)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    viewModel: MainViewModel,
    onFileClick: (FileItem) -> Unit
) {
    val query by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val searchHistory by viewModel.searchHistoryFlow.collectAsState(initial = emptyList())
    val recentFiles by viewModel.recentFiles.collectAsState()
    val smartSearchEnabled by viewModel.smartSearchEnabled.collectAsState()
    val isIndexingOcr by viewModel.isIndexingOcr.collectAsState()
    val indexingStatus by viewModel.indexingStatus.collectAsState()
    val isSelectionMode by viewModel.isSelectionMode.collectAsState()
    val selectedPaths by viewModel.selectedPaths.collectAsState()
    var shareFile by remember { mutableStateOf<FileItem?>(null) }
    var showTagSelectionDialog by remember { mutableStateOf(false) }

    var selectedFilterCategory by remember { mutableStateOf<FileCategory?>(null) }
    var selectedDateFilter by remember { mutableStateOf(DateFilter.ANY_TIME) }

    val hasSearchState = shareFile != null || query.isNotEmpty() || selectedFilterCategory != null || selectedDateFilter != DateFilter.ANY_TIME || isSelectionMode || showTagSelectionDialog

    BackHandler(enabled = hasSearchState) {
        if (shareFile != null) {
            shareFile = null
        } else if (showTagSelectionDialog) {
            showTagSelectionDialog = false
        } else if (isSelectionMode) {
            viewModel.clearSelection()
        } else if (query.isNotEmpty()) {
            viewModel.updateSearchQuery("")
        } else {
            selectedFilterCategory = null
            selectedDateFilter = DateFilter.ANY_TIME
        }
    }

    val activeFiltersCount = (if (selectedFilterCategory != null) 1 else 0) + (if (selectedDateFilter != DateFilter.ANY_TIME) 1 else 0)

    Column(modifier = Modifier.fillMaxSize()) {
        if (isSelectionMode) {
            com.example.ui.components.MultiSelectionTopBar(
                selectedCount = selectedPaths.size,
                totalItemsCount = if (query.isEmpty()) recentFiles.size else searchResults.size,
                onCloseSelection = { viewModel.clearSelection() },
                onSelectAll = { viewModel.selectAll() },
                onShare = {
                    val currentList = if (query.isEmpty()) recentFiles else searchResults
                    val selectedFiles = currentList.filter { selectedPaths.contains(it.path) }
                    if (selectedFiles.isNotEmpty()) {
                        shareFile = selectedFiles.firstOrNull()
                    }
                },
                onDelete = { viewModel.performDeleteSelected() },
                onMove = { viewModel.emitSnackbar("Select a destination folder in Browse tab") },
                onCopy = { viewModel.emitSnackbar("Select a destination folder in Browse tab") },
                onRename = { },
                onTag = { showTagSelectionDialog = true },
                onCompress = { },
                onToggleStar = {
                    val currentList = if (query.isEmpty()) recentFiles else searchResults
                    val selectedFiles = currentList.filter { selectedPaths.contains(it.path) }
                    selectedFiles.forEach { viewModel.performToggleStar(it) }
                    viewModel.clearSelection()
                },
                onMoveToSafeFolder = {
                    val currentList = if (query.isEmpty()) recentFiles else searchResults
                    val selectedFiles = currentList.filter { selectedPaths.contains(it.path) }
                    selectedFiles.forEach { viewModel.performMoveToSafeFolder(it) }
                    viewModel.clearSelection()
                },
                onInfo = { }
            )
        } else {
            // Persistent Top Search Bar Container
            Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 2.dp,
            shadowElevation = 1.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = "Search",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = { viewModel.updateSearchQuery(it) },
                    placeholder = {
                        Text(
                            text = "Search files, documents, text...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        disabledBorderColor = Color.Transparent,
                        errorBorderColor = Color.Transparent
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("search_query_input")
                )
                if (query.isNotEmpty()) {
                    AnimatedIconButton(
                        onClick = { viewModel.updateSearchQuery("") },
                        modifier = Modifier.testTag("clear_search_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = "Clear search query",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Smart Search Banner Status
        if (smartSearchEnabled && query.isEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AnimatedPulseIcon(
                            imageVector = Icons.Outlined.AutoAwesome,
                            contentDescription = "Gemini OCR Smart Search",
                            tint = MaterialTheme.colorScheme.primary,
                            isPulsing = isIndexingOcr
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Gemini AI OCR & Smart Search",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = indexingStatus ?: "Scans file names & extracts image text via Gemini AI OCR.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        if (isIndexingOcr) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.5.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            OutlinedButton(
                                onClick = { viewModel.triggerOcrSmartSearchIndexing() },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.testTag("scan_ocr_index_btn")
                            ) {
                                Text("Scan OCR", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
        }

        // Material 3 Filter Chips Horizontal Row (File Types + Date Ranges)
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Active filters indicator reset chip
            if (activeFiltersCount > 0) {
                item {
                    AssistChip(
                        onClick = {
                            selectedFilterCategory = null
                            selectedDateFilter = DateFilter.ANY_TIME
                        },
                        label = { Text("Reset ($activeFiltersCount)") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.FilterAltOff,
                                contentDescription = "Clear filters",
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                            labelColor = MaterialTheme.colorScheme.onErrorContainer
                        ),
                        modifier = Modifier.testTag("reset_filters_chip")
                    )
                }
            }

            // File Type: All Types Chip
            item {
                FilterChip(
                    selected = selectedFilterCategory == null,
                    onClick = { selectedFilterCategory = null },
                    label = { Text("All Types") },
                    leadingIcon = if (selectedFilterCategory == null) {
                        { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    } else null,
                    modifier = Modifier.testTag("filter_all_types")
                )
            }

            // File Category Chips
            items(FileCategory.values().filter { it != FileCategory.OTHER }) { cat ->
                val isSelected = selectedFilterCategory == cat
                FilterChip(
                    selected = isSelected,
                    onClick = { selectedFilterCategory = if (isSelected) null else cat },
                    label = { Text(cat.displayName) },
                    leadingIcon = if (isSelected) {
                        { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    } else null,
                    modifier = Modifier.testTag("filter_cat_${cat.name.lowercase()}")
                )
            }

            // Date Range Filter Chips
            items(DateFilter.values()) { dateFilter ->
                val isSelected = selectedDateFilter == dateFilter
                FilterChip(
                    selected = isSelected,
                    onClick = { selectedDateFilter = dateFilter },
                    label = { Text(dateFilter.displayName) },
                    leadingIcon = {
                        Icon(
                            imageVector = if (isSelected) Icons.Filled.Check else Icons.Outlined.CalendarToday,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    modifier = Modifier.testTag("filter_date_${dateFilter.name.lowercase()}")
                )
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))

        if (query.isEmpty()) {
            // Landing State: Recent Searches + Quick Categories + Recently Opened Files
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Recent Search Queries Section
                if (searchHistory.isNotEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                            ),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Outlined.History,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Recent Searches",
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                        )
                                    }
                                    TextButton(
                                        onClick = { viewModel.clearSearchHistory() },
                                        modifier = Modifier.testTag("clear_search_history")
                                    ) {
                                        Text("Clear all")
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                searchHistory.forEach { item ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable { viewModel.updateSearchQuery(item.query) }
                                            .padding(vertical = 8.dp, horizontal = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Search,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(
                                            text = item.query,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.weight(1f)
                                        )
                                        AnimatedIconButton(
                                            onClick = { viewModel.deleteSearchQuery(item.query) },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.Close,
                                                contentDescription = "Remove query from history",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(
                                            imageVector = Icons.Outlined.NorthWest,
                                            contentDescription = "Fill query",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Quick Discovery Category Cards
                item {
                    Text(
                        text = "Quick File Types",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }

                item {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp)
                    ) {
                        val quickCategories = listOf(
                            FileCategory.DOCUMENTS to Icons.Outlined.Description,
                            FileCategory.IMAGES to Icons.Outlined.Image,
                            FileCategory.VIDEOS to Icons.Outlined.VideoFile,
                            FileCategory.AUDIO to Icons.Outlined.AudioFile,
                            FileCategory.APPS to Icons.Outlined.Android
                        )

                        items(quickCategories) { (category, icon) ->
                            ElevatedCard(
                                onClick = {
                                    selectedFilterCategory = category
                                    viewModel.updateSearchQuery(category.displayName)
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.elevatedCardColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = category.displayName,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = category.displayName,
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                }
                            }
                        }
                    }
                }

                // Recently Opened Files Section
                item {
                    Text(
                        text = "Recently Opened",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }

                if (recentFiles.isEmpty()) {
                    item {
                        Text(
                            text = "No recent files opened yet.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }
                } else {
                    items(recentFiles.take(5), key = { it.path }) { file ->
                        val isSelected = selectedPaths.contains(file.path)
                        FileRowItem(
                            file = file,
                            isSelected = isSelected,
                            isSelectionMode = isSelectionMode,
                            onClick = {
                                if (isSelectionMode) {
                                    viewModel.toggleSelection(file.path)
                                } else {
                                    onFileClick(file)
                                }
                            },
                            onLongClick = {
                                viewModel.toggleSelection(file.path)
                            },
                            onOverflowClick = { shareFile = file }
                        )
                    }
                }
            }
        } else {
            // Active Search Query Results Screen
            val filteredResults = remember(searchResults, selectedFilterCategory, selectedDateFilter) {
                searchResults.filter { item ->
                    val categoryMatch = selectedFilterCategory == null || item.category == selectedFilterCategory
                    val dateMatch = item.matchesDateFilter(selectedDateFilter)
                    categoryMatch && dateMatch
                }
            }

            // Results Counter Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isSearching) "Searching..." else "${filteredResults.size} file(s) found",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (activeFiltersCount > 0) {
                    Text(
                        text = "Filters active",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            if (isSearching) {
                FileListShimmerPlaceholder(count = 5)
            } else if (filteredResults.isEmpty()) {
                EmptyState(
                    icon = Icons.Outlined.SearchOff,
                    title = "No matching files",
                    description = if (activeFiltersCount > 0) "Try clearing active file type or date range filters." else "Try searching for another keyword or filename.",
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(filteredResults, key = { it.path }) { file ->
                        val isSelected = selectedPaths.contains(file.path)
                        FileRowItem(
                            file = file,
                            isSelected = isSelected,
                            isSelectionMode = isSelectionMode,
                            onClick = {
                                if (isSelectionMode) {
                                    viewModel.toggleSelection(file.path)
                                } else {
                                    onFileClick(file)
                                }
                            },
                            onLongClick = {
                                viewModel.toggleSelection(file.path)
                            },
                            onOverflowClick = { shareFile = file }
                        )
                    }
                }
            }
        }

        if (shareFile != null) {
            com.example.ui.components.FileShareActionSheet(
                filesToShare = listOf(shareFile!!),
                onDismiss = { shareFile = null },
                onShareSuccess = { msg -> viewModel.emitSnackbar(msg) }
            )
        }

        if (showTagSelectionDialog) {
            com.example.ui.components.TagSelectionDialog(
                selectedCount = selectedPaths.size,
                viewModel = viewModel,
                onDismiss = { showTagSelectionDialog = false },
                onTagSelected = { tagId, tagName ->
                    showTagSelectionDialog = false
                    viewModel.addTagToFiles(selectedPaths, tagId, tagName)
                }
            )
        }
    }
    }
}
